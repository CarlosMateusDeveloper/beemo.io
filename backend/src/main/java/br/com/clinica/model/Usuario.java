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

    @Enumerated(EnumType.STRING)
    private PerfilUsuario perfil = PerfilUsuario.administrador;
}
