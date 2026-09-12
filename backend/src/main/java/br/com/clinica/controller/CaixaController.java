package br.com.clinica.controller;

import br.com.clinica.dto.CaixaDreResponse;
import br.com.clinica.dto.CaixaFluxoConsolidadoResponse;
import br.com.clinica.dto.CaixaResponse;
import br.com.clinica.dto.FecharTurnoRequest;
import br.com.clinica.dto.FecharTurnoResponse;
import br.com.clinica.dto.RegistrarPagamentoRequest;
import br.com.clinica.service.CaixaDreService;
import br.com.clinica.service.CaixaFluxoConsolidadoService;
import br.com.clinica.service.CaixaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/caixa")
public class CaixaController {

    private final CaixaService service;
    private final CaixaDreService dreService;
    private final CaixaFluxoConsolidadoService fluxoConsolidadoService;

    public CaixaController(CaixaService service, CaixaDreService dreService, CaixaFluxoConsolidadoService fluxoConsolidadoService) {
        this.service = service;
        this.dreService = dreService;
        this.fluxoConsolidadoService = fluxoConsolidadoService;
    }

    @GetMapping("/turno-atual")
    public CaixaResponse turnoAtual() {
        return service.turnoAtual();
    }

    @GetMapping("/dre")
    public CaixaDreResponse dre(@RequestParam(required = false) String periodo) {
        return dreService.calcular(periodo);
    }

    @GetMapping("/fluxo-consolidado")
    public CaixaFluxoConsolidadoResponse fluxoConsolidado(@RequestParam(required = false) String periodo) {
        return fluxoConsolidadoService.calcular(periodo);
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
