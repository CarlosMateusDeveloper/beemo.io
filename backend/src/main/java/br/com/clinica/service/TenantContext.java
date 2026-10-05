package br.com.clinica.service;

public final class TenantContext {
    private static final ThreadLocal<TenantService.Access> CURRENT=new ThreadLocal<>();
    private TenantContext() {}
    public static TenantService.Access get() { return CURRENT.get(); }
    public static int id() {
        var tenant=get();
        if(tenant==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Sua conta ainda não possui acesso a esta organização.");
        return tenant.id();
    }
    public static void set(TenantService.Access value) { CURRENT.set(value); }
    public static void clear() { CURRENT.remove(); }
}
