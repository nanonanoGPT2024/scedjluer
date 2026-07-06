package co.id.mcs.dika.service;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.id.mcs.dika.exception.AppException;
import co.id.mcs.dika.model.CustomerAddress;
import co.id.mcs.dika.model.CustomerPhone;
import co.id.mcs.dika.model.DataCustomerProfile;
import co.id.mcs.dika.model.LogUpload;
import co.id.mcs.dika.model.Mapper;
import co.id.mcs.dika.model.MasterCampaign;
import co.id.mcs.dika.model.OrderData;
import co.id.mcs.dika.model.OrderDataDuplicate;
import co.id.mcs.dika.repository.CustomerAddressRepository;
import co.id.mcs.dika.repository.CustomerPhoneRepository;
import co.id.mcs.dika.repository.DataCustomerProfileRepository;
import co.id.mcs.dika.repository.LogUploadRepository;
import co.id.mcs.dika.repository.MapperRepository;
import co.id.mcs.dika.repository.MasterCampaignRepository;
import co.id.mcs.dika.repository.OrderDataDuplicateRepository;
import co.id.mcs.dika.repository.OrderDataRepository;
import co.id.mcs.dika.util.ExcelExtractor;
import co.id.mcs.dika.util.JdbcUtil;
import co.id.mcs.ptdika.MadMachine.Mapper.Model.MapperRule;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ExcelExtractorService {

    private static final Set<String> DATE_KEYS = Set.of(
            "tanggal_lahir", "tanggalLahir",
            "tanggal_nilai", "tanggalNilai",
            "distribusi_date", "distribusiDate",
            "distribusi_date_spv", "distribusiDateSpv",
            "pickup_result_date", "pickupResultDate",
            "pickup_result_date_ms", "pickupResultDateMs",
            "status_date", "statusDate",
            "agree_date", "agreeDate",
            "last_call", "lastCall");

    private static final AtomicLong GLOBAL_CASE_ID = new AtomicLong(-1);
    private static final AtomicLong GLOBAL_VIP_COUNT = new AtomicLong(-1);
    private final Object initLock = new Object();

    @Autowired
    private MapperRepository mapperRepository;

    @Autowired
    private MasterCampaignRepository masterCampaignRepository;

    @Autowired
    private LogUploadRepository logUploadRepository;

    @Autowired
    private OrderDataRepository orderDataRepository;

    @Autowired
    private DataCustomerProfileRepository dataCustomerProfileRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    private CustomerPhoneRepository customerPhoneRepository;

    @Autowired
    private OrderDataDuplicateRepository orderDataDuplicateRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("ioExecutor")
    private Executor ioExecutor;

    private static final Pattern CAMPAIGN_DATE_PATTERN = Pattern.compile("\\d{2}(\\d{6})");

    public Map<String, Object> extractWithRules(InputStream inputStream, String fileName, String userId) {
        return extractWithRules(inputStream, fileName, userId, null);
    }

    public Map<String, Object> extractWithRules(InputStream inputStream, String fileName, String userId,
            String appReferenceId) {
        java.io.File tempFile = null;
        try {
            tempFile = java.io.File.createTempFile("upload_", "_" + fileName);
            java.nio.file.Files.copy(inputStream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return extractWithRules(tempFile, fileName, userId, appReferenceId);
        } catch (Exception e) {
            log.error("Error processing InputStream upload: {}", e.getMessage());
            return new HashMap<>();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public Map<String, Object> extractWithRules(java.io.File file, String fileName, String userId) {
        return extractWithRules(file, fileName, userId, null, true);
    }

    public Map<String, Object> extractWithRules(java.io.File file, String fileName, String userId,
            String appReferenceId) {
        return extractWithRules(file, fileName, userId, appReferenceId, true);
    }

    public Map<String, Object> extractWithRules(java.io.File file, String fileName, String userId,
            String appReferenceId, boolean async) {
        boolean isLargeFile = file.length() > 10 * 1024 * 1024; // 10MB
        if (isLargeFile) {
            log.warn("Large file detected ({} bytes). Dropping indexes for faster bulk insert...", file.length());
            dropIndexes();
        }

        try {
            String fileType = determineFileType(fileName);
            var mappers = mapperRepository.where("file", "=", fileType).select();
            if (mappers.isEmpty()) {
                throw new AppException(404, "Mapper configuration not found for file type: " + fileType);
            }
            Mapper mapperEntity = mappers.get(0);
            ExtractionConfig config = prepareExtractionConfig(mapperEntity);

            return processStreamingExtraction(file, fileName, userId, mapperEntity, config, appReferenceId, async);
        } catch (Exception e) {
            log.error("Data streaming error: {}", e.getMessage());
            log.error("Stack trace: ", e);
            return new HashMap<>();
        } finally {
            if (isLargeFile) {
                log.warn("Recreating indexes after bulk insert...");
                recreateIndexes();
            }
        }
    }

    private Map<String, Object> processStreamingExtraction(java.io.File file, String fileName, String userId,
            Mapper mapperEntity, ExtractionConfig config, String appReferenceId, boolean async) throws Exception {

        ObjectMapper objectMapper = createObjectMapper();
        BatchContext context = new BatchContext(fileName, mapperEntity, namedParameterJdbcTemplate);
        context.appReferenceId = appReferenceId;

        List<Map<String, Object>> allRowMaps = new ArrayList<>();
        List<Map<String, Object>> previewData = new ArrayList<>();

        // 1. Read all rows into memory (in-memory parsing is extremely fast)
        ExcelExtractor.extract(file, fileName, new int[] { 0 }, 1, "A", config.mapperRules, map -> {
            allRowMaps.add(map);
            if (previewData.size() < 10) {
                previewData.add(new HashMap<>(map));
            }
        });

        if (allRowMaps.isEmpty()) {
            return finalizeLoggingAndResponse(context, previewData, userId);
        }

        // 2. Pre-calculate duplicates against DB (fast check: ~50ms)
        Set<String> custNosToCheck = new HashSet<>();
        for (Map<String, Object> map : allRowMaps) {
            if (map.containsKey("od")) {
                Map<String, Object> odMap = (Map<String, Object>) map.get("od");
                if (odMap != null) {
                    Object cn = odMap.get("cust_no");
                    if (cn != null && !cn.toString().isEmpty()) {
                        custNosToCheck.add(cn.toString());
                    }
                }
            }
        }
        Set<String> existingInDb = fetchExistingCustNos(custNosToCheck);
        Set<String> processedKeysInFile = new HashSet<>();

        // 3. Pre-populate context statsMap in main thread for instant LogUpload insertion
        for (Map<String, Object> map : allRowMaps) {
            if (map.containsKey("od")) {
                Map<String, Object> odMap = (Map<String, Object>) map.get("od");
                if (odMap != null) {
                    Object cn = odMap.get("cust_no");
                    String custNo = cn != null ? cn.toString() : null;
                    boolean isDuplicate = (custNo != null && !custNo.isEmpty()) &&
                            (processedKeysInFile.contains(custNo) || existingInDb.contains(custNo));

                    Object promoObj = odMap.get("promo");
                    String promo = promoObj != null ? promoObj.toString() : null;
                    String campaignCode = determineCampaignCode(context.fileName, promo);

                    String statsKey = (campaignCode != null && !campaignCode.isEmpty()) ? campaignCode : "UNKNOWN";
                    CampaignStats stats = context.statsMap.computeIfAbsent(statsKey, k -> new CampaignStats(k));
                    stats.total++;

                    if (custNo == null || custNo.isEmpty()) {
                        stats.success++;
                    } else if (isDuplicate) {
                        stats.duplicate++;
                    } else {
                        processedKeysInFile.add(custNo);
                        stats.success++;
                    }
                }
            }
        }

        // 4. Save LogUpload in the main thread (takes ~5-10ms)
        Map<String, Object> response = finalizeLoggingAndResponse(context, previewData, userId);

        // 5. Build background context reusing same logIds but reset counts
        Map<String, CampaignStats> bgStatsMap = new HashMap<>();
        for (Map.Entry<String, CampaignStats> entry : context.statsMap.entrySet()) {
            CampaignStats mainStats = entry.getValue();
            CampaignStats bgStats = new CampaignStats(mainStats.campaignCode);
            bgStats.logId = mainStats.logId; // Keep the same logId!
            bgStatsMap.put(entry.getKey(), bgStats);
        }

        BatchContext bgContext = new BatchContext(fileName, mapperEntity, namedParameterJdbcTemplate);
        bgContext.appReferenceId = appReferenceId;
        bgContext.statsMap = bgStatsMap;

        // Reset processedKeysInFile for the background insertion thread
        processedKeysInFile.clear();

        // 6. Run database inserts asynchronously
        CompletableFuture.runAsync(() -> {
            long bgStart = System.currentTimeMillis();
            log.info("[BG-INSERT] Started background database insertion for {} rows...", allRowMaps.size());
            try {
                List<OrderData> currentOrderBatch = new ArrayList<>();
                List<DataCustomerProfile> currentProfileBatch = new ArrayList<>();
                List<CustomerAddress> currentAddressBatch = new ArrayList<>();
                List<CustomerPhone> currentPhoneBatch = new ArrayList<>();
                List<OrderDataDuplicate> currentDuplicateBatch = new ArrayList<>();

                int batchSize = 1000;
                List<Map<String, Object>> inputBuffer = new ArrayList<>();

                for (Map<String, Object> map : allRowMaps) {
                    inputBuffer.add(map);
                    if (inputBuffer.size() >= batchSize) {
                        processInputBuffer(inputBuffer, bgContext, processedKeysInFile, objectMapper, config,
                                currentProfileBatch, currentAddressBatch, currentPhoneBatch, currentOrderBatch,
                                currentDuplicateBatch, async);
                        inputBuffer.clear();
                    }
                }

                if (!inputBuffer.isEmpty()) {
                    processInputBuffer(inputBuffer, bgContext, processedKeysInFile, objectMapper, config,
                            currentProfileBatch, currentAddressBatch, currentPhoneBatch, currentOrderBatch,
                            currentDuplicateBatch, async);
                    inputBuffer.clear();
                }
                log.info("[BG-INSERT] Successfully finished background database insertion for {} rows in {} ms.",
                        allRowMaps.size(), (System.currentTimeMillis() - bgStart));
            } catch (Exception e) {
                log.error("[BG-INSERT] Critical error during background database insertion", e);
            }
        }, ioExecutor);

        // 7. Return response immediately!
        return response;
    }

    private void processInputBuffer(List<Map<String, Object>> buffer, BatchContext context,
            Set<String> processedKeysInFile, ObjectMapper objectMapper, ExtractionConfig config,
            List<DataCustomerProfile> profiles, List<CustomerAddress> addresses, List<CustomerPhone> phones,
            List<OrderData> orders, List<OrderDataDuplicate> duplicates, boolean async) throws Exception {

        long startTotal = System.currentTimeMillis();

        // 1. Batch Duplicate Check from DB
        long startDup = System.currentTimeMillis();
        Set<String> custNosToCheck = new HashSet<>();
        for (Map<String, Object> map : buffer) {
            if (map.containsKey("od")) {
                Map<String, Object> odMap = (Map<String, Object>) map.get("od");
                Object cn = odMap.get("cust_no");
                if (cn != null && !cn.toString().isEmpty()) {
                    custNosToCheck.add(cn.toString());
                }
            }
        }
        Set<String> existingInDb = fetchExistingCustNos(custNosToCheck);
        long endDup = System.currentTimeMillis();
        log.info("[PERF] Duplicate check for {} cust_nos took {} ms", custNosToCheck.size(), (endDup - startDup));

        // 2. Process Row Logic
        long startRowLogic = System.currentTimeMillis();
        for (Map<String, Object> map : buffer) {
            try {
                fixExcelDates(map);
                UUID customerId = UUID.randomUUID();

                OrderData od = null;
                if (map.containsKey("od")) {
                    od = objectMapper.convertValue(map.get("od"), OrderData.class);
                    if (od.getId() == null)
                        od.setId(UUID.randomUUID());
                    od.setCustomerId(customerId);
                }

                if (od != null) {
                    String custNo = od.getCustNo();
                    boolean isDuplicate = (custNo != null && !custNo.isEmpty()) &&
                            (processedKeysInFile.contains(custNo) || existingInDb.contains(custNo));

                    String promo = od.getPromo();
                    String campaignCode = determineCampaignCode(context.fileName, promo);
                    assignCampaignToOrder(od, campaignCode, context);

                    String statsKey = (campaignCode != null && !campaignCode.isEmpty()) ? campaignCode : "UNKNOWN";
                    CampaignStats stats = context.statsMap.computeIfAbsent(statsKey, k -> new CampaignStats(k));
                    stats.total++;

                    if (custNo == null || custNo.isEmpty()) {
                        od.setFlagVip(true);
                        od.setCustNo("VIP-" + GLOBAL_VIP_COUNT.incrementAndGet());
                        prepareValidOrder(od, stats, context);
                        orders.add(od);
                        stats.success++;

                        // Add related data ONLY for non-duplicates
                        addRelatedData(map, customerId, config, objectMapper, profiles, addresses, phones,
                                context.appReferenceId);
                    } else if (isDuplicate) {
                        stats.duplicate++;
                        OrderDataDuplicate dup = new OrderDataDuplicate();
                        dup.setId(UUID.randomUUID());
                        dup.setCustNo(custNo);
                        dup.setCampaignCode(campaignCode);
                        dup.setCreatedDate(new Date());
                        dup.setLogId(stats.logId);
                        dup.setError("Duplicate entry");
                        duplicates.add(dup);
                    } else {
                        processedKeysInFile.add(custNo);
                        od.setFlagVip(false);
                        prepareValidOrder(od, stats, context);
                        orders.add(od);
                        stats.success++;

                        // Add related data ONLY for non-duplicates
                        addRelatedData(map, customerId, config, objectMapper, profiles, addresses, phones,
                                context.appReferenceId);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing row in buffer: {}", e.getMessage());
            }
        }
        long endRowLogic = System.currentTimeMillis();
        log.info("[PERF] Row logic mapping/conversion for {} rows took {} ms", buffer.size(),
                (endRowLogic - startRowLogic));

        // 3. Flush to DB
        long startFlush = System.currentTimeMillis();
        flushAllBatches(profiles, addresses, phones, orders, duplicates, context, async);
        long endFlush = System.currentTimeMillis();
        log.info("[PERF] DB Flush for {} orders, {} profiles took {} ms", orders.size(), profiles.size(),
                (endFlush - startFlush));

        long endTotal = System.currentTimeMillis();
        log.info("[PERF] Total processInputBuffer time: {} ms", (endTotal - startTotal));
    }

    private void addRelatedData(Map<String, Object> map, UUID customerId, ExtractionConfig config,
            ObjectMapper objectMapper, List<DataCustomerProfile> profiles, List<CustomerAddress> addresses,
            List<CustomerPhone> phones, String appReferenceId) {
        if (map.containsKey("cp")) {
            ((Map<String, Object>) map.get("cp")).put("id", customerId);
            DataCustomerProfile cp = objectMapper.convertValue(map.get("cp"), DataCustomerProfile.class);
            cp.setAppReferenceId(appReferenceId);
            profiles.add(cp);
        }
        processAddressesAndPhones(map, customerId, config.keyToRuleMap, objectMapper, addresses, phones);
    }

    private Set<String> fetchExistingCustNos(Set<String> custNos) {
        if (custNos.isEmpty())
            return new HashSet<>();
        try {
            String sql = "SELECT cust_no FROM order_data " +
                    "WHERE cust_no IN (:custNos) " +
                    "AND created_date >= CURRENT_DATE - INTERVAL '3 months'";
            List<String> results = namedParameterJdbcTemplate.queryForList(
                    sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("custNos", custNos),
                    String.class);
            return new HashSet<>(results);
        } catch (Exception e) {
            log.warn("Batch DB cust_no check failed, falling back to empty set: {}", e.getMessage());
            if (e instanceof org.springframework.dao.DataAccessException)
                throw e;
            return new HashSet<>();
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // Handler untuk format tanggal jika invalid set null
        objectMapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleWeirdStringValue(DeserializationContext ctxt,
                    Class<?> targetType, String valueToConvert, String failureMsg) throws IOException {
                if (targetType == Date.class || targetType == LocalDate.class
                        || targetType == java.time.LocalDateTime.class) {
                    return null;
                }
                return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
            }
        });

        return objectMapper;
    }

    private static class ExtractionConfig {
        List<MapperRule> mapperRules;
        Map<String, Map<String, Object>> keyToRuleMap;

        ExtractionConfig(List<MapperRule> mapperRules, Map<String, Map<String, Object>> keyToRuleMap) {
            this.mapperRules = mapperRules;
            this.keyToRuleMap = keyToRuleMap;
        }
    }

    private ExtractionConfig prepareExtractionConfig(Mapper mapperEntity) throws Exception {
        // Parse JSON rules
        ObjectMapper objectMapper = createObjectMapper();

        List<Map<String, Object>> rulesList = objectMapper.readValue(
                mapperEntity.getMapper(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        List<MapperRule> mapperRules = new ArrayList<>();
        Map<String, Map<String, Object>> keyToRuleMap = new HashMap<>();

        for (Map<String, Object> rule : rulesList) {
            String headerName = (String) rule.get("header");
            Object toObj = rule.get("to");
            String targetKey = null;

            if (toObj instanceof Map) {
                Map<?, ?> toMap = (Map<?, ?>) toObj;
                String name = (String) toMap.get("name");
                String alias = (String) toMap.get("alias");

                if (alias != null && !alias.isEmpty()) {
                    if ("ca".equals(alias) || "cph".equals(alias)) {
                        targetKey = alias + "." + headerName;
                    } else {
                        targetKey = alias + "." + name;
                    }
                } else {
                    targetKey = name;
                }
            }

            if (headerName != null && targetKey != null) {
                mapperRules.add(new MapperRule(targetKey, headerName));
                keyToRuleMap.put(targetKey, rule);
            }
        }
        return new ExtractionConfig(mapperRules, keyToRuleMap);
    }

    /**
     * Menentukan campaign code berdasarkan nama file
     * 
     * @param fileName nama file yang di-upload
     * @return campaign code yang sesuai
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
     * Menentukan file type berdasarkan nama file
     * 
     * @param fileName nama file yang di-upload
     * @return file type yang sesuai
     */
    private String determineCampaignCode(String fileName, String promo) {
        String lowerFileName = fileName.toLowerCase();
        String campaign_name = "";

        if (lowerFileName.contains("smartcash") || lowerFileName.contains("kartu kredit")) {

            String upperFileName = fileName.toUpperCase();
            int start = upperFileName.indexOf("IN ") + 3;
            int end = upperFileName.indexOf("_SFTP");
            if (upperFileName.indexOf("IN ") == -1 || end == -1 || start >= end) {
                return null;
            }
            String nama_kota = fileName.substring(start, end);
            nama_kota = nama_kota.replace("_", " ");

            Pattern pattern = CAMPAIGN_DATE_PATTERN;
            Matcher matcher = pattern.matcher(fileName);
            String formattedDate5 = "";
            if (matcher.find()) {
                String value = matcher.group(1);

                String monthPart = value.substring(0, 2);
                String yearPart = value.substring(2);

                int monthNumber = Integer.parseInt(monthPart);
                Month month = Month.of(monthNumber);

                String monthName = month
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        .toUpperCase();

                formattedDate5 = monthName + " " + yearPart;
            }

            campaign_name = promo + " " + nama_kota.toUpperCase() + " " + formattedDate5.toUpperCase();
            return campaign_name;
        } else {
            String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
            int value1 = Integer.parseInt(fileNameWithoutExtension.substring(fileNameWithoutExtension.length() - 4));
            int year = 2000 + (value1 / 100);
            int monthNumber1 = value1 % 100;
            Month month1 = Month.of(monthNumber1);
            String formattedDate4 = month1.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
            if (lowerFileName.contains("supplemen")) {
                campaign_name = "TPNSUP JAKARTA " + formattedDate4.toUpperCase();
            } else {
                String[] filePart = fileName.split("_");
                String result = filePart[0];
                campaign_name = "ADDON " + result.toUpperCase() + " " + formattedDate4.toUpperCase();
            }
            return campaign_name;
        }
    }

    /**
     * Get start or end date from fileName based on mapper's extraction mode
     * 
     * @param fileName nama file yang di-upload
     * @param code     "start" or "end"
     * @param mapper   Mapper entity containing extrak_date_mode
     * @return Date extracted from fileName
     * @throws Exception if pattern not found or parsing fails
     */
    public Date getStartDateFromFileName(String fileName, String code, Mapper mapper) throws Exception {
        int month;
        int year;

        // Use extrak_date_mode from mapper instead of hard-coded file name checks
        Integer extractMode = mapper.getExtrakDateMode();

        if (extractMode == null) {
            throw new IllegalArgumentException(
                    "Extrak date mode tidak ditemukan di mapper untuk file: " + mapper.getFile());
        }

        if (extractMode == 1) {
            // Mode 1: SMARTCASH / KARTU KREDIT pattern (_sftp_DDMMYYYY)
            String lowerFileName = fileName.toLowerCase();
            int sftpIndex = lowerFileName.indexOf("_sftp_");
            if (sftpIndex == -1) {
                throw new IllegalArgumentException("Pattern _sftp_ tidak ditemukan dalam file: " + fileName);
            }

            String tanggal = fileName.substring(sftpIndex + 6).split(" ")[0];
            month = Integer.parseInt(tanggal.substring(2, 4));
            year = Integer.parseInt(tanggal.substring(tanggal.length() - 4));

        } else if (extractMode == 2) {
            // Mode 2: TIKET / SUPPLEMEN pattern (suffix 4 digit: YYMM)
            String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
            String suffix = fileNameWithoutExtension.substring(fileNameWithoutExtension.length() - 4);
            int value = Integer.parseInt(suffix);

            year = 2000 + (value / 100);
            month = value % 100;

        } else {
            throw new IllegalArgumentException(
                    "Extrak date mode tidak valid: " + extractMode + " untuk file: " + mapper.getFile());
        }

        // Common logic untuk kedua mode
        LocalDate startDate = LocalDate.of(year, month, 1);
        if ("start".equals(code)) {
            return java.sql.Date.valueOf(startDate);
        } else if ("end".equals(code)) {
            // End of month
            return java.sql.Date.valueOf(startDate.plusMonths(1).minusDays(1));
        }

        return null;
    }

    private class BatchContext {
        String fileName;
        Mapper mapperEntity;
        NamedParameterJdbcTemplate jdbcTemplate;
        String appReferenceId;

        Map<String, CampaignStats> statsMap = new HashMap<>();
        Map<String, MasterCampaign> campaignCache = new HashMap<>();
        List<MasterCampaign> newCampaigns = new ArrayList<>();

        BatchContext(String fileName, Mapper mapperEntity, NamedParameterJdbcTemplate jdbcTemplate) {
            this.fileName = fileName;
            this.mapperEntity = mapperEntity;
            this.jdbcTemplate = jdbcTemplate;
            init();
        }

        private void init() {
            ensureCountersInitialized();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready. Initializing ExcelExtractorService counters in background...");
        CompletableFuture.runAsync(this::ensureCountersInitialized)
                .exceptionally(ex -> {
                    log.error("Failed to initialize counters on startup", ex);
                    return null;
                });
    }

    private void ensureCountersInitialized() {
        if (GLOBAL_CASE_ID.get() == -1) {
            synchronized (initLock) {
                if (GLOBAL_CASE_ID.get() == -1) {
                    try {
                        // case_id is a numeric type in DB, so we can query MAX(case_id) directly.
                        Long mci = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(
                                "SELECT COALESCE(MAX(case_id), 0) FROM order_data",
                                Long.class);
                        GLOBAL_CASE_ID.set(mci != null ? mci : 0);
                    } catch (Exception e) {
                        log.error("Max case_id fetch failed: {}", e.getMessage());
                        GLOBAL_CASE_ID.set(0);
                    }
                }
            }
        }
        if (GLOBAL_VIP_COUNT.get() == -1) {
            synchronized (initLock) {
                if (GLOBAL_VIP_COUNT.get() == -1) {
                    try {
                        Long mvc = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(
                                "SELECT COALESCE(MAX(SUBSTRING(cust_no FROM 5)::BIGINT), 0) FROM order_data " +
                                        "WHERE cust_no LIKE 'VIP-%' AND cust_no IS NOT NULL",
                                Long.class);
                        GLOBAL_VIP_COUNT.set(mvc != null ? mvc : 0);
                    } catch (Exception e) {
                        log.warn("Max VIP count fetch failed: {}", e.getMessage());
                        GLOBAL_VIP_COUNT.set(0);
                    }
                }
            }
        }
    }

    private void processAddressesAndPhones(Map<String, Object> map, UUID customerId,
            Map<String, Map<String, Object>> keyToRuleMap, ObjectMapper objectMapper, List<CustomerAddress> addresses,
            List<CustomerPhone> phones) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = sdf.format(new Date());

        if (map.containsKey("ca")) {
            Map<String, Object> caMap = (Map<String, Object>) map.get("ca");
            for (Map.Entry<String, Object> entry : caMap.entrySet()) {
                // Skip empty address values — no point inserting blank rows
                Object valObj = entry.getValue();
                if (valObj == null || valObj.toString().trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> rule = keyToRuleMap.get("ca." + entry.getKey());
                if (rule != null) {
                    Map<String, Object> toMap = (Map<String, Object>) rule.get("to");
                    Map<String, Object> entityMap = new HashMap<>();
                    if (toMap.containsKey("identifiers"))
                        entityMap.putAll((Map<String, Object>) toMap.get("identifiers"));
                    entityMap.put((String) toMap.get("name"), valObj);
                    entityMap.put("id", UUID.randomUUID());
                    entityMap.put("customer_id", customerId);
                    entityMap.put("created_date", now);
                    addresses.add(objectMapper.convertValue(entityMap, CustomerAddress.class));
                }
            }
        }
        if (map.containsKey("cph")) {
            Map<String, Object> cphMap = (Map<String, Object>) map.get("cph");
            Set<String> seenPhoneNumbers = new HashSet<>();
            for (Map.Entry<String, Object> entry : cphMap.entrySet()) {
                // Skip empty phone values — no point inserting blank rows
                Object valObj = entry.getValue();
                if (valObj == null || valObj.toString().trim().isEmpty()) {
                    continue;
                }
                // Deduplicate identical phone numbers per customer
                String phoneNormalized = valObj.toString().trim().replaceAll("[^0-9]", "");
                String dedupeKey = phoneNormalized.isEmpty() ? valObj.toString().trim() : phoneNormalized;
                if (!seenPhoneNumbers.add(dedupeKey)) {
                    continue; // already inserted this number for this customer
                }
                Map<String, Object> rule = keyToRuleMap.get("cph." + entry.getKey());
                if (rule != null) {
                    Map<String, Object> toMap = (Map<String, Object>) rule.get("to");
                    Map<String, Object> entityMap = new HashMap<>();
                    if (toMap.containsKey("identifiers"))
                        entityMap.putAll((Map<String, Object>) toMap.get("identifiers"));
                    entityMap.put((String) toMap.get("name"), valObj);
                    entityMap.put("id", UUID.randomUUID());
                    entityMap.put("customer_id", customerId);
                    entityMap.put("created_at", now);
                    phones.add(objectMapper.convertValue(entityMap, CustomerPhone.class));
                }
            }
        }
    }

    private void assignCampaignToOrder(OrderData od, String code, BatchContext ctx) throws Exception {
        if (code == null || code.isEmpty())
            return;
        MasterCampaign mc = ctx.campaignCache.get(code);
        if (mc == null) {
            var existing = masterCampaignRepository.where("campaign_code", "=", code).select();
            if (!existing.isEmpty()) {
                mc = existing.get(0);
            } else {
                mc = new MasterCampaign();
                mc.setId(UUID.randomUUID());
                mc.setCampaignCode(code);
                mc.setCampaignOrigin(code);
                mc.setCreatedDate(new java.util.Date());
                mc.setBeginDate(getStartDateFromFileName(ctx.fileName, "start", ctx.mapperEntity));
                mc.setEndDate(getStartDateFromFileName(ctx.fileName, "end", ctx.mapperEntity));
                mc.setIsActive(true);
                mc.setIsPublished(true);
                mc.setDescription(code);
                mc.setIdType(ctx.mapperEntity.getTypeId());
                mc.setIdProduct(ctx.mapperEntity.getProductId());
                mc.setAppReferenceId(ctx.appReferenceId);
                ctx.newCampaigns.add(mc);
            }
            ctx.campaignCache.put(code, mc);
        }
        od.setCampaignId(mc.getId());
    }

    private void prepareValidOrder(OrderData od, CampaignStats stats, BatchContext ctx) {
        od.setCreatedDate(new Date());
        od.setLogUpload(stats.logId.toString());
        od.setCaseId(GLOBAL_CASE_ID.incrementAndGet());
        od.setCampIsActive(true);
        od.setCampIsPublished(true);
        od.setBusinessCategory(1);
        od.setFlagCall(false);
        od.setFlagReload(false);
        od.setIsPublish(false);
        od.setAppReferenceId(ctx.appReferenceId);
        od.setLastCall(null);
        od.setLastHangupCode(null);
        od.setLastHangupCause(null);
    }

    private void flushAllBatches(List<DataCustomerProfile> profiles, List<CustomerAddress> addresses,
            List<CustomerPhone> phones, List<OrderData> orders, List<OrderDataDuplicate> duplicates, BatchContext ctx, boolean async) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            // Collect logUploads before the transaction clears orders
            List<String> logUploads = new ArrayList<>();
            if (!orders.isEmpty()) {
                for (OrderData od : orders) {
                    if (od.getLogUpload() != null && !logUploads.contains(od.getLogUpload())) {
                        logUploads.add(od.getLogUpload());
                    }
                }
            }

            // Phase 1: Core inserts (campaigns, profiles, orders, duplicates) — atomic transaction
            long[] t = new long[5];
            transactionTemplate.execute(status -> {
                try {
                    t[0] = System.currentTimeMillis();
                    if (!ctx.newCampaigns.isEmpty()) {
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, ctx.newCampaigns, MasterCampaign.class);
                        ctx.newCampaigns.clear();
                    }
                    t[1] = System.currentTimeMillis();
                    if (!profiles.isEmpty()) {
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, profiles, DataCustomerProfile.class);
                        profiles.clear();
                    }
                    t[2] = System.currentTimeMillis();
                    if (!orders.isEmpty()) {
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, orders, OrderData.class);
                        orders.clear();
                    }
                    t[3] = System.currentTimeMillis();
                    if (!duplicates.isEmpty()) {
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, duplicates, OrderDataDuplicate.class);
                        duplicates.clear();
                    }
                    t[4] = System.currentTimeMillis();
                } catch (Exception e) {
                    log.error("Core batch insertion failed, rolling back: {}", e.getMessage());
                    status.setRollbackOnly();
                    throw e;
                }
                return null;
            });

            // Phase 2: Addresses and phones in PARALLEL (auto-committed, independent of each other)
            final List<CustomerAddress> addrBatch = new ArrayList<>(addresses);
            final List<CustomerPhone> phoneBatch = new ArrayList<>(phones);
            addresses.clear();
            phones.clear();

            AtomicLong addrMs = new AtomicLong(0);
            AtomicLong phoneMs = new AtomicLong(0);

            log.info("[PERF] addrBatch size: {}, phoneBatch size: {}", addrBatch.size(), phoneBatch.size());

            CompletableFuture<Void> addrFuture = addrBatch.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.runAsync(() -> {
                        long s = System.currentTimeMillis();
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, addrBatch, CustomerAddress.class);
                        addrMs.set(System.currentTimeMillis() - s);
                    }, ioExecutor);

            CompletableFuture<Void> phoneFuture = phoneBatch.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.runAsync(() -> {
                        long s = System.currentTimeMillis();
                        JdbcUtil.bulkInsert(ctx.jdbcTemplate, phoneBatch, CustomerPhone.class);
                        phoneMs.set(System.currentTimeMillis() - s);
                    }, ioExecutor);

            // Spawn the background thread for details (Phones, Leads, Addresses)
            final List<String> finalLogUploads = logUploads;
            if (async) {
                CompletableFuture.runAsync(() -> {
                    long bgStart = System.currentTimeMillis();
                    log.info("[BG-INSERT] Started background detail insertion for {} phones, {} addresses...",
                            phoneBatch.size(), addrBatch.size());
                    try {
                        // 1. Wait for phones to complete
                        phoneFuture.join();

                        // 2. Insert leads
                        long leadsStart = System.currentTimeMillis();
                        if (!finalLogUploads.isEmpty()) {
                            for (String logUpload : finalLogUploads) {
                                String leadSql = """
                                        INSERT INTO leads (
                                            id, source_id, cust_no, phone, customer_name, created_at,
                                            mock_call, campaign_id, status, app_reference_id,
                                            phone_id, phone_type, is_primary, customer_id
                                        )
                                        SELECT
                                            gen_random_uuid(),
                                            od.id,
                                            od.case_id::text,
                                            cph.phone_number,
                                            cp.name,
                                            NOW(),
                                            false,
                                            od.campaign_id,
                                            'NEW',
                                            od.app_reference_id,
                                            cph.id,
                                            cph.phone_type,
                                            cph.is_primary,
                                            cp.id
                                        FROM order_data od
                                        INNER JOIN data_customer_profile cp ON od.customer_id = cp.id
                                        INNER JOIN customer_phone cph ON od.customer_id = cph.customer_id
                                        WHERE od.log_upload = :logUpload
                                        """;

                                Map<String, Object> paramMap = new HashMap<>();
                                paramMap.put("logUpload", logUpload);
                                ctx.jdbcTemplate.update(leadSql, paramMap);
                            }
                        }
                        long leadsMs = System.currentTimeMillis() - leadsStart;

                        // 3. Wait for addresses to complete
                        addrFuture.join();

                        log.info(
                                "[BG-INSERT] Successfully finished background details insertion in {} ms. Phones: {}ms, Addresses: {}ms, Leads: {}ms",
                                (System.currentTimeMillis() - bgStart), phoneMs.get(), addrMs.get(), leadsMs);
                    } catch (Exception e) {
                        log.error("[BG-INSERT] Background detail insertion failed", e);
                    }
                }, ioExecutor);
            } else {
                // Synchronous Mode (Scheduler) - block and wait for phone, address, and lead inserts
                long bgStart = System.currentTimeMillis();
                log.info("[SYNC-INSERT] Processing detail insertion for {} phones, {} addresses...",
                        phoneBatch.size(), addrBatch.size());
                try {
                    // Wait for phone inserts to finish
                    phoneFuture.join();

                    // Insert leads
                    long leadsStart = System.currentTimeMillis();
                    if (!finalLogUploads.isEmpty()) {
                        for (String logUpload : finalLogUploads) {
                            String leadSql = """
                                    INSERT INTO leads (
                                        id, source_id, cust_no, phone, customer_name, created_at,
                                        mock_call, campaign_id, status, app_reference_id,
                                        phone_id, phone_type, is_primary, customer_id
                                    )
                                    SELECT
                                        gen_random_uuid(),
                                        od.id,
                                        od.case_id::text,
                                        cph.phone_number,
                                        cp.name,
                                        NOW(),
                                        false,
                                        od.campaign_id,
                                        'NEW',
                                        od.app_reference_id,
                                        cph.id,
                                        cph.phone_type,
                                        cph.is_primary,
                                        cp.id
                                    FROM order_data od
                                    INNER JOIN data_customer_profile cp ON od.customer_id = cp.id
                                    INNER JOIN customer_phone cph ON od.customer_id = cph.customer_id
                                    WHERE od.log_upload = :logUpload
                                    """;

                            Map<String, Object> paramMap = new HashMap<>();
                            paramMap.put("logUpload", logUpload);
                            ctx.jdbcTemplate.update(leadSql, paramMap);
                        }
                    }
                    long leadsMs = System.currentTimeMillis() - leadsStart;

                    // Wait for address inserts to finish
                    addrFuture.join();

                    log.info(
                            "[SYNC-INSERT] Successfully finished details insertion in {} ms. Phones: {}ms, Addresses: {}ms, Leads: {}ms",
                            (System.currentTimeMillis() - bgStart), phoneMs.get(), addrMs.get(), leadsMs);
                } catch (Exception e) {
                    log.error("[SYNC-INSERT] Detail insertion failed", e);
                    throw e;
                }
            }

            log.info(
                    "[PERF-BATCH-SYNC] Campaigns: {}ms, Profiles: {}ms, Orders: {}ms, Duplicates: {}ms",
                    (t[1] - t[0]), (t[2] - t[1]), (t[3] - t[2]), (t[4] - t[3]));
        } finally {
            // CRITICAL: Always clear batches to prevent memory leaks/avalanche on failure
            profiles.clear();
            addresses.clear();
            phones.clear();
            orders.clear();
            duplicates.clear();
            ctx.newCampaigns.clear();
        }
    }

    private Map<String, Object> finalizeLoggingAndResponse(BatchContext ctx, List<Map<String, Object>> previewData,
            String userId) {
        int totalAll = 0, totalSuccess = 0, totalDuplicate = 0, totalError = 0;

        for (CampaignStats stats : ctx.statsMap.values()) {
            int currentError = stats.total - stats.success - stats.duplicate;
            totalAll += stats.total;
            totalSuccess += stats.success;
            totalDuplicate += stats.duplicate;
            totalError += currentError;

            LogUpload logUpload = new LogUpload();
            logUpload.setId(stats.logId);
            logUpload.setNamaFile(ctx.fileName);
            logUpload.setTotalData(stats.total);
            logUpload.setSuccess(stats.success);
            logUpload.setDuplicate(stats.duplicate);
            logUpload.setFailedUpload(currentError);
            logUpload.setUploadDate(new java.util.Date());
            logUpload.setCampaignCode(stats.campaignCode);
            logUpload.setAppReferenceId(ctx.appReferenceId);

            if (userId != null && !userId.isEmpty()) {
                try {
                    logUpload.setUploadBy(java.util.UUID.fromString(userId));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID format for userId: {}", userId);
                    logUpload.setUploadBy(null);
                }
            } else {
                logUpload.setUploadBy(null);
            }

            try {
                logUploadRepository.insert(logUpload);
            } catch (Exception e) {
                log.error("Failed to insert LogUpload: {}", e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        Map<String, Integer> summary = new HashMap<>();
        summary.put("total", totalAll);
        summary.put("sukses", totalSuccess);
        summary.put("duplicate", totalDuplicate);
        summary.put("error", totalError);
        response.put("summary", summary);
        response.put("mapping", previewData);
        return response;
    }

    private void fixExcelDates(Map<String, Object> map) {
        if (map == null)
            return;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                String s = (String) val;
                // Excel date serials are typically 5-7 digit numbers (or with decimals for
                // time)
                // Only convert if the key is a known date field to avoid corrupting numeric
                // IDs/numbers like cust_no
                if (DATE_KEYS.contains(entry.getKey()) && s.matches("^\\d{5,7}(\\.\\d+)?$")) {
                    entry.setValue(formatExcelDate(s));
                }
            } else if (val instanceof Map) {
                fixExcelDates((Map<String, Object>) val);
            }
        }
    }

    private String formatExcelDate(String val) {
        try {
            double d = Double.parseDouble(val);
            java.util.Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(d);
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
        } catch (Exception e) {
            return null;
        }
    }

    private static class CampaignStats {
        String campaignCode;
        UUID logId;
        int total = 0;
        int success = 0;
        int duplicate = 0;

        public CampaignStats(String campaignCode) {
            this.campaignCode = campaignCode;
            this.logId = UUID.randomUUID();
        }
    }

    private void dropIndexes() {
        try {
            log.warn("Dropping indexes...");
            namedParameterJdbcTemplate.getJdbcTemplate().execute("DROP INDEX IF EXISTS idx_customer_phone_customer_id");
            namedParameterJdbcTemplate.getJdbcTemplate()
                    .execute("DROP INDEX IF EXISTS idx_customer_phone_customer_id_type");
            namedParameterJdbcTemplate.getJdbcTemplate().execute("DROP INDEX IF EXISTS idx_customer_phone_number");
            namedParameterJdbcTemplate.getJdbcTemplate()
                    .execute("DROP INDEX IF EXISTS idx_customer_address_customer_id");
            namedParameterJdbcTemplate.getJdbcTemplate().execute("DROP INDEX IF EXISTS idx_addr_kota");
            log.warn("Indexes dropped successfully.");
        } catch (Exception e) {
            log.error("Failed to drop indexes: {}", e.getMessage());
        }
    }

    private void recreateIndexes() {
        try {
            log.warn("Recreating indexes...");
            namedParameterJdbcTemplate.getJdbcTemplate().execute(
                    "CREATE INDEX IF NOT EXISTS idx_customer_phone_customer_id_type ON customer_phone(customer_id, phone_type)");
            namedParameterJdbcTemplate.getJdbcTemplate()
                    .execute("CREATE INDEX IF NOT EXISTS idx_customer_phone_number ON customer_phone(phone_number)");
            namedParameterJdbcTemplate.getJdbcTemplate().execute(
                    "CREATE INDEX IF NOT EXISTS idx_customer_address_customer_id ON customer_address(customer_id)");
            namedParameterJdbcTemplate.getJdbcTemplate()
                    .execute("CREATE INDEX IF NOT EXISTS idx_addr_kota ON customer_address(kota)");
            log.warn("Indexes recreated successfully.");
        } catch (Exception e) {
            log.error("Failed to recreate indexes: {}", e.getMessage());
        }
    }
}
