package br.com.clinica.controller;

import br.com.clinica.dto.DocumentoClinicoDto;
import br.com.clinica.dto.DocumentoClinicoRequest;
import br.com.clinica.model.DocumentoClinico;
import br.com.clinica.repository.DocumentoClinicoRepository;
import br.com.clinica.service.DocumentoClinicoEscritaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/documentos-clinicos")
public class DocumentoClinicoController {

    private final DocumentoClinicoRepository repository;
    private final DocumentoClinicoEscritaService escritaService;

    public DocumentoClinicoController(DocumentoClinicoRepository repository, DocumentoClinicoEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public List<DocumentoClinico> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public DocumentoClinico buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<DocumentoClinicoDto> criar(@RequestBody DocumentoClinicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public DocumentoClinicoDto atualizar(@PathVariable Integer id, @RequestBody DocumentoClinicoRequest request) {
        return escritaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Integer id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
