package br.com.clinica.controller;

import br.com.clinica.dto.PacienteFilaItemDto;
import br.com.clinica.dto.PacienteListagemItemDto;
import br.com.clinica.dto.PacienteResumoDto;
import br.com.clinica.dto.PacientesKpisResponse;
import br.com.clinica.model.Paciente;
import br.com.clinica.repository.PacienteRepository;
import br.com.clinica.service.PacienteBuscaService;
import br.com.clinica.service.PacienteFilaService;
import br.com.clinica.service.PacienteKpiService;
import br.com.clinica.service.PacienteListagemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final PacienteBuscaService buscaService;

    public PacienteController(
            PacienteRepository repository, PacienteKpiService kpiService,
            PacienteListagemService listagemService, PacienteFilaService filaService,
            PacienteBuscaService buscaService
    ) {
        this.repository = repository;
        this.kpiService = kpiService;
        this.listagemService = listagemService;
        this.filaService = filaService;
        this.buscaService = buscaService;
    }

    // Issue #11: "listar (paginado, com busca por nome/CPF/telefone)" — cpf
    // mascarado na resposta (ver PacienteBuscaService). GET /{id} continua
    // devolvendo o cadastro completo, necessário pra abrir/editar a ficha.
    @GetMapping
    public Page<PacienteResumoDto> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable
    ) {
        return buscaService.buscar(busca, pageable);
    }

    @GetMapping("/kpis")
    public PacientesKpisResponse kpis() {
        return kpiService.calcular();
    }

    @GetMapping("/listagem")
    public List<PacienteListagemItemDto> listagem() {
        return listagemService.listar();
    }

    // Issue #4: tabela paginada da tela /pacientes — busca/filtro/ordenação
    // e paginação de verdade (ver PacienteListagemService.paginar), CPF
    // mascarado no DTO. Não reaproveita GET /api/pacientes (issue #11)
    // porque aquele endpoint não tem status/idade/última-próxima consulta
    // que a tela precisa — ver comentário na service.
    @GetMapping("/tabela")
    public Page<PacienteListagemItemDto> tabela(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String filtroKpi,
            @RequestParam(required = false) List<String> convenio,
            @RequestParam(defaultValue = "ultima") String ordem,
            @RequestParam(defaultValue = "desc") String direcao,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return listagemService.paginar(busca, status, filtroKpi, convenio, ordem, direcao, pageable);
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
