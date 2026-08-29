package br.com.clinica.controller;

import br.com.clinica.model.ResultadoExame;
import br.com.clinica.service.ResultadoExameEscritaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Issue #13: registro de resultado (URL do arquivo, observações) vinculado a
// um exame — 1:1 (autorizacao_convenio/fatura seguem o mesmo padrão de
// sub-recurso nested).
@RestController
@RequestMapping("/api/exames/{idExame}/resultado")
public class ResultadoExameController {

    private final ResultadoExameEscritaService escritaService;

    public ResultadoExameController(ResultadoExameEscritaService escritaService) {
        this.escritaService = escritaService;
    }

    @GetMapping
    public ResultadoExame buscar(@PathVariable Integer idExame) {
        return escritaService.buscar(idExame);
    }

    @PostMapping
    public ResponseEntity<ResultadoExame> registrar(@PathVariable Integer idExame, @Valid @RequestBody ResultadoExame dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escritaService.registrar(idExame, dados));
    }
}
