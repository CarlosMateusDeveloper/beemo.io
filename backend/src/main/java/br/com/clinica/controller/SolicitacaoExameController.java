package br.com.clinica.controller;

import br.com.clinica.model.SolicitacaoExame;
import br.com.clinica.repository.SolicitacaoExameRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/solicitacoes-exame")
public class SolicitacaoExameController {

    private final SolicitacaoExameRepository repository;

    public SolicitacaoExameController(SolicitacaoExameRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SolicitacaoExame> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public SolicitacaoExame buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<SolicitacaoExame> criar(@Valid @RequestBody SolicitacaoExame solicitacao) {
        SolicitacaoExame salva = repository.save(solicitacao);
        return ResponseEntity.status(HttpStatus.CREATED).body(salva);
    }

    @PutMapping("/{id}")
    public SolicitacaoExame atualizar(@PathVariable Integer id, @Valid @RequestBody SolicitacaoExame dados) {
        SolicitacaoExame existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setExame(dados.getExame());
        existente.setUrgente(dados.getUrgente());
        existente.setJustificativa(dados.getJustificativa());
        existente.setStatus(dados.getStatus());
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
