package br.com.clinica.controller;

import br.com.clinica.dto.EspecialidadePacoteDtos.AlterarStatusRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.AplicarRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.CriarVersaoRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.PersonalizarRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.PublicarRequest;
import br.com.clinica.service.EspecialidadePacoteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/configuracoes/especialidades")
public class EspecialidadePacoteController {
    private final EspecialidadePacoteService service;
    public EspecialidadePacoteController(EspecialidadePacoteService service) { this.service = service; }

    @GetMapping("/pacotes")
    public List<Map<String,Object>> catalogo() { return service.catalogo(); }

    @GetMapping("/revisores")
    public List<Map<String,Object>> revisores() { return service.revisores(); }

    @PostMapping("/pacotes/{id}/versoes")
    public Map<String,Object> criarVersao(@PathVariable long id, @Valid @RequestBody CriarVersaoRequest request,
            Authentication authentication) { return service.criarVersao(id,request,author(authentication)); }

    @PutMapping("/versoes/{id}")
    public Map<String,Object> atualizarVersao(@PathVariable long id, @Valid @RequestBody CriarVersaoRequest request,
            Authentication authentication) { return service.atualizarVersao(id,request,author(authentication)); }

    @PostMapping("/versoes/{id}/revisao")
    public Map<String,Object> enviarRevisao(@PathVariable long id, Authentication authentication) {
        return service.enviarRevisao(id,author(authentication));
    }

    @PostMapping("/versoes/{id}/publicar")
    public Map<String,Object> publicar(@PathVariable long id, @Valid @RequestBody PublicarRequest request,
            Authentication authentication) { return service.publicar(id,request,author(authentication)); }

    @GetMapping("/versoes/{id}/preview")
    public Map<String,Object> preview(@PathVariable long id,
            @RequestParam(name="componente") List<String> componentes) {
        return service.preview(id,componentes);
    }

    @PostMapping("/versoes/{id}/aplicar")
    public Map<String,Object> aplicar(@PathVariable long id, @Valid @RequestBody AplicarRequest request,
            Authentication authentication) { return service.aplicar(id,request,author(authentication)); }

    @PutMapping("/aplicacoes/{id}/componentes/{chave}")
    public Map<String,Object> personalizar(@PathVariable long id, @PathVariable String chave,
            @Valid @RequestBody PersonalizarRequest request, Authentication authentication) {
        return service.personalizar(id,chave,request,author(authentication));
    }

    @PostMapping("/aplicacoes/{id}/desativar")
    public Map<String,Object> desativar(@PathVariable long id, @Valid @RequestBody AlterarStatusRequest request,
            Authentication authentication) { return service.desativar(id,request,author(authentication)); }

    @PostMapping("/aplicacoes/{id}/reverter")
    public Map<String,Object> reverter(@PathVariable long id, @Valid @RequestBody AlterarStatusRequest request,
            Authentication authentication) { return service.reverter(id,request,author(authentication)); }

    private static int author(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer id))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Faça login para configurar a clínica");
        return id;
    }
}
