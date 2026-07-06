package co.id.mcs.dika.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import co.id.mcs.dika.constant.WbillH0Constant;
import lombok.extern.log4j.Log4j2;

/**
 * TriggerWbillH0Service
 *
 * Menjalankan query data AGREE Credit Card H0 (status_date &
 * tanggal_pickup = CURDATE()),
 * lalu menyimpan hasilnya ke file CSV di folder yang dikonfigurasi via
 * {@code upload.wbill.h0.output.path}.
 *
 * File CSV diberi nama dengan format: wbill_h0_<yyyyMMdd_HHmmss>.csv
 */
@Log4j2
@Service
public class TriggerWbillH0Service {

    @Value("${upload.wbill.h0.output.path}")
    private String wbillH0OutputPath;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // -------------------------------------------------------------------------
    // Public trigger (dipanggil dari controller, dijalankan di background)
    // -------------------------------------------------------------------------

    public void triggerWbillH0() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.warn("Wbill H0 export is already running. Skipping trigger.");
            return;
        }

        executorService.submit(() -> {
            try {
                runWbillH0Export();
            } catch (Exception e) {
                log.error("Error during wbill H0 export process: ", e);
            } finally {
                isProcessing.set(false);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private void runWbillH0Export() throws Exception {
        log.info("Starting Wbill H0 export to folder: {}", wbillH0OutputPath);

        // Pastikan folder output ada
        File outputDir = new File(wbillH0OutputPath);
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                log.error("Failed to create output directory: {}", wbillH0OutputPath);
                return;
            }
            log.info("Created output directory: {}", wbillH0OutputPath);
        }

        // Buat nama file CSV dengan timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "wbill_h0_" + timestamp + ".csv";
        File csvFile = new File(outputDir, fileName);

        // Jalankan query
        log.info("Executing wbill H0 query...");
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                WbillH0Constant.WBILL_H0_QUERY, new MapSqlParameterSource()
                        .addValue("pAppReferenceId", null));
        log.info("Query returned {} rows (including header row)", rows.size());

        // Tulis ke CSV
        writeCsv(csvFile, rows);
        log.info("Wbill H0 CSV written to: {}", csvFile.getAbsolutePath());
    }

    /**
     * Tulis hasil query ke file CSV.
     * Baris pertama dari hasil query adalah baris header (dari UNION pertama),
     * sehingga langsung ditulis apa adanya tanpa perlu menambah header lagi.
     */
    private void writeCsv(File csvFile, List<Map<String, Object>> rows) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, java.nio.charset.StandardCharsets.UTF_8))) {
            for (Map<String, Object> row : rows) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object value : row.values()) {
                    if (!first)
                        sb.append(',');
                    sb.append(escapeCsvField(value));
                    first = false;
                }
                pw.println(sb.toString());
            }
        }
    }

    /**
     * Escape nilai untuk format CSV:
     * - null → kosong
     * - Nilai yang mengandung koma, newline, atau tanda kutip → dibungkus
     * double-quote,
     * tanda kutip di dalam di-escape menjadi dua tanda kutip ("")
     */
    private String escapeCsvField(Object value) {
        if (value == null)
            return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * Kemas seluruh file CSV di folder wbill-h0 menjadi satu ZIP in-memory.
     * Mengembalikan byte[] ZIP, atau null jika folder kosong.
     */
    public byte[] downloadAsZip() throws IOException {
        File outputDir = new File(wbillH0OutputPath);
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            return null;
        }
        File[] csvFiles = outputDir.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (File csv : csvFiles) {
                zos.putNextEntry(new ZipEntry(csv.getName()));
                Files.copy(csv.toPath(), zos);
                zos.closeEntry();

                boolean deleted = csv.delete();
                if (!deleted) {
                    log.warn("Gagal menghapus file: {}", csv.getName());
                }
            }
        }
        return baos.toByteArray();
    }

    /** Kembalikan nama ZIP file. */
    public String getZipFileName() {
        return "wbill_h0_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".zip";
    }
}
