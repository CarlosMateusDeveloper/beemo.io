package br.com.clinica.controller;

import br.com.clinica.dto.*;
import br.com.clinica.model.Glosa;
import br.com.clinica.model.RecursoGlosa;
import br.com.clinica.service.GlosaDetalheService;
import br.com.clinica.service.GlosaEscritaService;
import br.com.clinica.service.GlosaIndicadoresService;
import br.com.clinica.service.GlosaListagemService;
import br.com.clinica.service.RecursoGlosaEscritaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// Recuperação de glosas (docs/specs/recuperacao-glosas.md). Criação de
// recurso fica aqui, aninhada (POST /api/glosas/{id}/recursos) — o resto do
// ciclo de vida do recurso (editar/anexar/enviar/resultado) vive em
// RecursoGlosaController, sobre o próprio id do recurso.
@RestController
@RequestMapping("/api/glosas")
public class GlosaController {

    private final GlosaListagemService listagemService;
    private final GlosaDetalheService detalheService;
    private final GlosaEscritaService escritaService;
    private final GlosaIndicadoresService indicadoresService;
    private final RecursoGlosaEscritaService recursoEscritaService;

    public GlosaController(
            GlosaListagemService listagemService, GlosaDetalheService detalheService,
            GlosaEscritaService escritaService, GlosaIndicadoresService indicadoresService,
            RecursoGlosaEscritaService recursoEscritaService
    ) {
        this.listagemService = listagemService;
        this.detalheService = detalheService;
        this.escritaService = escritaService;
        this.indicadoresService = indicadoresService;
        this.recursoEscritaService = recursoEscritaService;
    }

    @GetMapping
    public Page<GlosaListagemItemDto> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer idConvenio,
            @RequestParam(required = false) Integer idUsuarioResponsavel,
            @RequestParam(required = false) String recorribilidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prazoAte,
            @PageableDefault(size = 20, sort = "prazoRecurso") Pageable pageable
    ) {
        return listagemService.listar(status, idConvenio, idUsuarioResponsavel, recorribilidade, prazoAte, pageable);
    }

    @GetMapping("/indicadores")
    public GlosaIndicadoresDto indicadores() {
        return indicadoresService.calcular();
    }

    @GetMapping("/{id}")
    public GlosaDetalheDto detalhar(@PathVariable Integer id) {
        return detalheService.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<Glosa> criar(@RequestBody GlosaCriarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    @PutMapping("/{id}/classificacao")
    public Glosa classificar(@PathVariable Integer id, @RequestBody GlosaClassificarRequest request) {
        return escritaService.classificar(id, request);
    }

    @PostMapping("/{id}/aceitar")
    public Glosa aceitar(@PathVariable Integer id) {
        return escritaService.aceitar(id);
    }

    @PutMapping("/{id}/responsavel")
    public Glosa alterarResponsavel(@PathVariable Integer id, @RequestBody GlosaResponsavelRequest request) {
        return escritaService.alterarResponsavel(id, request);
    }

    @PostMapping("/{id}/recursos")
    public ResponseEntity<RecursoGlosa> criarRecurso(@PathVariable Integer id, @RequestBody RecursoGlosaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recursoEscritaService.criar(id, request));
    }
}
