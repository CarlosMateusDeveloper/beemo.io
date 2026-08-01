package br.com.clinica.controller;

import br.com.clinica.model.Consulta;
import br.com.clinica.repository.ConsultaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private final ConsultaRepository repository;

    public ConsultaController(ConsultaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Consulta> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Consulta buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Consulta> criar(@Valid @RequestBody Consulta consulta) {
        Consulta salva = repository.save(consulta);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public Consulta atualizar(@PathVariable Integer id, @Valid @RequestBody Consulta dados) {
        Consulta existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setPaciente(dados.getPaciente());
        existente.setAgenda(dados.getAgenda());
        existente.setStatusConsulta(dados.getStatusConsulta());
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
