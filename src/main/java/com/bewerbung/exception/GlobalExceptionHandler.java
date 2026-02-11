package com.bewerbung.exception;

import com.bewerbung.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;
import java.util.regex.Pattern;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern SECRET_LIKE_PATTERN = Pattern.compile(
            "(?i)(sk-[a-z0-9\\-_]{8,}|xkeysib-[a-z0-9\\-_]{8,}|bearer\\s+[a-z0-9\\-_\\.]{8,}|api[-_ ]?key\\s*[:=]\\s*[^\\s]+)"
    );

    private static String safeClientMessage(String message) {
        if (message == null || message.isBlank()) {
            return "An unexpected error occurred";
        }
        if (message.contains("GPT_API_KEY") || message.contains("EMAIL_API_KEY")) {
            return "Configuration error. Please contact support.";
        }
        if (SECRET_LIKE_PATTERN.matcher(message).find()) {
            return "An unexpected error occurred";
        }
        return message;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        ApiError apiError = new ApiError("VALIDATION_ERROR", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidRequestBody(HttpMessageNotReadableException ex) {
        logger.warn("Invalid request body: {}", ex.getMessage());
        
        ApiError apiError = new ApiError("INVALID_REQUEST", "Invalid request body format");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        logger.warn("Missing request parameter: {}", ex.getMessage());
        
        String message = "Missing required parameter: " + ex.getParameterName();
        ApiError apiError = new ApiError("MISSING_PARAMETER", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipartException(MultipartException ex) {
        logger.warn("Multipart error: {}", ex.getMessage());
        
        ApiError apiError = new ApiError("FILE_UPLOAD_ERROR", "Error processing file upload: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        ApiError apiError = new ApiError("INVALID_ARGUMENT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(apiError);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        // Ignore favicon.ico and other missing static resources - don't log as error
        if (!ex.getResourcePath().equals("/favicon.ico")) {
            logger.debug("Resource not found: {}", ex.getResourcePath());
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException ex) {
        logger.error("Unhandled runtime exception: {}", ex.getMessage(), ex);
        
        // Include the actual error message for better debugging
        String errorMessage = safeClientMessage(ex.getMessage());
        
        ApiError apiError = new ApiError("INTERNAL_ERROR", errorMessage);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        
        String errorMessage = safeClientMessage(ex.getMessage());
        
        ApiError apiError = new ApiError("INTERNAL_ERROR", errorMessage);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }
}

