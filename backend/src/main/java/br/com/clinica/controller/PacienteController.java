package br.com.clinica.controller;

import br.com.clinica.dto.PacienteFilaItemDto;
import br.com.clinica.dto.PacienteListagemItemDto;
import br.com.clinica.dto.PacientesKpisResponse;
import br.com.clinica.model.Paciente;
import br.com.clinica.repository.PacienteRepository;
import br.com.clinica.service.PacienteFilaService;
import br.com.clinica.service.PacienteKpiService;
import br.com.clinica.service.PacienteListagemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteRepository repository;
    private final PacienteKpiService kpiService;
    private final PacienteListagemService listagemService;
    private final PacienteFilaService filaService;

    public PacienteController(
            PacienteRepository repository, PacienteKpiService kpiService,
            PacienteListagemService listagemService, PacienteFilaService filaService
    ) {
        this.repository = repository;
        this.kpiService = kpiService;
        this.listagemService = listagemService;
        this.filaService = filaService;
    }

    @GetMapping
    public List<Paciente> listar() {
        return repository.findAll();
    }

    @GetMapping("/kpis")
    public PacientesKpisResponse kpis() {
        return kpiService.calcular();
    }

    @GetMapping("/listagem")
    public List<PacienteListagemItemDto> listagem() {
        return listagemService.listar();
    }

    @GetMapping("/fila")
    public List<PacienteFilaItemDto> fila() {
        return filaService.listarHoje();
    }

    @GetMapping("/{id}")
    public Paciente buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Paciente> criar(@Valid @RequestBody Paciente paciente) {
        Paciente salvo = repository.save(paciente);
        // Re-busca via findById (JOIN FETCH convenio) em vez de devolver o
        // retorno cru do save(): quando o convenio chega so com {id}, o
        // Hibernate reassocia como proxy lazy no ciclo de merge, e devolver
        // esse objeto quebraria a serializacao (open-in-view: false).
        Paciente recarregado = repository.findById(salvo.getId())
                .orElseThrow(() -> new IllegalStateException("Paciente recem-criado não encontrado"));
        return ResponseEntity.status(HttpStatus.CREATED).body(recarregado);
    }

    @PutMapping("/{id}")
    public Paciente atualizar(@PathVariable Integer id, @Valid @RequestBody Paciente dados) {
        Paciente existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
        existente.setCpf(dados.getCpf());
        existente.setDataNascimento(dados.getDataNascimento());
        existente.setDdd(dados.getDdd());
        existente.setNumero(dados.getNumero());
        existente.setConvenio(dados.getConvenio());
        existente.setHistoriaFamiliar(dados.getHistoriaFamiliar());
        existente.setHistoriaSocial(dados.getHistoriaSocial());
        existente.setEmail(dados.getEmail());
        existente.setCep(dados.getCep());
        existente.setLogradouro(dados.getLogradouro());
        existente.setNumeroEndereco(dados.getNumeroEndereco());
        existente.setComplemento(dados.getComplemento());
        existente.setBairro(dados.getBairro());
        existente.setCidade(dados.getCidade());
        existente.setUf(dados.getUf());
        repository.save(existente);
        // Mesmo motivo do POST: re-busca com JOIN FETCH em vez de devolver o
        // retorno cru do save().
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Paciente não encontrado após salvar"));
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
