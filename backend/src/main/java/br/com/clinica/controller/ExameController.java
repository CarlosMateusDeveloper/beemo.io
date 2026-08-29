package br.com.clinica.controller;

import br.com.clinica.dto.ExameStatusRequest;
import br.com.clinica.model.Exame;
import br.com.clinica.repository.ExameRepository;
import br.com.clinica.service.ExameEscritaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Issue #13 (API de Exames): CRUD de exame (solicitação, com ou sem consulta
// vinculada) com transições de status validadas (ver ExameEscritaService) e
// listagem paginada/filtrável por paciente/status. Resultado fica em
// ResultadoExameController (nested, /api/exames/{id}/resultado).
@RestController
@RequestMapping("/api/exames")
public class ExameController {

    private final ExameRepository repository;
    private final ExameEscritaService escritaService;

    public ExameController(ExameRepository repository, ExameEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public Page<Exame> listar(
            @RequestParam(required = false) Integer idPaciente,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "solicitadoEm") Pageable pageable
    ) {
        return repository.buscar(idPaciente, status, pageable);
    }

    @GetMapping("/{id}")
    public Exame buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Exame> criar(@Valid @RequestBody Exame exame) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(exame));
    }

    @PutMapping("/{id}")
    public Exame atualizar(@PathVariable Integer id, @Valid @RequestBody Exame dados) {
        return escritaService.atualizarDados(id, dados);
    }

    @PostMapping("/{id}/status")
    public Exame atualizarStatus(@PathVariable Integer id, @RequestBody ExameStatusRequest request) {
        return escritaService.atualizarStatus(id, request);
    }
}
