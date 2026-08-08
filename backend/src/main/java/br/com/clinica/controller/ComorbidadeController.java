package br.com.clinica.controller;

import br.com.clinica.model.Comorbidade;
import br.com.clinica.repository.ComorbidadeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/comorbidades")
public class ComorbidadeController {

    private final ComorbidadeRepository repository;

    public ComorbidadeController(ComorbidadeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Comorbidade> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Comorbidade buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Comorbidade> criar(@Valid @RequestBody Comorbidade comorbidade) {
        Comorbidade salva = repository.save(comorbidade);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public Comorbidade atualizar(@PathVariable Integer id, @Valid @RequestBody Comorbidade dados) {
        Comorbidade existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setPaciente(dados.getPaciente());
        existente.setDescricao(dados.getDescricao());
        existente.setCodigoCid(dados.getCodigoCid());
        existente.setDataDiagnostico(dados.getDataDiagnostico());
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
