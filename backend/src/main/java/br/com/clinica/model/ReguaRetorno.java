package br.com.clinica.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "regua_retorno")
@Getter
@Setter
@NoArgsConstructor
public class ReguaRetorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regua")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private GrupoRetorno grupo;

    @Column(name = "prazo_dias", nullable = false)
    private Short prazoDias;

    private Boolean ativa = true;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();
}
