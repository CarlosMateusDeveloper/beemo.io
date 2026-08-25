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

    public ProntuarioController(
            ProntuarioListagemService listagemService, ProntuarioDetalheService detalheService,
            ProntuarioEscritaService escritaService
    ) {
        this.listagemService = listagemService;
        this.detalheService = detalheService;
        this.escritaService = escritaService;
    }

    @GetMapping("/listagem")
    public List<ProntuarioListagemItemDto> listagem() {
        return listagemService.listar();
    }

    @GetMapping("/pacientes/{idPaciente}")
    public ProntuarioPacienteDetalheDto detalharPaciente(@PathVariable Integer idPaciente) {
        return detalheService.detalharPaciente(idPaciente);
    }

    @GetMapping("/pacientes/{idPaciente}/documentos")
    public List<ProntuarioDocumentoDto> documentos(@PathVariable Integer idPaciente) {
        return detalheService.documentos(idPaciente);
    }

    @GetMapping("/{id}/detalhe")
    public ProntuarioDetalheCompletoDto detalhe(@PathVariable Integer id) {
        return detalheService.detalharProntuario(id);
    }

    @PostMapping
    public ResponseEntity<ProntuarioSalvoDto> criar(@RequestBody ProntuarioSalvarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}")
    public ProntuarioSalvoDto atualizar(@PathVariable Integer id, @RequestBody ProntuarioSalvarRequest request) {
        return escritaService.atualizar(id, request);
    }
}
