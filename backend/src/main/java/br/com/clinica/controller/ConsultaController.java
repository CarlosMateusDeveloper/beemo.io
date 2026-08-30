package br.com.clinica.controller;

import br.com.clinica.dto.ConsultaDto;
import br.com.clinica.dto.ConsultaRequest;
import br.com.clinica.model.Consulta;
import br.com.clinica.repository.ConsultaRepository;
import br.com.clinica.service.ConsultaEscritaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private final ConsultaRepository repository;
    private final ConsultaEscritaService escritaService;

    public ConsultaController(ConsultaRepository repository, ConsultaEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public List<Consulta> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Consulta buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<ConsultaDto> criar(@RequestBody ConsultaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public ConsultaDto atualizar(@PathVariable Integer id, @RequestBody ConsultaRequest request) {
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
