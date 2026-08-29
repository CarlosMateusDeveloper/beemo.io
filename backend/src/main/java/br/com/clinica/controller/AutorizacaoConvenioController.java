package br.com.clinica.controller;

import br.com.clinica.model.AutorizacaoConvenio;
import br.com.clinica.repository.AutorizacaoConvenioRepository;
import br.com.clinica.service.AutorizacaoConvenioEscritaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Issue #15 (API de Convênios): CRUD de autorizacao_convenio vinculada a uma
// consulta (número da guia, status pendente/autorizado/negado/expirado).
// CRUD de convênio em si já existia (ConvenioController).
@RestController
@RequestMapping("/api/autorizacoes-convenio")
public class AutorizacaoConvenioController {

    private final AutorizacaoConvenioRepository repository;
    private final AutorizacaoConvenioEscritaService escritaService;

    public AutorizacaoConvenioController(
            AutorizacaoConvenioRepository repository, AutorizacaoConvenioEscritaService escritaService
    ) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public Page<AutorizacaoConvenio> listar(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "solicitadoEm") Pageable pageable
    ) {
        return repository.buscar(status, pageable);
    }

    @GetMapping("/{id}")
    public AutorizacaoConvenio buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<AutorizacaoConvenio> criar(@Valid @RequestBody AutorizacaoConvenio dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(dados));
    }

    @PutMapping("/{id}")
    public AutorizacaoConvenio atualizar(@PathVariable Integer id, @Valid @RequestBody AutorizacaoConvenio dados) {
        return escritaService.atualizar(id, dados);
    }
}
