package br.com.clinica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 100)
    @Column(unique = true)
    private String email;

    // Hash BCrypt — nunca o texto puro. Ver PasswordEncoder em SecurityConfig.
    // @JsonIgnore garante que isso nunca vaza numa resposta, mesmo se algum
    // endpoint futuro devolver a entidade Usuario direto por engano.
    @NotBlank
    @Size(max = 255)
    @JsonIgnore
    private String senha;

    // insertable/updatable = false: perfil é enum nativo do Postgres
    // (perfil_usuario); repository.save() bindaria o valor como varchar e
    // quebraria ("operator does not exist") sem stringtype=unspecified na
    // URL JDBC. Escrita fica em UsuarioEscritaService via SQL nativo com
    // CAST; sem valor informado, a coluna usa o DEFAULT do banco ('administrador').
    @Enumerated(EnumType.STRING)
    @Column(insertable = false, updatable = false)
    private PerfilUsuario perfil;
}
