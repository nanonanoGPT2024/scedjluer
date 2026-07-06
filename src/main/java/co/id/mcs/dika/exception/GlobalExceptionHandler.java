package co.id.mcs.dika.exception;

import co.id.mcs.dika.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ErrorResponse> handleApiException(
                        ApiException ex,
                        HttpServletRequest request) {

                log.error("Api exception: {} - {}", ex.getHttpStatus(), ex.getMessage());

                ErrorResponse error = new ErrorResponse(
                                false,
                                ex.getHttpStatus().value(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(ex.getHttpStatus())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(error);
        }

        @ExceptionHandler(AppException.class)
        public ResponseEntity<ErrorResponse> handleAppException(
                        AppException ex,
                        HttpServletRequest request) {

                log.error("App exception: {} - {}", ex.getStatusCode(), ex.getMessage());

                ErrorResponse error = new ErrorResponse(
                                false,
                                ex.getStatusCode(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(ex.getStatusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Object> handleValidationException(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                Map<String, String> fieldErrors = new HashMap<>();
                ex.getBindingResult().getAllErrors().forEach(error -> {
                        String fieldName = ((FieldError) error).getField();
                        // Hilangkan prefix "payload." agar FE lebih mudah mapping ke form
                        if (fieldName.startsWith("payload.")) {
                                fieldName = fieldName.substring(8);
                        }
                        String errorMessage = error.getDefaultMessage();
                        fieldErrors.put(fieldName, errorMessage);
                });

                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("status", 400);
                response.put("message", "Validation failed");
                response.put("errors", fieldErrors);
                response.put("path", request.getRequestURI());
                response.put("timestamp", LocalDateTime.now());

                log.error("Validation error: {}", fieldErrors);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(response);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Object> handleHttpMessageNotReadableException(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {

                Map<String, String> fieldErrors = new HashMap<>();

                // Cari tau apakah penyebabnya karena gagal parse format (misal tipe Date)
                if (ex.getCause() instanceof InvalidFormatException ife) {
                        // Ambil index terakhir dari path untuk mendapatkan nama properti terdalam
                        // (misal: "beginDate", bukan "payload")
                        int pathSize = ife.getPath().size();
                        String fieldName = ife.getPath().get(pathSize - 1).getFieldName();

                        // Potong awalan 'payload.' untuk berjaga-jaga jika path masih menempel
                        if (fieldName.startsWith("payload.")) {
                                fieldName = fieldName.substring(8);
                        }

                        fieldErrors.put(fieldName, "Format tidak valid (" + ife.getValue()
                                        + "). Harap gunakan format yang benar: yyyy-MM-dd");
                } else {
                        fieldErrors.put("request", "Format data JSON tidak valid atau rusak.");
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("status", 400);
                response.put("message", "Data format invalid");
                response.put("errors", fieldErrors);
                response.put("path", request.getRequestURI());
                response.put("timestamp", LocalDateTime.now());

                log.error("Parse error: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGlobalException(
                        Exception ex,
                        HttpServletRequest request) {

                log.error("Unexpected error: ", ex);

                ErrorResponse error = new ErrorResponse(
                                false,
                                500,
                                "Internal server error",
                                request.getRequestURI(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(error);
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
                        NoResourceFoundException ex,
                        HttpServletRequest request) {

                if (request.getRequestURI().endsWith("favicon.ico")) {
                        log.trace("Favicon not found (ignoring)");
                } else if (request.getRequestURI().endsWith("/metrics")) {
                        log.trace("Metrics endpoint not found (ignoring)");
                } else {
                        log.warn("Resource not found: {}", request.getRequestURI());
                }

                ErrorResponse error = new ErrorResponse(
                                false,
                                HttpStatus.NOT_FOUND.value(),
                                "Resource not found",
                                request.getRequestURI(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(error);
        }

        @ExceptionHandler(DataProcessingException.class)
        public ResponseEntity<ErrorResponse> handleDataProcessingException(
                        DataProcessingException ex,
                        HttpServletRequest request) {

                // Check for duplicate error
                Throwable cause = ex.getCause();
                boolean isDuplicate = false;
                while (cause != null) {
                        if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("duplicate")) {
                                isDuplicate = true;
                                break;
                        }
                        cause = cause.getCause();
                }

                HttpStatus status;
                String message;

                if (isDuplicate) {
                        status = HttpStatus.CONFLICT;
                        message = "Data Already Exist";
                        log.error("Duplicate data error in service {}-{}: {}", ex.getServiceCode(), ex.getActionCode(),
                                        ex.getCause().getMessage());
                } else if (ex.getCause() instanceof AppException appEx) {
                        status = HttpStatus.valueOf(appEx.getStatusCode());
                        message = appEx.getMessage();
                        log.error("App error in service {}-{}: {}", ex.getServiceCode(), ex.getActionCode(),
                                        appEx.getMessage());
                } else {
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                        // If cause exists, it's a technical error -> Mask it.
                        // If cause is null, it's a manual validation message -> Show it.
                        message = ex.getCause() != null ? "Internal Server Error" : ex.getMessage();

                        log.error("Error in service {}-{}: ", ex.getServiceCode(), ex.getActionCode(), ex.getCause());
                }

                ErrorResponse error = new ErrorResponse(
                                false,
                                status.value(),
                                message,
                                request.getRequestURI(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(status)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(error);
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<co.id.mcs.dika.dto.BaseResponse<String>> handleResourceNotFoundException(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {

                co.id.mcs.dika.dto.BaseResponse<String> response = new co.id.mcs.dika.dto.BaseResponse<>();
                response.setCode(ex.getServiceCode() + "-" + HttpStatus.NOT_FOUND.value() + "-" + ex.getActionCode());
                response.setMessage(ex.getMessage());

                log.error("Data not found: " + response.toString());

                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
}
