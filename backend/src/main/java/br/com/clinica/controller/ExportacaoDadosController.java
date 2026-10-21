package br.com.clinica.controller;

import br.com.clinica.service.ExportacaoDadosService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exportacoes")
public class ExportacaoDadosController {
    private final ExportacaoDadosService service;
    public ExportacaoDadosController(ExportacaoDadosService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Map<String,Object>> solicitar(Authentication authentication, HttpServletRequest request) {
        int user = (Integer) authentication.getPrincipal();
        request.setAttribute("clinicos.audit.action", "dados.exportar");
        return ResponseEntity.accepted().body(service.solicitar(user));
    }

    @GetMapping
    public List<Map<String,Object>> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public Map<String,Object> detalhe(@PathVariable UUID id) { return service.resumo(id); }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> baixar(@PathVariable UUID id, @RequestParam String token, HttpServletRequest request) {
        request.setAttribute("clinicos.audit.action", "dados.exportar.download");
        var arquivo = service.baixar(id, token);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + arquivo.filename())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(arquivo.bytes());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(@PathVariable UUID id, HttpServletRequest request) {
        request.setAttribute("clinicos.audit.action", "dados.exportar.revogar");
        service.revogar(id);
        return ResponseEntity.noContent().build();
    }
}
