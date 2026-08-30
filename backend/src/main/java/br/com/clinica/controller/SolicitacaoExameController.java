package br.com.clinica.controller;

import br.com.clinica.dto.SolicitacaoExameDto;
import br.com.clinica.dto.SolicitacaoExameRequest;
import br.com.clinica.model.SolicitacaoExame;
import br.com.clinica.repository.SolicitacaoExameRepository;
import br.com.clinica.service.SolicitacaoExameEscritaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/solicitacoes-exame")
public class SolicitacaoExameController {

    private final SolicitacaoExameRepository repository;
    private final SolicitacaoExameEscritaService escritaService;

    public SolicitacaoExameController(SolicitacaoExameRepository repository, SolicitacaoExameEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
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
    public ResponseEntity<SolicitacaoExameDto> criar(@RequestBody SolicitacaoExameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public SolicitacaoExameDto atualizar(@PathVariable Integer id, @RequestBody SolicitacaoExameRequest request) {
        return escritaService.atualizar(id, request);
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
