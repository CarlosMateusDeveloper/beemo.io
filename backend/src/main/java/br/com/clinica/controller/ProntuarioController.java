package br.com.clinica.controller;

import br.com.clinica.model.Prontuario;
import br.com.clinica.repository.ProntuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/prontuarios")
public class ProntuarioController {

    private final ProntuarioRepository repository;

    public ProntuarioController(ProntuarioRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Prontuario> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Prontuario buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Prontuario> criar(@Valid @RequestBody Prontuario prontuario) {
        Prontuario salvo = repository.save(prontuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Prontuario atualizar(@PathVariable Integer id, @Valid @RequestBody Prontuario dados) {
        Prontuario existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setQueixaPrincipal(dados.getQueixaPrincipal());
        existente.setHistoriaDoencaAtual(dados.getHistoriaDoencaAtual());
        existente.setDescricao(dados.getDescricao());
        existente.setExameFisico(dados.getExameFisico());
        existente.setHipoteseDiagnostica(dados.getHipoteseDiagnostica());
        existente.setDiagnostico(dados.getDiagnostico());
        existente.setTipoDiagnostico(dados.getTipoDiagnostico());
        existente.setPrescricao(dados.getPrescricao());
        existente.setPlanoTerapeutico(dados.getPlanoTerapeutico());
        existente.setConduta(dados.getConduta());
        existente.setMedicoResponsavel(dados.getMedicoResponsavel());
        existente.setAssinadoEm(dados.getAssinadoEm());
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
