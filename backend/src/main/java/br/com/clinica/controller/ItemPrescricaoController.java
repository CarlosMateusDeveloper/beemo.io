package br.com.clinica.controller;

import br.com.clinica.model.ItemPrescricao;
import br.com.clinica.repository.ItemPrescricaoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/itens-prescricao")
public class ItemPrescricaoController {

    private final ItemPrescricaoRepository repository;

    public ItemPrescricaoController(ItemPrescricaoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ItemPrescricao> listar(@RequestParam(required = false) Integer idProntuario) {
        if (idProntuario != null) {
            return repository.findByProntuarioId(idProntuario);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ItemPrescricao buscar(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<ItemPrescricao> criar(@Valid @RequestBody ItemPrescricao item) {
        ItemPrescricao salvo = repository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public ItemPrescricao atualizar(@PathVariable Integer id, @Valid @RequestBody ItemPrescricao dados) {
        ItemPrescricao existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setProntuario(dados.getProntuario());
        existente.setMedicamento(dados.getMedicamento());
        existente.setPrincipioAtivo(dados.getPrincipioAtivo());
        existente.setDosagem(dados.getDosagem());
        existente.setViaAdministracao(dados.getViaAdministracao());
        existente.setPosologia(dados.getPosologia());
        existente.setDuracaoTratamento(dados.getDuracaoTratamento());
        existente.setQuantidade(dados.getQuantidade());
        return repository.save(existente);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Integer id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
