package br.com.clinica.controller;

import br.com.clinica.dto.RecursoGlosaDocumentoRequest;
import br.com.clinica.dto.RecursoGlosaDto;
import br.com.clinica.dto.RecursoGlosaEnviarRequest;
import br.com.clinica.dto.RecursoGlosaRequest;
import br.com.clinica.dto.RecursoGlosaResultadoRequest;
import br.com.clinica.model.RecursoGlosa;
import br.com.clinica.model.RecursoGlosaDocumento;
import br.com.clinica.service.RecursoGlosaEscritaService;
import br.com.clinica.service.RecursoGlosaLeituraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Ciclo de vida de um recurso de glosa já criado (spec seções 6-12) — a
// criação (POST) fica em GlosaController, aninhada em /api/glosas/{id}/recursos,
// porque precisa do contexto da glosa-mãe.
@RestController
@RequestMapping("/api/recursos")
public class RecursoGlosaController {

    private final RecursoGlosaLeituraService leituraService;
    private final RecursoGlosaEscritaService escritaService;

    public RecursoGlosaController(RecursoGlosaLeituraService leituraService, RecursoGlosaEscritaService escritaService) {
        this.leituraService = leituraService;
        this.escritaService = escritaService;
    }

    @GetMapping("/{id}")
    public RecursoGlosaDto buscar(@PathVariable Integer id) {
        return leituraService.buscar(id);
    }

    @PutMapping("/{id}")
    public RecursoGlosa atualizar(@PathVariable Integer id, @RequestBody RecursoGlosaRequest request) {
        return escritaService.atualizar(id, request);
    }

    @PostMapping("/{id}/documentos")
    public ResponseEntity<RecursoGlosaDocumento> anexarDocumento(
            @PathVariable Integer id, @RequestBody RecursoGlosaDocumentoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.anexarDocumento(id, request));
    }

    @PostMapping("/{id}/enviar")
    public RecursoGlosa enviar(@PathVariable Integer id, @RequestBody RecursoGlosaEnviarRequest request) {
        return escritaService.enviar(id, request);
    }

    @PostMapping("/{id}/resultado")
    public RecursoGlosa registrarResultado(@PathVariable Integer id, @RequestBody RecursoGlosaResultadoRequest request) {
        return escritaService.registrarResultado(id, request);
    }
}
