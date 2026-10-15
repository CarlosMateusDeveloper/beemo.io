package br.com.clinica.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {IntegracaoApiController.class, IntegracaoGestaoController.class})
public class IntegracaoErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException error, HttpServletRequest request) {
        String code = error.getStatusCode().value() == 409 ? "integration_conflict" : "integration_request_invalid";
        return ResponseEntity.status(error.getStatusCode()).body(body(code,
                error.getReason() == null ? "Não foi possível processar a integração" : error.getReason(), request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("integration_payload_invalid",
                "O payload não corresponde ao contrato da integração.", request));
    }

    private Map<String,Object> body(String code, String message, HttpServletRequest request) {
        return Map.of("error", Map.of("code",code,"message",message),
                "requestId", Objects.toString(request.getAttribute("requestId"), ""));
    }
}
