package br.com.clinica.controller;

import br.com.clinica.dto.CaixaResponse;
import br.com.clinica.dto.FecharTurnoRequest;
import br.com.clinica.dto.FecharTurnoResponse;
import br.com.clinica.dto.RegistrarPagamentoRequest;
import br.com.clinica.service.CaixaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/caixa")
public class CaixaController {

    private final CaixaService service;

    public CaixaController(CaixaService service) {
        this.service = service;
    }

    @GetMapping("/turno-atual")
    public CaixaResponse turnoAtual() {
        return service.turnoAtual();
    }

    @PostMapping("/pagamentos")
    public ResponseEntity<Void> registrarPagamento(@RequestBody RegistrarPagamentoRequest request) {
        service.registrarPagamento(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/turno/fechar")
    public FecharTurnoResponse fecharTurno(@RequestBody FecharTurnoRequest request) {
        return service.fecharTurno(request);
    }
}
