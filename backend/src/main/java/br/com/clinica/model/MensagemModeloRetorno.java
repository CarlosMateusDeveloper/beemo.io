package br.com.clinica.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mensagem_modelo_retorno")
@Getter
@Setter
@NoArgsConstructor
public class MensagemModeloRetorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private GrupoRetorno grupo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texto;
}
