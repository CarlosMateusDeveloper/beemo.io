package br.com.clinica.controller;

import br.com.clinica.dto.ConvenioListagemItemDto;
import br.com.clinica.dto.ConveniosKpisResponse;
import br.com.clinica.model.Convenio;
import br.com.clinica.model.ConvenioPlano;
import br.com.clinica.model.ConvenioProcedimento;
import br.com.clinica.model.DocumentoObrigatorioConvenio;
import br.com.clinica.model.RegraAuditoria;
import br.com.clinica.repository.ConvenioRepository;
import br.com.clinica.service.ConvenioConfigService;
import br.com.clinica.service.ConvenioListagemService;
import br.com.clinica.service.ConveniosKpiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/convenios")
public class ConvenioController {

    private final ConvenioRepository repository;
    private final ConvenioListagemService listagemService;
    private final ConveniosKpiService kpiService;
    private final ConvenioConfigService configService;

    public ConvenioController(
            ConvenioRepository repository, ConvenioListagemService listagemService,
            ConveniosKpiService kpiService, ConvenioConfigService configService
    ) {
        this.repository = repository;
        this.listagemService = listagemService;
        this.kpiService = kpiService;
        this.configService = configService;
    }

    @GetMapping
    public List<Convenio> listar() {
        return repository.findAll();
    }

    // Tabela da aba Convênios: nome, status, contagem de procedimentos/regras,
    // última atualização — ver spec seção 8.
    @GetMapping("/listagem")
    public List<ConvenioListagemItemDto> listagem() {
        return listagemService.listar();
    }

    // KPIs do cabeçalho de /convenios (spec seção 4).
    @GetMapping("/kpis")
    public ConveniosKpisResponse kpis(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) Integer convenioId
    ) {
        return kpiService.calcular(periodo, convenioId);
    }

    @GetMapping("/{id}")
    public Convenio buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Convenio> criar(@Valid @RequestBody Convenio convenio) {
        convenio.setAtualizadoEm(OffsetDateTime.now());
        Convenio salvo = repository.save(convenio);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public Convenio atualizar(@PathVariable Integer id, @Valid @RequestBody Convenio dados) {
        Convenio existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setNome(dados.getNome());
        existente.setRegistroAns(dados.getRegistroAns());
        existente.setAtivo(dados.getAtivo());
        existente.setContato(dados.getContato());
        existente.setObservacoes(dados.getObservacoes());
        existente.setAtualizadoEm(OffsetDateTime.now());
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

    // --- Planos ---

    @GetMapping("/{id}/planos")
    public List<ConvenioPlano> listarPlanos(@PathVariable Integer id) {
        return configService.listarPlanos(id);
    }

    @PostMapping("/{id}/planos")
    public ResponseEntity<ConvenioPlano> criarPlano(@PathVariable Integer id, @Valid @RequestBody ConvenioPlano dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.criarPlano(id, dados));
    }

    @PutMapping("/{id}/planos/{idPlano}")
    public ConvenioPlano atualizarPlano(
            @PathVariable Integer id, @PathVariable Integer idPlano, @Valid @RequestBody ConvenioPlano dados
    ) {
        return configService.atualizarPlano(id, idPlano, dados);
    }

    // --- Procedimentos ---

    @GetMapping("/{id}/procedimentos")
    public List<ConvenioProcedimento> listarProcedimentos(@PathVariable Integer id) {
        return configService.listarProcedimentos(id);
    }

    @PostMapping("/{id}/procedimentos")
    public ResponseEntity<ConvenioProcedimento> criarProcedimento(
            @PathVariable Integer id, @Valid @RequestBody ConvenioProcedimento dados
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.criarProcedimento(id, dados));
    }

    @PutMapping("/{id}/procedimentos/{idProcedimento}")
    public ConvenioProcedimento atualizarProcedimento(
            @PathVariable Integer id, @PathVariable Integer idProcedimento, @Valid @RequestBody ConvenioProcedimento dados
    ) {
        return configService.atualizarProcedimento(id, idProcedimento, dados);
    }

    // --- Regras de auditoria ---

    @GetMapping("/{id}/regras")
    public List<RegraAuditoria> listarRegras(@PathVariable Integer id) {
        return configService.listarRegras(id);
    }

    @PostMapping("/{id}/regras")
    public ResponseEntity<RegraAuditoria> criarRegra(@PathVariable Integer id, @Valid @RequestBody RegraAuditoria dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.criarRegra(id, dados));
    }

    @PutMapping("/{id}/regras/{idRegra}")
    public RegraAuditoria atualizarRegra(
            @PathVariable Integer id, @PathVariable Integer idRegra, @Valid @RequestBody RegraAuditoria dados
    ) {
        return configService.atualizarRegra(id, idRegra, dados);
    }

    // --- Documentos obrigatórios ---

    @GetMapping("/{id}/documentos-obrigatorios")
    public List<DocumentoObrigatorioConvenio> listarDocumentos(@PathVariable Integer id) {
        return configService.listarDocumentos(id);
    }

    @PostMapping("/{id}/documentos-obrigatorios")
    public ResponseEntity<DocumentoObrigatorioConvenio> criarDocumento(
            @PathVariable Integer id, @Valid @RequestBody DocumentoObrigatorioConvenio dados
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.criarDocumento(id, dados));
    }

    @PutMapping("/{id}/documentos-obrigatorios/{idDocumento}")
    public DocumentoObrigatorioConvenio atualizarDocumento(
            @PathVariable Integer id, @PathVariable Integer idDocumento, @Valid @RequestBody DocumentoObrigatorioConvenio dados
    ) {
        return configService.atualizarDocumento(id, idDocumento, dados);
    }
}
