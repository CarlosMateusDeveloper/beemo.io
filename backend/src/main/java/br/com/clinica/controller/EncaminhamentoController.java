package br.com.clinica.controller;

import br.com.clinica.dto.EncaminhamentoDto;
import br.com.clinica.dto.EncaminhamentoRequest;
import br.com.clinica.model.Encaminhamento;
import br.com.clinica.repository.EncaminhamentoRepository;
import br.com.clinica.service.EncaminhamentoEscritaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/encaminhamentos")
public class EncaminhamentoController {

    private final EncaminhamentoRepository repository;
    private final EncaminhamentoEscritaService escritaService;

    public EncaminhamentoController(EncaminhamentoRepository repository, EncaminhamentoEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
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
    public ResponseEntity<EncaminhamentoDto> criar(@RequestBody EncaminhamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public EncaminhamentoDto atualizar(@PathVariable Integer id, @RequestBody EncaminhamentoRequest request) {
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
