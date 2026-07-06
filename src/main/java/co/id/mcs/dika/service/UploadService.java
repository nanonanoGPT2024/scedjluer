package co.id.mcs.dika.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import co.id.mcs.dika.constant.ActionCode;
import co.id.mcs.dika.exception.AppException;
import co.id.mcs.dika.exception.DataProcessingException;
import co.id.mcs.dika.model.Mapper;
import co.id.mcs.dika.model.MasterKotaUpload;
import co.id.mcs.dika.repository.MapperRepository;
import co.id.mcs.dika.repository.MasterKotaUploadRepository;
import co.id.mcs.dika.util.ResponseUtil;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Comparator;
import java.util.stream.Stream;

@Log4j2
@Service
public class UploadService {

    private final String serviceCode = "005";
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Value("${upload.source.path}")
    private String uploadSourcePath;

    @Value("${app.reference.id}")
    private String defaultAppReferenceId;

    @Autowired
    private ExcelExtractorService excelExtractorService;

    @Autowired
    private MapperRepository mapperRepository;

    @Autowired
    private MasterKotaUploadRepository masterKotaUploadRepository;

    @Transactional(timeout = 300)
    public ResponseEntity<?> processUpload(MultipartFile file, String userId, String appReferenceId) {

        try {
            validateFile(file);

            String fileName = file.getOriginalFilename().toLowerCase();

            Object result;
            try (InputStream inputStream = file.getInputStream()) {
                result = excelExtractorService.extractWithRules(inputStream, fileName, userId, appReferenceId);
            }
            return ResponseUtil.build(serviceCode, ActionCode.UPLOAD, result);

        } catch (Exception e) {
            throw new DataProcessingException(serviceCode, ActionCode.UPLOAD, e);
        }
    }

    /**
     * Simpan file ke folder trigger-auto (upload.source.path).
     * File akan diambil saat trigger-auto dijalankan.
     */
    public void saveToAutoFolder(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(400, "File is mandatory");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || (!originalFilename.toLowerCase().endsWith(".xls")
                        && !originalFilename.toLowerCase().endsWith(".xlsx"))) {
            throw new AppException(400, "Only Excel files (.xls, .xlsx) are allowed");
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
            log.info("Saved auto-upload file to: {}", destFile);
        } catch (IOException e) {
            throw new AppException(500, "Failed to save file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AppException(400, "File is mandatory");
        }

        if (file.getSize() > 100 * 1024 * 1024) { // 100MB
            throw new AppException(400, "File size exceeds 100MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.toLowerCase().endsWith(".xls")
                        && !originalFilename.toLowerCase().endsWith(".xlsx"))) {
            throw new AppException(400, "Only Excel files (.xls, .xlsx) are allowed");
        }

        if (checkDateExtraction(originalFilename)) {
            throw new AppException(400, "Failed to extract date from filename");
        }
    }

