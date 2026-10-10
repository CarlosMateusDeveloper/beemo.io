package br.com.clinica.controller;

import br.com.clinica.dto.MedicosPainelRequest;
import br.com.clinica.dto.MedicosPainelResponse;
import br.com.clinica.model.Medico;
import br.com.clinica.repository.MedicoRepository;
import br.com.clinica.service.MedicoPainelService;
import br.com.clinica.service.AccessControlService;
import br.com.clinica.service.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoRepository repository;
    private final MedicoPainelService painelService;
    private final AccessControlService access;

    public MedicoController(MedicoRepository repository, MedicoPainelService painelService, AccessControlService access) {
        this.repository = repository;
        this.painelService = painelService;
        this.access = access;
    }

    @GetMapping
    public List<Map<String,Object>> listar() {
        Integer doctor = access.patientDoctorId();
        return repository.findAll().stream().filter(m -> doctor == null || doctor.equals(m.getId())).map(this::view).toList();
    }

    @PostMapping("/painel")
    public MedicosPainelResponse painel(@RequestBody MedicosPainelRequest request) {
        Integer doctor = access.patientDoctorId();
        MedicosPainelResponse response = painelService.calcular(request);
        if (doctor == null) return response;
        var rows = response.medicos().stream().filter(row -> doctor.equals(row.id())).toList();
        return new MedicosPainelResponse(rows.isEmpty(), rows);
    }

    @GetMapping("/{id}")
    public Map<String,Object> buscar(@PathVariable Integer id) {
        Integer doctor = access.patientDoctorId();
        if (doctor != null && !doctor.equals(id)) throw AccessControlService.forbidden();
        return view(repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
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

    private Map<String,Object> view(Medico medico) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id", medico.getId()); result.put("nome", medico.getNome()); result.put("crm", medico.getCrm());
        result.put("status", medico.getStatus()); result.put("especialidade", medico.getEspecialidade());
        if (access.has("repasse.todos.visualizar") || (access.has("repasse.proprio.visualizar")
                && java.util.Objects.equals(TenantContext.get().idMedico(), medico.getId())))
            result.put("repassePercentual", medico.getRepassePercentual());
        return result;
    }
}
