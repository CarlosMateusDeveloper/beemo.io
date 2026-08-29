package br.com.clinica.controller;

import br.com.clinica.model.Fatura;
import br.com.clinica.repository.FaturaRepository;
import br.com.clinica.service.FaturaEscritaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

// Issue #14 (API de Financeiro): CRUD de fatura gerada a partir de uma
// consulta, com listagem paginada e filtrável por status/período. Registro
// de pagamento fica em PagamentoController (nested, /api/faturas/{id}/pagamentos).
@RestController
@RequestMapping("/api/faturas")
public class FaturaController {

    private final FaturaRepository repository;
    private final FaturaEscritaService escritaService;

    public FaturaController(FaturaRepository repository, FaturaEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public Page<Fatura> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "vencimento") Pageable pageable
    ) {
        return repository.buscar(status, inicio, fim, pageable);
    }

    @GetMapping("/{id}")
    public Fatura buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Fatura> criar(@Valid @RequestBody Fatura fatura) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(fatura));
    }

    @PutMapping("/{id}")
    public Fatura atualizar(@PathVariable Integer id, @Valid @RequestBody Fatura dados) {
        return escritaService.atualizar(id, dados);
    }
}
