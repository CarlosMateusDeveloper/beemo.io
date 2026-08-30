package br.com.clinica.controller;

import br.com.clinica.dto.AlergiaDto;
import br.com.clinica.dto.AlergiaRequest;
import br.com.clinica.model.Alergia;
import br.com.clinica.repository.AlergiaRepository;
import br.com.clinica.service.AlergiaEscritaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/alergias")
public class AlergiaController {

    private final AlergiaRepository repository;
    private final AlergiaEscritaService escritaService;

    public AlergiaController(AlergiaRepository repository, AlergiaEscritaService escritaService) {
        this.repository = repository;
        this.escritaService = escritaService;
    }

    @GetMapping
    public List<Alergia> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Alergia buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<AlergiaDto> criar(@RequestBody AlergiaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public AlergiaDto atualizar(@PathVariable Integer id, @RequestBody AlergiaRequest request) {
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
