package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lote_item")
@Getter
@Setter
@NoArgsConstructor
public class LoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote_item")
    private Integer id;

    @NotNull
    @Column(name = "id_lote", nullable = false)
    private Integer idLote;

    @NotNull
    @Column(name = "id_fatura", nullable = false, unique = true)
    private Integer idFatura;
}
