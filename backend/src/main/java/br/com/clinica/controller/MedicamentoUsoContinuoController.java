package br.com.clinica.controller;

import br.com.clinica.model.MedicamentoUsoContinuo;
import br.com.clinica.repository.MedicamentoUsoContinuoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/medicamentos-uso-continuo")
public class MedicamentoUsoContinuoController {

    private final MedicamentoUsoContinuoRepository repository;

    public MedicamentoUsoContinuoController(MedicamentoUsoContinuoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<MedicamentoUsoContinuo> listar(@RequestParam(required = false) Integer idPaciente) {
        if (idPaciente != null) {
            return repository.findByPacienteId(idPaciente);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public MedicamentoUsoContinuo buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<MedicamentoUsoContinuo> criar(@Valid @RequestBody MedicamentoUsoContinuo medicamento) {
        MedicamentoUsoContinuo salvo = repository.save(medicamento);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public MedicamentoUsoContinuo atualizar(@PathVariable Integer id, @Valid @RequestBody MedicamentoUsoContinuo dados) {
        MedicamentoUsoContinuo existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setPaciente(dados.getPaciente());
        existente.setMedicamento(dados.getMedicamento());
        existente.setDosagem(dados.getDosagem());
        existente.setPosologia(dados.getPosologia());
        existente.setDataInicio(dados.getDataInicio());
        existente.setAtivo(dados.getAtivo());
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
