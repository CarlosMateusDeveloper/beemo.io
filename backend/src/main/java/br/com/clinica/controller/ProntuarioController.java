package br.com.clinica.controller;

import br.com.clinica.dto.ProntuarioAtendimentoDto;
import br.com.clinica.dto.ProntuarioDetalheCompletoDto;
import br.com.clinica.dto.ProntuarioDocumentoDto;
import br.com.clinica.dto.ProntuarioListagemItemDto;
import br.com.clinica.dto.ProntuarioPacienteDetalheDto;
import br.com.clinica.dto.ProntuarioSalvarRequest;
import br.com.clinica.dto.ProntuarioSalvoDto;
import br.com.clinica.service.ProntuarioDetalheService;
import br.com.clinica.service.ProntuarioEscritaService;
import br.com.clinica.service.ProntuarioListagemService;
import br.com.clinica.service.ClinicalScopeService;
import br.com.clinica.service.AccessControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prontuarios")
public class ProntuarioController {

    private final ProntuarioListagemService listagemService;
    private final ProntuarioDetalheService detalheService;
    private final ProntuarioEscritaService escritaService;
    private final ClinicalScopeService scope;
    private final AccessControlService access;

    public ProntuarioController(
            ProntuarioListagemService listagemService, ProntuarioDetalheService detalheService,
            ProntuarioEscritaService escritaService, ClinicalScopeService scope, AccessControlService access
    ) {
        this.listagemService = listagemService;
        this.detalheService = detalheService;
        this.escritaService = escritaService;
        this.scope = scope;
        this.access = access;
    }

    @GetMapping("/listagem")
    public List<ProntuarioListagemItemDto> listagem() {
        return listagemService.listar();
    }

    @GetMapping("/pacientes/{idPaciente}")
    public ProntuarioPacienteDetalheDto detalharPaciente(@PathVariable Integer idPaciente) {
        scope.patient(idPaciente);
        return detalheService.detalharPaciente(idPaciente);
    }

    @GetMapping("/pacientes/{idPaciente}/documentos")
    public List<ProntuarioDocumentoDto> documentos(@PathVariable Integer idPaciente) {
        scope.patient(idPaciente);
        return detalheService.documentos(idPaciente);
    }

    @GetMapping("/{id}/detalhe")
    public ProntuarioDetalheCompletoDto detalhe(@PathVariable Integer id) {
        scope.chart(id);
        return detalheService.detalharProntuario(id);
    }

    @PostMapping
    public ResponseEntity<ProntuarioSalvoDto> criar(@RequestBody ProntuarioSalvarRequest request) {
        scope.consultation(request.consultaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(withResponsibleDoctor(request)));
    }

    @PutMapping("/{id}")
    public ProntuarioSalvoDto atualizar(@PathVariable Integer id, @RequestBody ProntuarioSalvarRequest request) {
        scope.chart(id);
        return escritaService.atualizar(id, withResponsibleDoctor(request));
    }

    private ProntuarioSalvarRequest withResponsibleDoctor(ProntuarioSalvarRequest request) {
        Integer doctor = access.forceDoctor(request.medicoResponsavelId());
        return new ProntuarioSalvarRequest(request.consultaId(), doctor, request.queixaPrincipal(),
                request.historiaDoencaAtual(), request.descricao(), request.exameFisico(),
                request.hipoteseDiagnostica(), request.diagnostico(), request.tipoDiagnostico(),
                request.prescricao(), request.planoTerapeutico(), request.conduta(),
                request.retornoSugeridoDias(), request.finalizar());
    }
}
