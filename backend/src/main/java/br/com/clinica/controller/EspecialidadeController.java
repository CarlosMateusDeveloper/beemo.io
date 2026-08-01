package br.com.clinica.controller;

import br.com.clinica.model.Especialidade;
import br.com.clinica.repository.EspecialidadeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/especialidades")
public class EspecialidadeController {

    private final EspecialidadeRepository repository;

    public EspecialidadeController(EspecialidadeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Especialidade> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Especialidade buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Especialidade> criar(@Valid @RequestBody Especialidade especialidade) {
        Especialidade salva = repository.save(especialidade);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public Especialidade atualizar(@PathVariable Integer id, @Valid @RequestBody Especialidade dados) {
        Especialidade existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
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
