package br.com.clinica.model;


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

    @Email
    @Size(max = 100)
    private String email;

    // Hash Argon2id (BCrypt legado migrado no login) — nunca o texto puro. Ver PasswordEncoder em SecurityConfig.
    // WRITE_ONLY aceita a senha na criação, sem expô-la em respostas mesmo se algum
    // endpoint futuro devolver a entidade Usuario direto por engano.
    @NotBlank
    @Size(max = 255)
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
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
