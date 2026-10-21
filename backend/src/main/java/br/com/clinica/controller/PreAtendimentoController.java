package br.com.clinica.controller;

import br.com.clinica.dto.PreAtendimentoDtos;
import br.com.clinica.service.PreAtendimentoService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PreAtendimentoController {
    private final PreAtendimentoService service;
    public PreAtendimentoController(PreAtendimentoService service) { this.service=service; }
    private static int actor(Authentication auth) { return (Integer)auth.getPrincipal(); }

    @GetMapping("/api/pre-atendimentos")
    public List<Map<String,Object>> queue(@RequestParam(required=false) String status) { return service.fila(status); }
    @GetMapping("/api/pre-atendimentos/{id}")
    public Map<String,Object> detail(@PathVariable long id) { return service.detalhe(id); }
    @PostMapping("/api/pre-atendimentos/consultas/{id}/links")
    public Map<String,Object> link(@PathVariable int id,@RequestBody(required=false) PreAtendimentoDtos.CriarLink input,Authentication auth) {
        return service.criarLink(id,input,actor(auth));
    }
    @PostMapping("/api/pre-atendimentos/{id}/revisao")
    public Map<String,Object> review(@PathVariable long id,@Valid @RequestBody PreAtendimentoDtos.Revisar input,Authentication auth) {
        return service.revisar(id,input,actor(auth));
    }
    @PostMapping("/api/pre-atendimentos/{id}/revogar")
    public ResponseEntity<Void> revoke(@PathVariable long id,Authentication auth) { service.revogar(id,actor(auth)); return ResponseEntity.noContent().build(); }
    @GetMapping("/api/pre-atendimentos/{pre}/anexos/{attachment}")
    public ResponseEntity<byte[]> download(@PathVariable long pre,@PathVariable long attachment) { return file(service.baixar(pre,attachment)); }

    @GetMapping("/api/pre-atendimento/publico")
    public Map<String,Object> publicState(@RequestHeader("X-Pre-Atendimento-Token") String token) { return service.estadoPublico(token); }
    @PostMapping("/api/pre-atendimento/publico/verificar")
    public Map<String,Object> verify(@RequestHeader("X-Pre-Atendimento-Token") String token,@Valid @RequestBody PreAtendimentoDtos.Verificar input) { return service.verificar(token,input); }
    @GetMapping("/api/pre-atendimento/publico/dados")
    public Map<String,Object> data(@RequestHeader("X-Pre-Atendimento-Token") String token,@RequestHeader("X-Pre-Atendimento-Session") String session) { return service.dadosPublicos(token,session); }
    @PutMapping("/api/pre-atendimento/publico/rascunho")
    public Map<String,Object> save(@RequestHeader("X-Pre-Atendimento-Token") String token,@RequestHeader("X-Pre-Atendimento-Session") String session,
            @Valid @RequestBody PreAtendimentoDtos.Salvar input) { return service.salvar(token,session,input); }
    @PostMapping("/api/pre-atendimento/publico/enviar")
    public Map<String,Object> submit(@RequestHeader("X-Pre-Atendimento-Token") String token,@RequestHeader("X-Pre-Atendimento-Session") String session,
            @RequestBody Map<String,Integer> input) { return service.enviar(token,session,input.getOrDefault("versao",0)); }
    @PostMapping(value="/api/pre-atendimento/publico/anexos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> attachment(@RequestHeader("X-Pre-Atendimento-Token") String token,@RequestHeader("X-Pre-Atendimento-Session") String session,
            @RequestPart("arquivo") MultipartFile file) { return service.anexar(token,session,file); }
    @GetMapping("/api/pre-atendimento/publico/anexos/{attachment}")
    public ResponseEntity<byte[]> publicDownload(@RequestHeader("X-Pre-Atendimento-Token") String token,@RequestHeader("X-Pre-Atendimento-Session") String session,
            @PathVariable long attachment) { return file(service.baixarPublico(token,session,attachment)); }

    private static ResponseEntity<byte[]> file(PreAtendimentoService.Download file) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                .filename(file.name(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(file.type())).body(file.content());
    }
}
