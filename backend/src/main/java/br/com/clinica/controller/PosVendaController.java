package br.com.clinica.controller;

import br.com.clinica.dto.PosVendaAcaoRequest;
import br.com.clinica.service.PosVendaService;
import br.com.clinica.service.PosVendaIndicadoresService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pos-venda")
public class PosVendaController {
    private final PosVendaService service;
    private final PosVendaIndicadoresService indicadores;
    public PosVendaController(PosVendaService service, PosVendaIndicadoresService indicadores) {
        this.service = service; this.indicadores = indicadores;
    }

    @GetMapping
    public Map<String,Object> listar(@RequestParam(defaultValue="abertos") String status,
            @RequestParam(defaultValue="") String busca, @RequestParam(required=false) Integer responsavel,
            @RequestParam(defaultValue="0") int pagina, @RequestParam(defaultValue="todos") String fila) {
        return service.listar(status,busca,responsavel,pagina,fila);
    }

    @GetMapping("/indicadores")
    public Map<String,Object> indicadores(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim) {
        return indicadores.calcular(inicio,fim);
    }

    @GetMapping("/indicadores/casos")
    public Map<String,Object> casosIndicador(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam String metrica,
            @RequestParam(defaultValue="0") int pagina,
            @RequestParam(defaultValue="25") int tamanho) {
        return indicadores.casos(inicio,fim,metrica,pagina,tamanho);
    }

    @GetMapping(value="/indicadores/exportar", produces="text/csv;charset=UTF-8")
    public ResponseEntity<String> exportarIndicador(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam String metrica) {
        String csv=indicadores.exportar(inicio,fim,metrica);
        String nome=metrica.replaceAll("[^a-z0-9_-]", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=pos-venda-"+nome+".csv")
                .contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv);
    }

    @GetMapping("/responsaveis")
    public List<Map<String,Object>> responsaveis() { return service.responsaveis(); }

    @GetMapping("/{id}")
    public Map<String,Object> detalhe(@PathVariable long id) { return service.detalhe(id); }

    @PostMapping("/{id}/acoes")
    public ResponseEntity<Void> agir(@PathVariable long id, @Valid @RequestBody PosVendaAcaoRequest request,
            Authentication auth) {
        Integer autor = auth != null && auth.getPrincipal() instanceof Integer i ? i : null;
        service.agir(id,request,autor);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> erro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "Não foi possível concluir a ação" : exception.getReason()));
    }
}
