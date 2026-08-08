package br.com.clinica.controller;

import br.com.clinica.model.Alergia;
import br.com.clinica.repository.AlergiaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/alergias")
public class AlergiaController {

    private final AlergiaRepository repository;

    public AlergiaController(AlergiaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Alergia> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Alergia buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Alergia> criar(@Valid @RequestBody Alergia alergia) {
        Alergia salva = repository.save(alergia);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public Alergia atualizar(@PathVariable Integer id, @Valid @RequestBody Alergia dados) {
        Alergia existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setPaciente(dados.getPaciente());
        existente.setTipo(dados.getTipo());
        existente.setSubstancia(dados.getSubstancia());
        existente.setGravidade(dados.getGravidade());
        existente.setObservacao(dados.getObservacao());
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
