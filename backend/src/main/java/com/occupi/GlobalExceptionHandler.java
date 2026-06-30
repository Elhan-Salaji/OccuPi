package com.occupi; // ← change this to match the folder you place it in

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central mapping of exceptions to HTTP responses.
 *
 * <p>Keeps controllers free of manual null/blank checks: invalid request bodies
 * (bean validation) and invalid arguments raised in services/repositories
 * ({@link IllegalArgumentException}) are both reported as 400 Bad Request with
 * a small, consistent error body.</p>
 *
 * <p>{@code RoomNotFoundException} is intentionally not handled here; it already
 * maps itself to 404 via {@code @ResponseStatus} and needs no extra wiring.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles invalid arguments raised anywhere in the call stack (controller,
     * service, repository) — e.g. a roomId containing disallowed characters.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Handles {@code @Valid} bean validation failures on {@code @RequestBody} DTOs,
     * collecting all field errors into a single readable response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
