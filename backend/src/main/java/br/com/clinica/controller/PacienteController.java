package br.com.clinica.controller;

import br.com.clinica.model.Paciente;
import br.com.clinica.repository.PacienteRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteRepository repository;

    public PacienteController(PacienteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Paciente> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Paciente buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Paciente> criar(@Valid @RequestBody Paciente paciente) {
        Paciente salvo = repository.save(paciente);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Paciente atualizar(@PathVariable Integer id, @Valid @RequestBody Paciente dados) {
        Paciente existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
        existente.setCpf(dados.getCpf());
        existente.setDataNascimento(dados.getDataNascimento());
        existente.setDdd(dados.getDdd());
        existente.setNumero(dados.getNumero());
        existente.setConvenio(dados.getConvenio());
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
