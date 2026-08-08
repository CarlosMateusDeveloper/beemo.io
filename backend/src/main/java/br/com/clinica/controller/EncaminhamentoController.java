package br.com.clinica.controller;

import br.com.clinica.model.Encaminhamento;
import br.com.clinica.repository.EncaminhamentoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/encaminhamentos")
public class EncaminhamentoController {

    private final EncaminhamentoRepository repository;

    public EncaminhamentoController(EncaminhamentoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Encaminhamento> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Encaminhamento buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Encaminhamento> criar(@Valid @RequestBody Encaminhamento encaminhamento) {
        Encaminhamento salvo = repository.save(encaminhamento);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Encaminhamento atualizar(@PathVariable Integer id, @Valid @RequestBody Encaminhamento dados) {
        Encaminhamento existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setEspecialidadeDestino(dados.getEspecialidadeDestino());
        existente.setMotivo(dados.getMotivo());
        existente.setPrioridade(dados.getPrioridade());
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
