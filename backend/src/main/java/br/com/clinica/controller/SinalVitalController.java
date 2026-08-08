package br.com.clinica.controller;

import br.com.clinica.model.SinalVital;
import br.com.clinica.repository.SinalVitalRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/sinais-vitais")
public class SinalVitalController {

    private final SinalVitalRepository repository;

    public SinalVitalController(SinalVitalRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SinalVital> listar(@RequestParam(required = false) Integer idConsulta) {
        if (idConsulta != null) {
            return repository.findByConsultaId(idConsulta);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public SinalVital buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<SinalVital> criar(@Valid @RequestBody SinalVital sinalVital) {
        SinalVital salvo = repository.save(sinalVital);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public SinalVital atualizar(@PathVariable Integer id, @Valid @RequestBody SinalVital dados) {
        SinalVital existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setConsulta(dados.getConsulta());
        existente.setPressaoSistolica(dados.getPressaoSistolica());
        existente.setPressaoDiastolica(dados.getPressaoDiastolica());
        existente.setFrequenciaCardiaca(dados.getFrequenciaCardiaca());
        existente.setFrequenciaRespiratoria(dados.getFrequenciaRespiratoria());
        existente.setTemperatura(dados.getTemperatura());
        existente.setSaturacaoOxigenio(dados.getSaturacaoOxigenio());
        existente.setPeso(dados.getPeso());
        existente.setAltura(dados.getAltura());
        existente.setEscalaDor(dados.getEscalaDor());
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
