package br.com.clinica.controller;

import br.com.clinica.model.Convenio;
import br.com.clinica.repository.ConvenioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/convenios")
public class ConvenioController {

    private final ConvenioRepository repository;

    public ConvenioController(ConvenioRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Convenio> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Convenio buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Convenio> criar(@Valid @RequestBody Convenio convenio) {
        Convenio salvo = repository.save(convenio);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Convenio atualizar(@PathVariable Integer id, @Valid @RequestBody Convenio dados) {
        Convenio existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
        existente.setRegistroAns(dados.getRegistroAns());
        existente.setAtivo(dados.getAtivo());
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
