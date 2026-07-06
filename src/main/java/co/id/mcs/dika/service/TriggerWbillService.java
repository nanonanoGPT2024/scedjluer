package co.id.mcs.dika.service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import co.id.mcs.dika.constant.WbillConstant;
import lombok.extern.log4j.Log4j2;

/**
 * TriggerWbillService
 *
 * Menjalankan query data AGREE Credit Card hari ini (status_date &
 * tanggal_pickup = CURDATE()),
 * lalu menyimpan hasilnya ke file CSV di folder yang dikonfigurasi via
 * {@code upload.wbill.output.path}.
 *
 * File CSV diberi nama dengan format: wbill_<yyyyMMdd_HHmmss>.csv
 */
@Log4j2
@Service
public class TriggerWbillService {

    @Value("${upload.wbill.output.path:D:/home/tele/wbill}")
    private String wbillOutputPath;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // -------------------------------------------------------------------------
    // Public trigger (dipanggil dari controller, dijalankan di background)
    // -------------------------------------------------------------------------

    public void triggerWbill() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.warn("Wbill export is already running. Skipping trigger.");
            return;
        }

        executorService.submit(() -> {
            try {
                runWbillExport();
            } catch (Exception e) {
                log.error("Error during wbill export process: ", e);
            } finally {
                isProcessing.set(false);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private void runWbillExport() throws Exception {
        log.info("Starting Wbill export to folder: {}", wbillOutputPath);

        // Pastikan folder output ada
        File outputDir = new File(wbillOutputPath);
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                log.error("Failed to create output directory: {}", wbillOutputPath);
                return;
            }
            log.info("Created output directory: {}", wbillOutputPath);
        }

        // Buat nama file CSV dengan timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "wbill_" + timestamp + ".csv";
        File csvFile = new File(outputDir, fileName);

        // Jalankan query
        log.info("Executing wbill query...");
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                WbillConstant.WBILL_QUERY, new MapSqlParameterSource()
                        .addValue("pAppReferenceId", null));
        log.info("Query returned {} rows (including header row)", rows.size());

        // Tulis ke CSV
        writeCsv(csvFile, rows);
        log.info("Wbill CSV written to: {}", csvFile.getAbsolutePath());
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
}
