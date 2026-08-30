package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// idRecurso/idDocumentoAnexo ficam como Integer puro (não @ManyToOne) —
// mesmo motivo de Glosa/RecursoGlosa.
@Entity
@Table(name = "recurso_glosa_documento")
@Getter
@Setter
@NoArgsConstructor
public class RecursoGlosaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recurso_glosa_documento")
    private Integer id;

    @Column(name = "id_recurso", nullable = false)
    private Integer idRecurso;

    // prontuario / guia / solicitacao_medica / autorizacao / laudo / comprovante / outro
    @NotBlank
    @Column(nullable = false)
    private String tipo;

    // Preenchido quando a evidência é um arquivo real de documento_anexo;
    // nulo quando é só a existência do registro (ex.: prontuário, guia).
    @Column(name = "id_documento_anexo")
    private Integer idDocumentoAnexo;

    private String descricao;
}
