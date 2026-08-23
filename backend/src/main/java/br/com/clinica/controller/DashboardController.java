package br.com.clinica.controller;

import br.com.clinica.dto.DashboardRequest;
import br.com.clinica.dto.DashboardResponse;
import br.com.clinica.service.DashboardService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @PostMapping
    public DashboardResponse calcular(@RequestBody(required = false) DashboardRequest request) {
        DashboardRequest efetivo = request != null ? request : new DashboardRequest("Mês", null);
        return service.calcular(efetivo);
    }
}