    /**
     * Determine file type from filename
     */
    private String determineFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.contains("smartcash")) {
            return "SMARTCASH";
        } else if (lowerFileName.contains("kartu kredit")) {
            return "KARTU KREDIT";
        } else if (lowerFileName.contains("supplemen")) {
            return "SUPPLEMEN";
        } else {
            return "TIKET";
        }
    }

    /**
     * Check if date can be extracted from filename based on mapper's
     * extrak_date_mode
     * 
     * @param fileName nama file yang di-upload
     * @return true if date extraction fails, false if successful
     */
    private boolean checkDateExtraction(String fileName) {
        try {
            // Determine file type
            String fileType = determineFileType(fileName);

            // Get mapper for this file type
            var mappers = mapperRepository.where("file", "=", fileType).select();
            if (mappers.isEmpty()) {
                log.warn("Mapper not found for file type: {}", fileType);
                return true; // Extraction fails if no mapper found
            }

            Mapper mapper = mappers.get(0);
            Integer extractMode = mapper.getExtrakDateMode();

            if (extractMode == null) {
                log.warn("Extrak date mode not configured for file type: {}", fileType);
                return true; // Extraction fails if mode not configured
            }

            // Validate based on extraction mode
            if (extractMode == 1) {
                // Mode 1: SMARTCASH / KARTU KREDIT - check for _sftp_ pattern
                String upperFileName = fileName.toUpperCase();
                int start = upperFileName.indexOf("IN ") + 3;
                int end = upperFileName.indexOf("_SFTP");
                if (upperFileName.indexOf("IN ") == -1 || end == -1 || start >= end) {
                    return true;
                }

                Pattern pattern = Pattern.compile("\\d{2}(\\d{6})");
                Matcher matcher = pattern.matcher(fileName);
                return !matcher.find();

            } else if (extractMode == 2) {
                // Mode 2: TIKET / SUPPLEMEN - check for 4 digit suffix
                String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
                try {
                    Integer.parseInt(fileNameWithoutExtension.substring(fileNameWithoutExtension.length() - 4));
                    return false; // Extraction successful
                } catch (Exception e) {
                    return true; // Extraction failed
                }
            } else {
                log.warn("Invalid extrak date mode: {} for file type: {}", extractMode, fileType);
                return true;
            }

        } catch (Exception e) {
            log.error("Error checking date extraction for file: {}", fileName, e);
            return true; // Extraction fails on error
        }
    }

    // @Async
    // @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    public void autoProcessUpload() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.warn("Auto-process upload is already running. Skipping this trigger.");
            return;
        }

        try {
            log.warn("Starting auto-process upload task");

            File folder = new File(uploadSourcePath);
            if (!folder.exists() || !folder.isDirectory()) {
                log.error("Upload source path does not exist or is not a directory: {}", uploadSourcePath);
                return;
            }

            File[] files = folder
                    .listFiles(
                            (dir, name) -> name.toLowerCase().endsWith(".xls") || name.toLowerCase().endsWith(".xlsx"));

            if (files == null || files.length == 0) {
                log.warn("No Excel files found in {}", uploadSourcePath);
                return;
            }

            UUID creditCardProductId = UUID.fromString("9faa8725-7702-42ba-a0ff-fa28b6b4224b");
            UUID smartcashProductId = UUID.fromString("9d1467c5-7c29-42f7-869e-0308a6c9666b");

            List<MasterKotaUpload> kotaKartuKredit = masterKotaUploadRepository
                    .where("is_description", "=", 1)
                    .andWhere("id_product", "=", creditCardProductId)
                    .select();

            List<MasterKotaUpload> kotaSmartcash = masterKotaUploadRepository
                    .where("is_description", "=", 1)
                    .andWhere("id_product", "=", smartcashProductId)
                    .select();

            for (File file : files) {
                String fileName = file.getName();

                if (!file.exists()) {
                    log.warn("File no longer exists (may have been processed by another thread): {}", fileName);
                    continue;
                }

                if (fileName.contains("Kartu Kredit") || fileName.contains("Smartcash")) {
                    String fileNameUpperCase = fileName.toUpperCase(java.util.Locale.ROOT);
                    List<MasterKotaUpload> masterKotaUploads = fileName.contains("Kartu Kredit")
                            ? kotaKartuKredit
                            : kotaSmartcash;

                    boolean kotaDitemukan = false;
                    if (masterKotaUploads != null) {
                        kotaDitemukan = masterKotaUploads.stream()
                                .map(MasterKotaUpload::getKota)
                                .filter(kota -> kota != null && !kota.isBlank())
                                .anyMatch(kota -> fileNameUpperCase
                                        .contains(kota.trim().toUpperCase(java.util.Locale.ROOT)));
                    }

                    if (!kotaDitemukan) {
                        log.warn("File dilewati karena kota tidak terdaftar: {}", fileName);
                        continue;
                    }
                }

                File processingFile = new File(file.getAbsolutePath() + ".processing");
                if (!file.renameTo(processingFile)) {
                    log.warn("Failed to lock file (might be processed by another thread or still uploading): {}",
                            fileName);
                    continue;
                }

                java.time.LocalDateTime startTime = java.time.LocalDateTime.now();
                log.warn("Processing file: {} | Start Time: {}", fileName, startTime);

                if (checkDateExtraction(fileName)) {
                    log.warn("Skipping file due to date extraction failure: {}", fileName);
                    processingFile.renameTo(new File(file.getAbsolutePath() + ".error"));
                    continue;
                }

                try {
                    excelExtractorService.extractWithRules(processingFile, fileName, null, defaultAppReferenceId);
                    java.time.LocalDateTime endTime = java.time.LocalDateTime.now();
                    log.warn("Successfully processed file: {} | End Time: {} | Duration: {} ms",
                            fileName, endTime, java.time.Duration.between(startTime, endTime).toMillis());

                    boolean deleted = false;
                    for (int i = 0; i < 3; i++) {
                        try {
                            java.nio.file.Files.delete(processingFile.toPath());
                            deleted = true;
                            log.warn("Successfully deleted file after processing: {}", fileName);
                            break;
                        } catch (java.nio.file.FileSystemException e) {
                            log.warn("Attempt {} - File locked, waiting to delete: {}", i + 1,
                                    e.getMessage());
                            System.gc(); // Force GC to release memory-mapped POI locks on Windows
                            Thread.sleep(1000); // Wait 1 second before retrying
                        } catch (Exception e) {
                            log.error("Error deleting file: {}", e.getMessage());
                            break;
                        }
                    }

                    if (!deleted) {
                        log.error("Failed to delete file after 3 attempts. Manual cleanup may be required: {}",
                                fileName);
                        processingFile.renameTo(new File(file.getAbsolutePath() + ".delete_failed"));
                    }
                } catch (Exception e) {
                    log.error("Failed to process file: {} | Error at: {}", fileName, java.time.LocalDateTime.now(), e);
                    processingFile.renameTo(new File(file.getAbsolutePath() + ".error"));
                }
            }

        } finally {
            isProcessing.set(false);
            log.warn("Finished auto-process upload task");
        }
    }

    public void extractZipToUploadFolder(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(400, "File is mandatory");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new AppException(400, "Only ZIP files are allowed");
        }

        try {
            Path destDir = Paths.get(uploadSourcePath);
            if (!Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    if (!zipEntry.isDirectory()) {
                        String name = zipEntry.getName();
                        Path targetPath = destDir.resolve(name).normalize();
                        if (!targetPath.startsWith(destDir)) {
                            throw new IOException("Bad zip entry: " + name);
                        }
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zipEntry = zis.getNextEntry();
                }
            }
            log.info("Successfully extracted zip file {} to {}", originalFilename, destDir);
        } catch (IOException e) {
            throw new AppException(500, "Failed to extract zip file: " + e.getMessage());
        }
    }

    public void cleanUploadFolder() {
        try {
            Path dir = Paths.get(uploadSourcePath);
            if (!Files.exists(dir)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                if (!path.equals(dir)) {
                                    Files.delete(path);
                                }
                            } catch (IOException e) {
                                log.error("Failed to delete path: {}", path, e);
                            }
                        });
            }
            log.info("Successfully cleaned upload folder: {}", dir);
        } catch (IOException e) {
            throw new AppException(500, "Failed to clean upload folder: " + e.getMessage());
        }
    }

}
