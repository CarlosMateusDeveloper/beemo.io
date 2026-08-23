package br.com.clinica.controller;

import br.com.clinica.dto.MedicosPainelRequest;
import br.com.clinica.dto.MedicosPainelResponse;
import br.com.clinica.model.Medico;
import br.com.clinica.repository.MedicoRepository;
import br.com.clinica.service.MedicoPainelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoRepository repository;
    private final MedicoPainelService painelService;

    public MedicoController(MedicoRepository repository, MedicoPainelService painelService) {
        this.repository = repository;
        this.painelService = painelService;
    }

    @GetMapping
    public List<Medico> listar() {
        return repository.findAll();
    }

    @PostMapping("/painel")
    public MedicosPainelResponse painel(@RequestBody MedicosPainelRequest request) {
        return painelService.calcular(request);
    }

    @GetMapping("/{id}")
    public Medico buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Medico> criar(@Valid @RequestBody Medico medico) {
        Medico salvo = repository.save(medico);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Medico atualizar(@PathVariable Integer id, @Valid @RequestBody Medico dados) {
        Medico existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
        existente.setCrm(dados.getCrm());
        existente.setStatus(dados.getStatus());
        existente.setRepassePercentual(dados.getRepassePercentual());
        existente.setEspecialidade(dados.getEspecialidade());
        return repository.save(existente);
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
