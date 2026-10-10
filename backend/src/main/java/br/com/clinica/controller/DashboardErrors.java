package br.com.clinica.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalidFilter(ResponseStatusException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatusCode()).body(body("invalid_filter", error.getReason(), request));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> invalidJson(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("invalid_request", "Informe um JSON válido e datas no formato AAAA-MM-DD.", request));
    }
    private Map<String, Object> body(String code, String message, HttpServletRequest request) {
        return Map.of("error", Map.of("code", code, "message", message), "message", message,
            "requestId", String.valueOf(request.getAttribute("requestId")));
    }
}
