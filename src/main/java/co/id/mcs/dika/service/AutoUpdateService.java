package co.id.mcs.dika.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import co.id.mcs.dika.model.Printing;
import co.id.mcs.dika.util.JdbcUtil;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AutoUpdateService {

    @Value("${upload.source.path.update}")
    private String uploadSourcePath;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public void triggerUpdate() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.warn("Update process is already running. Skipping trigger.");
            return;
        }

        executorService.submit(() -> {
            try {
                runUpdateProcess();
            } catch (Exception e) {
                log.error("Error during trigger update process: ", e);
            } finally {
                isProcessing.set(false);
            }
        });
    }

    /**
     * Simpan file CSV ke folder trigger-update (upload.source.path.update).
     * File akan diambil saat trigger-update dijalankan.
     */
    public void saveToUpdateFolder(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new co.id.mcs.dika.exception.AppException(400, "File is mandatory");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            throw new co.id.mcs.dika.exception.AppException(400, "Only CSV files are allowed");
        }
        try {
            Path destDir = Paths.get(uploadSourcePath);
            if (!Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }
            Path destFile = destDir.resolve(originalFilename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Saved update file to: {}", destFile);
        } catch (IOException e) {
            throw new co.id.mcs.dika.exception.AppException(500, "Failed to save file: " + e.getMessage());
        }
    }

    private void runUpdateProcess() {
        log.info("Starting Auto Update process from folder: {}", uploadSourcePath);
        File folder = new File(uploadSourcePath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.error("Source path does not exist or is not a directory: {}", uploadSourcePath);
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            log.info("No CSV files found for update in {}", uploadSourcePath);
            return;
        }

        for (File file : files) {
            log.info("Processing update file: {}", file.getName());
            try {
                processCsvFile(file);
                if (file.delete()) {
                    log.info("Successfully deleted processed file: {}", file.getName());
                } else {
                    log.warn("Failed to delete processed file: {}", file.getName());
                }
            } catch (Exception e) {
                log.error("Failed to process file: {}", file.getName(), e);
            }
        }
        log.info("Finished Auto Update process");
    }

    /**
     * Optimasi:
     * 1. Baca seluruh CSV ke memory → kumpulkan semua case_id
     * 2. Satu query IN → ambil order_data yg relevan (id, case_id, cust_no, dll)
     * 3. Update order_data via batch UPDATE (CASE WHEN) satu query
     * 4. Bulk insert Printing
     */
    private void processCsvFile(File file) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // ── Tahap 1: Baca CSV ke memory ────────────────────────────────────────
        record CsvRow(
            Long caseId, String cis, String barcode,
            String statusMs, String reasonMs,
            String pickupResultDateStr, String msCodeUpdate, String msNameUpdate
        ) {}

        List<CsvRow> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null) return;

            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                String[] v = line.replace("\"\"", "").split("\\|");
                if (v.length < 33) continue;
                try {
                    String caseIdStr = v[1].replace("\"", "").trim();
                    if (caseIdStr.isEmpty()) continue;
                    Long caseId = Long.parseLong(caseIdStr);

                    rows.add(new CsvRow(
                        caseId,
                        v[2].replace("\"", "").trim(),
                        v[24].replace("\"", "").trim(),
                        v[25].replace("\"", "").trim(),
                        v[27].replace("\"", "").trim(),
                        v[30].replace("\"", "").trim(),
                        v[31].replace("\"", "").trim(),
                        v[32].replace("\"", "").trim()
                    ));
                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", lineNum, e.getMessage());
                }
            }
        }

        if (rows.isEmpty()) {
            log.info("No valid rows found in {}", file.getName());
            return;
        }
        log.info("Read {} rows from {}", rows.size(), file.getName());

        // ── Tahap 2: Fetch order_data sekaligus (1 query IN) ─────────────────
        Set<Long> caseIds = new HashSet<>();
        for (CsvRow r : rows) caseIds.add(r.caseId());

        // Map: case_id → {id (UUID)}
        Map<Long, UUID> caseIdToOrderId = new HashMap<>();
        String sqlFetch = "SELECT id, case_id FROM order_data WHERE case_id IN (:caseIds)";
        MapSqlParameterSource p1 = new MapSqlParameterSource("caseIds", caseIds);
        namedParameterJdbcTemplate.query(sqlFetch, p1, rs -> {
            long cid = rs.getLong("case_id");
            UUID uid = UUID.fromString(rs.getString("id"));
            caseIdToOrderId.put(cid, uid);
        });
        log.info("Matched {} of {} case_ids in order_data", caseIdToOrderId.size(), caseIds.size());

        if (caseIdToOrderId.isEmpty()) {
            log.info("No matching order_data records; nothing to update.");
            return;
        }

        // ── Tahap 3: Batch UPDATE order_data via CASE WHEN (1 query) ─────────
        List<CsvRow> matchedRows = new ArrayList<>();
        for (CsvRow r : rows) {
            if (caseIdToOrderId.containsKey(r.caseId())) matchedRows.add(r);
        }

        int batchSize = 500;
        Date now = new Date();
        List<Printing> printingBatch = new ArrayList<>();
        int updateCount = 0;

        for (int i = 0; i < matchedRows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, matchedRows.size());
            List<CsvRow> chunk = matchedRows.subList(i, end);

            StringBuilder caseStatus   = new StringBuilder("CASE ");
            StringBuilder caseReason   = new StringBuilder("CASE ");
            StringBuilder casePuDate   = new StringBuilder("CASE ");
            StringBuilder caseMsCode   = new StringBuilder("CASE ");
            StringBuilder caseMsName   = new StringBuilder("CASE ");
            List<UUID> ids = new ArrayList<>();
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("now", now);

            for (int j = 0; j < chunk.size(); j++) {
                CsvRow r = chunk.get(j);
                UUID orderId = caseIdToOrderId.get(r.caseId());
                ids.add(orderId);

                Date puDate = null;
                if (!r.pickupResultDateStr().isEmpty()) {
                    try { puDate = dateFormat.parse(r.pickupResultDateStr()); } catch (Exception ignore) {}
                }

                String idKey  = "id"  + j;
                String smKey  = "sm"  + j;
                String rmKey  = "rm"  + j;
                String pdKey  = "pd"  + j;
                String mcKey  = "mc"  + j;
                String mnKey  = "mn"  + j;

                params.addValue(idKey, orderId);
                params.addValue(smKey, r.statusMs());
                params.addValue(rmKey, r.reasonMs());
                params.addValue(pdKey, puDate);
                params.addValue(mcKey, r.msCodeUpdate());
                params.addValue(mnKey, r.msNameUpdate());

                caseStatus.append("WHEN id = :").append(idKey).append(" THEN :").append(smKey).append(" ");
                caseReason.append("WHEN id = :").append(idKey).append(" THEN :").append(rmKey).append(" ");
                casePuDate.append("WHEN id = :").append(idKey).append(" THEN :").append(pdKey).append(" ");
                caseMsCode.append("WHEN id = :").append(idKey).append(" THEN :").append(mcKey).append(" ");
                caseMsName.append("WHEN id = :").append(idKey).append(" THEN :").append(mnKey).append(" ");

                // Prepare Printing
                Printing printing = new Printing();
                printing.setId(UUID.randomUUID());
                printing.setNoCase(r.caseId());
                printing.setCis(r.cis());
                printing.setBarcode(r.barcode());
                printing.setStatusPickUpMs(r.statusMs());
                printing.setStatusGagalPu(r.reasonMs());
                printing.setPuMsDate(r.pickupResultDateStr());
                printing.setMsCode(r.msCodeUpdate());
                printing.setMsName(r.msNameUpdate());
                printing.setDateUpload(now);
                printingBatch.add(printing);
            }

            caseStatus.append("ELSE status_ms END");
            caseReason.append("ELSE reason_ms END");
            casePuDate.append("ELSE pickup_result_date END");
            caseMsCode.append("ELSE ms_code_update END");
            caseMsName.append("ELSE ms_name_update END");
            params.addValue("ids", ids);

            String finalSql = "UPDATE order_data SET " +
                "status_ms = " + caseStatus + ", " +
                "reason_ms = " + caseReason + ", " +
                "pickup_result_date = " + casePuDate + ", " +
                "ms_code_update = " + caseMsCode + ", " +
                "ms_name_update = " + caseMsName + ", " +
                "update_date = :now " +
                "WHERE id IN (:ids)";

            int affected = namedParameterJdbcTemplate.update(finalSql, params);
            updateCount += affected;
            log.info("Batch update: {} rows affected (chunk {}-{})", affected, i, end - 1);
        }

        // ── Tahap 4: Bulk insert Printing ─────────────────────────────────────
        if (!printingBatch.isEmpty()) {
            for (int i = 0; i < printingBatch.size(); i += batchSize) {
                int end = Math.min(i + batchSize, printingBatch.size());
                List<Printing> chunk = printingBatch.subList(i, end);
                log.info("Inserting {} printing records", chunk.size());
                JdbcUtil.bulkInsert(namedParameterJdbcTemplate, new ArrayList<>(chunk), Printing.class);
            }
        }

        log.info("Finished processing {}: read={}, matched={}, updated={}, printing={}",
                file.getName(), rows.size(), matchedRows.size(), updateCount, printingBatch.size());
    }
}
