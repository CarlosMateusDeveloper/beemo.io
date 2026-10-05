package br.com.clinica.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="auth_magic_token", indexes=@Index(name="idx_magic_expira", columnList="expira_em"))
public class MagicLoginToken {
    @Id @Column(length=64) private String hash;
    @Column(name="id_usuario") private Integer idUsuario;
    @Column(nullable=false, length=100) private String email;
    @Column(name="expira_em", nullable=false) private Instant expiraEm;
    @Column(name="usado_em") private Instant usadoEm;
    protected MagicLoginToken() {}
}
