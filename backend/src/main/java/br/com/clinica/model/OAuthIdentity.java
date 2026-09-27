package br.com.clinica.model;
import jakarta.persistence.*;

@Entity
@Table(name="auth_oauth_identity",uniqueConstraints={
    @UniqueConstraint(name="uq_oauth_subject",columnNames={"issuer","subject"}),
    @UniqueConstraint(name="uq_oauth_usuario_provider",columnNames={"id_usuario","provider"})})
public class OAuthIdentity {
    @Id @Column(length=36) private String id;
    @Column(name="id_usuario",nullable=false) private Integer idUsuario;
    @Column(nullable=false,length=30) private String provider;
    @Column(nullable=false,length=400) private String issuer;
    @Column(nullable=false,length=255) private String subject;
    protected OAuthIdentity() {}
}
