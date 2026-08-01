package br.com.clinica.controller;

import br.com.clinica.model.Agenda;
import br.com.clinica.repository.AgendaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/agendas")
public class AgendaController {

    private final AgendaRepository repository;

    public AgendaController(AgendaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Agenda> listar(@RequestParam(required = false) Integer idMedico) {
        if (idMedico != null) {
            return repository.findByMedicoId(idMedico);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Agenda buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Agenda> criar(@Valid @RequestBody Agenda agenda) {
        Agenda salva = repository.save(agenda);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public Agenda atualizar(@PathVariable Integer id, @Valid @RequestBody Agenda dados) {
        Agenda existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setMedico(dados.getMedico());
        existente.setSituacao(dados.getSituacao());
        existente.setDataSlot(dados.getDataSlot());
        existente.setHoraSlot(dados.getHoraSlot());
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
