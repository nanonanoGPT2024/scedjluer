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
import java.util.Collection;
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

import co.id.mcs.dika.model.SubmittedActivities;
import co.id.mcs.dika.util.JdbcUtil;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AutoSubmitService {

    @Value("${upload.source.path.submit}")
    private String uploadSourcePath;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public void triggerSubmit() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.warn("Submit process is already running. Skipping trigger.");
            return;
        }

        executorService.submit(() -> {
            try {
                runSubmitProcess();
            } catch (Exception e) {
                log.error("Error during trigger submit process: ", e);
            } finally {
                isProcessing.set(false);
            }
        });
    }

    /**
     * Simpan file CSV ke folder trigger-submit (upload.source.path.submit).
     * File akan diambil saat trigger-submit dijalankan.
     */
    public void saveToSubmitFolder(org.springframework.web.multipart.MultipartFile file) {
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
            log.info("Saved submit file to: {}", destFile);
        } catch (IOException e) {
            throw new co.id.mcs.dika.exception.AppException(500, "Failed to save file: " + e.getMessage());
        }
    }

    private void runSubmitProcess() {
        log.info("Starting Auto Submit process from folder: {}", uploadSourcePath);
        File folder = new File(uploadSourcePath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.error("Source path does not exist or is not a directory: {}", uploadSourcePath);
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            log.info("No CSV files found for submit in {}", uploadSourcePath);
            return;
        }

        for (File file : files) {
            log.info("Processing submit file: {}", file.getName());
            try {
                processCsvFilev2(file);
                if (file.delete()) {
                    log.info("Successfully deleted processed file: {}", file.getName());
                } else {
                    log.warn("Failed to delete processed file: {}", file.getName());
                }
            } catch (Exception e) {
                log.error("Failed to process file: {}", file.getName(), e);
            }
        }
        log.info("Finished Auto Submit process");
    }

    /**
     * CSV format: case_id|"cust_no"|"tgl_submit"
     * Example: 1|"3211088713"|"04/06/2025 21:00:00"
     *
     * Optimasi:
     * 1. Baca seluruh CSV ke memory → kumpulkan semua case_id
     * 2. Satu query IN untuk ambil order_data (case_id → order_id)
     * 3. Satu query untuk ambil semua order_id yg sudah ada di submitted_activities
     * 4. Dedup di memory → bulk insert
     */
    private void processCsvFile(File file) throws Exception {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        // ── Tahap 1: Baca CSV ke memory ────────────────────────────────────────
        record CsvRow(Long caseId, String custNo, String tglSubmit) {
        }
        List<CsvRow> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null)
                return;

            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty())
                    continue;
                String[] v = line.replace("\"\"", "").split("\\|");
                if (v.length < 3) {
                    log.warn("Row {} insufficient columns, skipping.", lineNum);
                    continue;
                }
                try {
                    String caseIdStr = v[0].replace("\"", "").trim();
                    if (caseIdStr.isEmpty())
                        continue;
                    Long caseId = Long.parseLong(caseIdStr);
                    String custNo = v[1].replace("\"", "").trim();
                    String tglSubmit = v[2].replace("\"", "").trim();
                    rows.add(new CsvRow(caseId, custNo, tglSubmit));
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

        // ── Tahap 2: Ambil order_data sekaligus (1 query IN) ──────────────────
        Set<Long> caseIds = new HashSet<>();
        for (CsvRow r : rows)
            caseIds.add(r.caseId());

        // Map: case_id → order UUID
        Map<Long, UUID> caseIdToOrderId = new HashMap<>();
        if (!caseIds.isEmpty()) {
            String sql = "SELECT id, case_id FROM order_data WHERE case_id IN (:caseIds)";
            MapSqlParameterSource params = new MapSqlParameterSource("caseIds", caseIds);
            namedParameterJdbcTemplate.query(sql, params, rs -> {
                UUID id = UUID.fromString(rs.getString("id"));
                long cid = rs.getLong("case_id");
                caseIdToOrderId.put(cid, id);
            });
            log.info("Fetched {} matching order_data records for {} case_ids",
                    caseIdToOrderId.size(), caseIds.size());
        }

        // ── Tahap 3: Ambil order_id yg sudah ada di submitted_activities (1 query IN)
        Set<UUID> existingOrderIds = new HashSet<>();
        Set<String> existingCustNos = new HashSet<>();

        Collection<UUID> orderIdsToCheck = caseIdToOrderId.values();
        if (!orderIdsToCheck.isEmpty()) {
            String sqlExist = "SELECT order_id, cust_no FROM submitted_activities WHERE order_id IN (:orderIds)";
            MapSqlParameterSource p2 = new MapSqlParameterSource("orderIds",
                    orderIdsToCheck.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList()));
            namedParameterJdbcTemplate.query(sqlExist, p2, rs -> {
                String oid = rs.getString("order_id");
                if (oid != null)
                    existingOrderIds.add(UUID.fromString(oid));
                String cn = rs.getString("cust_no");
                if (cn != null)
                    existingCustNos.add(cn);
            });
        }

        // Fallback: cust_no untuk yg tidak punya order_id di order_data
        Set<String> custNosWithoutOrder = new HashSet<>();
        for (CsvRow r : rows) {
            if (!caseIdToOrderId.containsKey(r.caseId())) {
                custNosWithoutOrder.add(r.custNo());
            }
        }
        if (!custNosWithoutOrder.isEmpty()) {
            String sqlCustNo = "SELECT cust_no FROM submitted_activities WHERE cust_no IN (:custNos)";
            MapSqlParameterSource p3 = new MapSqlParameterSource("custNos", custNosWithoutOrder);
            namedParameterJdbcTemplate.query(sqlCustNo, p3, rs -> {
                existingCustNos.add(rs.getString("cust_no"));
            });
        }
        log.info("Found {} already-submitted order_ids and {} cust_nos in DB",
                existingOrderIds.size(), existingCustNos.size());

        // ── Tahap 4: Build batch & insert ─────────────────────────────────────
        List<SubmittedActivities> batch = new ArrayList<>();
        int insertCount = 0;
        int skippedCount = 0;
        Date now = new Date();

        for (CsvRow r : rows) {
            UUID orderId = caseIdToOrderId.get(r.caseId());

            // Cek duplikasi di memory
            boolean exists = (orderId != null)
                    ? existingOrderIds.contains(orderId)
                    : existingCustNos.contains(r.custNo());

            if (exists) {
                skippedCount++;
                continue;
            }

            Date submitAt = null;
            if (!r.tglSubmit().isEmpty()) {
                try {
                    submitAt = inputFormat.parse(r.tglSubmit());
                } catch (Exception ex) {
                    log.warn("Failed to parse tgl_submit '{}' for case_id {}", r.tglSubmit(), r.caseId());
                }
            }

            SubmittedActivities sa = new SubmittedActivities();
            sa.setId(UUID.randomUUID());
            sa.setOrderId(orderId);
            sa.setCustNo(r.custNo());
            sa.setSubmitAt(submitAt);
            sa.setCreatedAt(now);
            batch.add(sa);
            insertCount++;

            // Tandai sebagai sudah diproses agar tidak duplikat dalam file yg sama
            if (orderId != null)
                existingOrderIds.add(orderId);
            else
                existingCustNos.add(r.custNo());

            if (batch.size() >= 500) {
                log.info("Inserting batch of {} submitted_activities records", batch.size());
                JdbcUtil.bulkInsert(namedParameterJdbcTemplate, batch, SubmittedActivities.class);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            log.info("Inserting final batch of {} submitted_activities records", batch.size());
            JdbcUtil.bulkInsert(namedParameterJdbcTemplate, batch, SubmittedActivities.class);
        }

        log.info("Finished processing {}: total={}, inserted={}, skipped={}",
                file.getName(), rows.size(), insertCount, skippedCount);
    }

    private void processCsvFilev2(File file) throws Exception {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date now = new Date();
        List<SubmittedActivities> batch = new ArrayList<>();
        int batchSize = 500;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null)
                return;

            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty())
                    continue;
                String[] v = line.replace("\"\"", "").split("\\|");
                if (v.length < 3)
                    continue;

                try {
                    String caseIdStr = v[0].replace("\"", "").trim();
                    if (caseIdStr.isEmpty())
                        continue;
                    Long caseId = Long.parseLong(caseIdStr);
                    String custNo = v[1].replace("\"", "").trim();
                    String tglSubmitStr = v[2].replace("\"", "").trim();

                    // 1. Ambil order_id dari order_data berdasarkan case_id
                    String sqlOrder = "SELECT id FROM order_data WHERE case_id = :caseId";
                    MapSqlParameterSource orderParams = new MapSqlParameterSource("caseId", caseId);
                    List<UUID> orderIds = namedParameterJdbcTemplate.query(sqlOrder, orderParams,
                            (rs, rNum) -> UUID.fromString(rs.getString("id")));

                    UUID orderId = orderIds.isEmpty() ? null : orderIds.get(0);

                    // 2. Cek apakah data sudah ada di submitted_activities berdasarkan order_id /
                    // cust_no
                    boolean exists = false;
                    if (orderId != null) {
                        String sqlCheck = "SELECT COUNT(1) FROM submitted_activities WHERE order_id = :orderId";
                        Integer count = namedParameterJdbcTemplate.queryForObject(sqlCheck,
                                new MapSqlParameterSource("orderId", orderId), Integer.class);
                        exists = (count != null && count > 0);
                    } else {
                        String sqlCheck = "SELECT COUNT(1) FROM submitted_activities WHERE cust_no = :custNo";
                        Integer count = namedParameterJdbcTemplate.queryForObject(sqlCheck,
                                new MapSqlParameterSource("custNo", custNo), Integer.class);
                        exists = (count != null && count > 0);
                    }

                    if (!exists) {
                        Date submitAt = null;
                        if (!tglSubmitStr.isEmpty()) {
                            try {
                                submitAt = inputFormat.parse(tglSubmitStr);
                            } catch (Exception ignore) {
                            }
                        }

                        SubmittedActivities sa = new SubmittedActivities();
                        sa.setId(UUID.randomUUID());
                        sa.setOrderId(orderId);
                        sa.setCustNo(custNo);
                        sa.setSubmitAt(submitAt);
                        sa.setCreatedAt(now);

                        batch.add(sa);
                    }
                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", lineNum, e.getMessage());
                }

                if (batch.size() >= batchSize) {
                    JdbcUtil.bulkInsert(namedParameterJdbcTemplate, batch, SubmittedActivities.class);
                    log.info("Batch inserted {} records into submitted_activities", batch.size());
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                JdbcUtil.bulkInsert(namedParameterJdbcTemplate, batch, SubmittedActivities.class);
                log.info("Final batch inserted {} records into submitted_activities", batch.size());
            }
        }
    }
}
