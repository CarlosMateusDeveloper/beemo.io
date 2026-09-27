package br.com.clinica.model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="auth_session",indexes=@Index(name="idx_auth_session_expira",columnList="expira_em"))
public class AuthSession {
    @Id @Column(length=36) private String id;
    @Column(name="id_usuario",nullable=false) private Integer idUsuario;
    @Column(name="expira_em",nullable=false) private Instant expiraEm;
    @Column(name="revogada_em") private Instant revogadaEm;
    protected AuthSession() {}
}
