package br.com.clinica.controller;

import br.com.clinica.model.DiagnosticoCid;
import br.com.clinica.repository.DiagnosticoCidRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/diagnosticos-cid")
public class DiagnosticoCidController {

    private final DiagnosticoCidRepository repository;

    public DiagnosticoCidController(DiagnosticoCidRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DiagnosticoCid> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public DiagnosticoCid buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<DiagnosticoCid> criar(@Valid @RequestBody DiagnosticoCid diagnostico) {
        DiagnosticoCid salvo = repository.save(diagnostico);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public DiagnosticoCid atualizar(@PathVariable Integer id, @Valid @RequestBody DiagnosticoCid dados) {
        DiagnosticoCid existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setCodigoCid(dados.getCodigoCid());
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
