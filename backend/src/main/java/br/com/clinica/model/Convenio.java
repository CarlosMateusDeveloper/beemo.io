package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "convenio")
@Getter
@Setter
@NoArgsConstructor
public class Convenio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_convenio")
    private Integer id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Size(min = 6, max = 6)
    @Column(name = "registro_ans", length = 6, unique = true)
    private String registroAns;

    private Boolean ativo = true;

    @Size(max = 150)
    private String contato;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;
}
