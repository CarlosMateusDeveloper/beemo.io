package br.com.clinica.controller;

import br.com.clinica.dto.LoteCriarRequest;
import br.com.clinica.dto.LoteDetalheDto;
import br.com.clinica.dto.LoteElegivelItemDto;
import br.com.clinica.dto.LoteListagemItemDto;
import br.com.clinica.dto.LoteStatusRequest;
import br.com.clinica.dto.LoteSugestaoDto;
import br.com.clinica.model.LoteFaturamento;
import br.com.clinica.service.LoteDetalheService;
import br.com.clinica.service.LoteEscritaService;
import br.com.clinica.service.LoteListagemService;
import br.com.clinica.service.LoteSugestaoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteSugestaoService sugestaoService;
    private final LoteEscritaService escritaService;
    private final LoteListagemService listagemService;
    private final LoteDetalheService detalheService;

    public LoteController(
            LoteSugestaoService sugestaoService, LoteEscritaService escritaService,
            LoteListagemService listagemService, LoteDetalheService detalheService
    ) {
        this.sugestaoService = sugestaoService;
        this.escritaService = escritaService;
        this.listagemService = listagemService;
        this.detalheService = detalheService;
    }

    // --- Wizard ---

    @GetMapping("/sugestoes")
    public java.util.List<LoteSugestaoDto> sugestoes() {
        return sugestaoService.sugestoes();
    }

    @GetMapping("/elegiveis")
    public java.util.List<LoteElegivelItemDto> elegiveis(@RequestParam Integer idConvenio) {
        return sugestaoService.elegiveis(idConvenio);
    }

    @PostMapping
    public ResponseEntity<LoteFaturamento> criar(@RequestBody LoteCriarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.criar(request));
    }

    // --- Listagem e detalhe ---

    @GetMapping
    public Page<LoteListagemItemDto> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer idConvenio,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return listagemService.listar(status, idConvenio, pageable);
    }

    @GetMapping("/{id}")
    public LoteDetalheDto detalhar(@PathVariable Integer id) {
        return detalheService.detalhar(id);
    }

    @PutMapping("/{id}/status")
    public LoteFaturamento atualizarStatus(@PathVariable Integer id, @RequestBody LoteStatusRequest request) {
        return escritaService.atualizarStatus(id, request.status());
    }
}
