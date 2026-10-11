package br.com.clinica.controller;

import br.com.clinica.dto.PosVendaCsvConfigRequest;
import br.com.clinica.service.PosVendaCsvImportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/pos-venda/importacoes")
public class PosVendaImportacaoController {
    private final PosVendaCsvImportService service;

    public PosVendaImportacaoController(PosVendaCsvImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> preview(@RequestPart("arquivo") MultipartFile file,
            @RequestParam(defaultValue = "America/Fortaleza") String fuso) {
        return service.preview(file, fuso);
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@RequestPart("arquivo") MultipartFile file,
            @Valid @RequestPart("configuracao") PosVendaCsvConfigRequest config, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer author)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login para importar dados");
        }
        return service.importar(file, config, author);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return service.detail(id);
    }
}
