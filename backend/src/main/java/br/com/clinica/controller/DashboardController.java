package br.com.clinica.controller;

import br.com.clinica.dto.DashboardRequest;
import br.com.clinica.dto.DashboardResponse;
import br.com.clinica.service.DashboardService;
import br.com.clinica.service.AccessControlService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/dashboard", "/api/v1/dashboard"})
public class DashboardController {

    private final DashboardService service;
    private final AccessControlService access;

    public DashboardController(DashboardService service, AccessControlService access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping
    public DashboardResponse calcular(@RequestBody(required = false) DashboardRequest request) {
        DashboardRequest efetivo = request != null ? request : new DashboardRequest("Mês", null, null, null);
        Integer doctor = access.forceDoctor(efetivo.profissionalId());
        DashboardResponse response = service.calcular(new DashboardRequest(
                efetivo.periodo(), doctor, efetivo.dataInicio(), efetivo.dataFim()));
        if (response == null) return null; // permite adapters/test doubles sem payload
        boolean operational = access.has("dashboard.operacional.visualizar");
        boolean financial = access.has("dashboard.financeiro.visualizar");
        if (operational && financial) return response;
        if (financial) return new DashboardResponse(response.empty(), response.totalConsultas(), response.faturamento(),
                null, null, null, response.ranking(), response.pagador(), response.serieTemporal(), response.serieUnidade(),
                new DashboardResponse.HojeDto(response.hoje().consultas(), response.hoje().filaAguardando(), List.of()));
        return new DashboardResponse(response.empty(), response.totalConsultas(), null, response.ocupacao(), response.noShow(),
                response.novosRetornos(), List.of(), null,
                response.serieTemporal().stream().map(item -> new DashboardResponse.SerieItemDto(
                        item.label(), null, item.atendimentos(), item.cancelamentos(), item.faltas())).toList(),
                response.serieUnidade(), response.hoje());
    }
}
