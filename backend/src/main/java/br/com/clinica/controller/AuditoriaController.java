package br.com.clinica.controller;

import br.com.clinica.dto.AuditoriaDetalheDto;
import br.com.clinica.dto.AuditoriaListagemItemDto;
import br.com.clinica.dto.AuditoriaResumoDto;
import br.com.clinica.service.AuditoriaListagemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

// Aba Auditoria (docs/specs/convenios.md). O motor que gera esses dados
// (AuditoriaEngineService) roda automaticamente na assinatura do
// prontuário — não há endpoint de escrita aqui, é só leitura do resultado.
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaListagemService listagemService;

    public AuditoriaController(AuditoriaListagemService listagemService) {
        this.listagemService = listagemService;
    }

    @GetMapping
    public Page<AuditoriaListagemItemDto> listar(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "avaliadoEm", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return listagemService.listar(status, pageable);
    }

    @GetMapping("/resumo")
    public AuditoriaResumoDto resumo() {
        return listagemService.resumo();
    }

    @GetMapping("/{id}")
    public AuditoriaDetalheDto detalhar(@PathVariable Integer id) {
        return listagemService.detalhar(id);
    }
}
