package br.com.clinica.controller;

import br.com.clinica.model.DiagnosticoCiap2;
import br.com.clinica.repository.DiagnosticoCiap2Repository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/diagnosticos-ciap2")
public class DiagnosticoCiap2Controller {

    private final DiagnosticoCiap2Repository repository;

    public DiagnosticoCiap2Controller(DiagnosticoCiap2Repository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DiagnosticoCiap2> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public DiagnosticoCiap2 buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<DiagnosticoCiap2> criar(@Valid @RequestBody DiagnosticoCiap2 diagnostico) {
        DiagnosticoCiap2 salvo = repository.save(diagnostico);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public DiagnosticoCiap2 atualizar(@PathVariable Integer id, @Valid @RequestBody DiagnosticoCiap2 dados) {
        DiagnosticoCiap2 existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setCodigoCiap2(dados.getCodigoCiap2());
        existente.setDescricao(dados.getDescricao());
        existente.setPrincipal(dados.getPrincipal());
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
