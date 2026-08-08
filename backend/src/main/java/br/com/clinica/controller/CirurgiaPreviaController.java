package br.com.clinica.controller;

import br.com.clinica.model.CirurgiaPrevia;
import br.com.clinica.repository.CirurgiaPreviaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/cirurgias-previas")
public class CirurgiaPreviaController {

    private final CirurgiaPreviaRepository repository;

    public CirurgiaPreviaController(CirurgiaPreviaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CirurgiaPrevia> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public CirurgiaPrevia buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<CirurgiaPrevia> criar(@Valid @RequestBody CirurgiaPrevia cirurgia) {
        CirurgiaPrevia salva = repository.save(cirurgia);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public CirurgiaPrevia atualizar(@PathVariable Integer id, @Valid @RequestBody CirurgiaPrevia dados) {
        CirurgiaPrevia existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setPaciente(dados.getPaciente());
        existente.setDescricao(dados.getDescricao());
        existente.setDataCirurgia(dados.getDataCirurgia());
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
