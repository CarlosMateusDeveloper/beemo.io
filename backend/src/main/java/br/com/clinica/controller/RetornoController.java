package br.com.clinica.controller;

import br.com.clinica.dto.*;
import br.com.clinica.model.GrupoRetorno;
import br.com.clinica.service.MensagemModeloRetornoService;
import br.com.clinica.service.ReguaRetornoService;
import br.com.clinica.service.RetornoAcaoService;
import br.com.clinica.service.RetornoDeteccaoService;
import br.com.clinica.service.RetornoResultadosService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/retorno")
public class RetornoController {

    private final RetornoDeteccaoService deteccaoService;
    private final RetornoAcaoService acaoService;
    private final ReguaRetornoService reguaService;
    private final MensagemModeloRetornoService modeloService;
    private final RetornoResultadosService resultadosService;

    public RetornoController(
            RetornoDeteccaoService deteccaoService, RetornoAcaoService acaoService,
            ReguaRetornoService reguaService, MensagemModeloRetornoService modeloService,
            RetornoResultadosService resultadosService
    ) {
        this.deteccaoService = deteccaoService;
        this.acaoService = acaoService;
        this.reguaService = reguaService;
        this.modeloService = modeloService;
        this.resultadosService = resultadosService;
    }

    @GetMapping("/resumo")
    public RetornoResumoDto resumo() {
        return deteccaoService.resumo();
    }

    @GetMapping("/grupos/{grupo}")
    public List<RetornoPacienteItemDto> grupo(@PathVariable String grupo) {
        return deteccaoService.listarGrupo(converterGrupo(grupo));
    }

    @PostMapping("/pacientes/adiar")
    public void adiar(@RequestBody RetornoIdsRequest request) {
        acaoService.adiarTrintaDias(request.idsPaciente());
    }

    @PostMapping("/pacientes/nao-contatar")
    public void naoContatar(@RequestBody RetornoNaoContatarRequest request) {
        acaoService.marcarNaoContatar(request.idsPaciente(), request.motivo());
    }

    @PostMapping("/pacientes/enviar-mensagem")
    public RetornoEnviarMensagemResponse enviarMensagem(@RequestBody RetornoEnviarMensagemRequest request) {
        return acaoService.enviarMensagem(
                request.idsPaciente(), converterGrupo(request.grupo()), request.texto(), request.idUsuario()
        );
    }

    @GetMapping("/reguas")
    public List<ReguaRetornoDto> reguas() {
        return reguaService.listar();
    }

    @PutMapping("/reguas/{id}")
    public ReguaRetornoDto atualizarRegua(@PathVariable Integer id, @RequestBody ReguaRetornoRequest request) {
        return reguaService.atualizar(id, request);
    }

    @GetMapping("/modelos")
    public List<ModeloMensagemDto> modelos() {
        return modeloService.listar();
    }

    @PutMapping("/modelos/{grupo}")
    public ModeloMensagemDto atualizarModelo(@PathVariable String grupo, @RequestBody ModeloMensagemRequest request) {
        return modeloService.atualizar(grupo, request);
    }

    @GetMapping("/resultados")
    public RetornoResultadosDto resultados(
            @RequestParam(required = false) String inicio, @RequestParam(required = false) String fim
    ) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataInicio = inicio != null ? LocalDate.parse(inicio) : hoje.withDayOfMonth(1);
        LocalDate dataFim = fim != null ? LocalDate.parse(fim) : hoje;
        return resultadosService.calcular(dataInicio, dataFim);
    }

    private GrupoRetorno converterGrupo(String grupo) {
        try {
            return GrupoRetorno.valueOf(grupo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo inválido: " + grupo);
        }
    }
}
