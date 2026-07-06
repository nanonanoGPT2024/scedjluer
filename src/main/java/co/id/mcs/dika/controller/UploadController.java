package co.id.mcs.dika.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.id.mcs.dika.service.UploadService;
import co.id.mcs.dika.service.AutoUpdateService;
import co.id.mcs.dika.service.AutoSubmitService;
import co.id.mcs.dika.service.TriggerWbillService;
import co.id.mcs.dika.service.TriggerDumpService;
import co.id.mcs.dika.service.TriggerWbillH0Service;
import co.id.mcs.dika.service.TriggerWbillH1Service;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    @Autowired
    private UploadService uploadService;

    @Value("${app.reference.id}")
    private String defaultAppReferenceId;

    @Autowired
    private AutoUpdateService autoUpdateService;

    @Autowired
    private AutoSubmitService autoSubmitService;

    @Autowired
    private TriggerWbillService triggerWbillService;

    @Autowired
    private TriggerDumpService triggerDumpService;

    @Autowired
    private TriggerWbillH0Service triggerWbillH0Service;

    @Autowired
    private TriggerWbillH1Service triggerWbillH1Service;

    @PostMapping(value = "/data", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadData(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "user-id", required = false) String userIdDash,
            @RequestHeader(value = "app-reference-id", required = false) String appReferenceId) {

        if (appReferenceId == null || appReferenceId.trim().isEmpty()) {
            appReferenceId = defaultAppReferenceId;
        }

        return uploadService.processUpload(file, userIdDash, appReferenceId);
    }

    @GetMapping("/trigger-auto")
    public ResponseEntity<?> triggerAutoUpload() {
        uploadService.autoProcessUpload();
        return ResponseEntity.ok("Auto-upload triggered manually");
    }

    @PostMapping(value = "/trigger-auto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToAutoFolder(
            @RequestParam("file") MultipartFile file) {
        uploadService.saveToAutoFolder(file);
        return ResponseEntity.ok("File uploaded to auto folder successfully");
    }

    @GetMapping("/triger-update")
    public ResponseEntity<?> triggerUpdate() {
        autoUpdateService.triggerUpdate();
        return ResponseEntity.ok("Update triggered successfully in background");
    }

    @PostMapping(value = "/trigger-update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToUpdateFolder(
            @RequestParam("file") MultipartFile file) {
        autoUpdateService.saveToUpdateFolder(file);
        return ResponseEntity.ok("File uploaded to update folder successfully");
    }

    @GetMapping("/trigger-submit")
    public ResponseEntity<?> triggerSubmit() {
        autoSubmitService.triggerSubmit();
        return ResponseEntity.ok("Submit triggered successfully in background");
    }

    @PostMapping(value = "/trigger-submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToSubmitFolder(
            @RequestParam("file") MultipartFile file) {
        autoSubmitService.saveToSubmitFolder(file);
        return ResponseEntity.ok("File uploaded to submit folder successfully");
    }

    @GetMapping("/trigger-wbill")
    public ResponseEntity<?> triggerWbill() {
        triggerWbillService.triggerWbill();
        return ResponseEntity.ok("Wbill export triggered successfully in background");
    }

    @GetMapping("/trigger-dump")
    public ResponseEntity<?> triggerDump() {
        triggerDumpService.triggerDump();
        return ResponseEntity.ok("Dump export triggered successfully in background");
    }

    @GetMapping("/trigger-dump/download")
    public ResponseEntity<byte[]> downloadDumpZip() throws Exception {
        byte[] zipBytes = triggerDumpService.downloadAsZip();
        if (zipBytes == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No CSV files found in dump folder".getBytes());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", triggerDumpService.getZipFileName());
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    @GetMapping("/trigger-wbill-h0")
    public ResponseEntity<?> triggerWbillH0() {
        triggerWbillH0Service.triggerWbillH0();
        return ResponseEntity.ok("Wbill H0 export triggered successfully in background");
    }

    @GetMapping("/trigger-wbill-h0/download")
    public ResponseEntity<byte[]> downloadWbillH0Zip() throws Exception {
        byte[] zipBytes = triggerWbillH0Service.downloadAsZip();
        if (zipBytes == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No CSV files found in wbill-h0 folder".getBytes());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", triggerWbillH0Service.getZipFileName());
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    @GetMapping("/trigger-wbill-h1")
    public ResponseEntity<?> triggerWbillH1() {
        triggerWbillH1Service.triggerWbillH1();
        return ResponseEntity.ok("Wbill H1 export triggered successfully in background");
    }

    @GetMapping("/trigger-wbill-h1/download")
    public ResponseEntity<byte[]> downloadWbillH1Zip() throws Exception {
        byte[] zipBytes = triggerWbillH1Service.downloadAsZip();
        if (zipBytes == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No CSV files found in wbill-h1 folder".getBytes());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", triggerWbillH1Service.getZipFileName());
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    @PostMapping(value = "/upload-data-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDataZip(
            @RequestParam("file") MultipartFile file) {
        uploadService.extractZipToUploadFolder(file);
        return ResponseEntity.ok("Zip file extracted and saved successfully");
    }

    @GetMapping("/clean-folder-upload")
    public ResponseEntity<?> cleanFolderUpload() {
        uploadService.cleanUploadFolder();
        return ResponseEntity.ok("Upload folder cleaned successfully");
    }
}
