package br.com.clinica.controller;

import br.com.clinica.model.DocumentoClinico;
import br.com.clinica.repository.DocumentoClinicoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/documentos-clinicos")
public class DocumentoClinicoController {

    private final DocumentoClinicoRepository repository;

    public DocumentoClinicoController(DocumentoClinicoRepository repository) {
        this.repository = repository;
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
    public ResponseEntity<DocumentoClinico> criar(@Valid @RequestBody DocumentoClinico documento) {
        DocumentoClinico salvo = repository.save(documento);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public DocumentoClinico atualizar(@PathVariable Integer id, @Valid @RequestBody DocumentoClinico dados) {
        DocumentoClinico existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setTipo(dados.getTipo());
        existente.setDiasAfastamento(dados.getDiasAfastamento());
        existente.setCodigoCidRelacionado(dados.getCodigoCidRelacionado());
        existente.setTexto(dados.getTexto());
        return repository.save(existente);
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
