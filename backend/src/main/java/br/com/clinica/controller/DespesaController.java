package br.com.clinica.controller;

import br.com.clinica.dto.DespesaDto;
import br.com.clinica.dto.DespesaPagarRequest;
import br.com.clinica.dto.DespesaResumoDto;
import br.com.clinica.model.Despesa;
import br.com.clinica.service.DespesaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Despesas/contas a pagar (/caixa/despesas).
@RestController
@RequestMapping("/api/despesas")
public class DespesaController {

    private final DespesaService service;

    public DespesaController(DespesaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<DespesaDto> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String periodo,
            @PageableDefault(size = 20, sort = "vencimento") Pageable pageable
    ) {
        return service.listar(status, categoria, periodo, pageable);
    }

    @GetMapping("/resumo")
    public DespesaResumoDto resumo(@RequestParam(required = false) String periodo) {
        return service.resumo(periodo);
    }

    @GetMapping("/{id}")
    public DespesaDto buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<DespesaDto> criar(@Valid @RequestBody Despesa dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dados));
    }

    @PutMapping("/{id}")
    public DespesaDto atualizar(@PathVariable Integer id, @Valid @RequestBody Despesa dados) {
        return service.atualizar(id, dados);
    }

    @PostMapping("/{id}/pagar")
    public DespesaDto pagar(@PathVariable Integer id, @RequestBody(required = false) DespesaPagarRequest request) {
        return service.marcarPaga(id, request == null ? null : request.dataPagamento());
    }

    @PostMapping("/{id}/cancelar")
    public DespesaDto cancelar(@PathVariable Integer id) {
        return service.cancelar(id);
    }
}
