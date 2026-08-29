package br.com.clinica.controller;

import br.com.clinica.model.Pagamento;
import br.com.clinica.service.PagamentoEscritaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Issue #14: registro de pagamento vinculado a uma fatura. Nested sob
// fatura (RESTful) — distinto de POST /api/caixa/pagamentos (CaixaService),
// que é o fluxo específico da tela /caixa (amarrado a um turno de caixa).
@RestController
@RequestMapping("/api/faturas/{idFatura}/pagamentos")
public class PagamentoController {

    private final PagamentoEscritaService escritaService;

    public PagamentoController(PagamentoEscritaService escritaService) {
        this.escritaService = escritaService;
    }

    @GetMapping
    public List<Pagamento> listar(@PathVariable Integer idFatura) {
        return escritaService.listar(idFatura);
    }

    @PostMapping
    public ResponseEntity<Pagamento> registrar(@PathVariable Integer idFatura, @Valid @RequestBody Pagamento dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.registrar(idFatura, dados));
    }
}
