package com.bidding.core.web.exception;

import com.bidding.core.domain.exception.DomainRuleException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<Map<String, Object>> handleDomainRuleException(DomainRuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "DOMAIN_RULE_VIOLATION",
                "message", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    /**
     * OPTIMISTIC LOCK EXCEPTION HANDLER:
     * İki işlem aynı versiyondaki açık artırmayı güncellemeye çalıştığında
     * biri commit atar, diğeri bu hatayı alır (HTTP 409 Conflict).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONCURRENT_MODIFICATION_CONFLICT",
                "message", "Another user submitted a bid at the exact same moment. Your bid could not be committed. Please refresh and try again.",
                "details", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "VALIDATION_FAILED",
                "message", errors,
                "timestamp", Instant.now()
        ));
    }
}
