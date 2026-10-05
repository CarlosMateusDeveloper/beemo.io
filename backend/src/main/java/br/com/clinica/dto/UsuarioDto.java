package br.com.clinica.dto;
import br.com.clinica.service.TenantService;
public record UsuarioDto(Integer id,String nome,String email,String perfil,TenantService.Access tenantAtivo) {}
