package br.com.clinica.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.client.registration.*;
import java.util.*;

@Component
public class OAuthClients implements ClientRegistrationRepository, Iterable<ClientRegistration> {
    private final Map<String,ClientRegistration> clients=new LinkedHashMap<>();
    private final String api;
    private final String frontend;
    public OAuthClients(Environment env) {
        api=validUrl(env.getProperty("APP_API_URL","http://localhost:8080"));
        frontend=validUrl(env.getProperty("app.auth.frontend-url","http://localhost:5173"));
        if(frontend.startsWith("https://") && (env.getProperty("app.jwt.secret","").isBlank()
                || !env.getProperty("app.auth.secure-cookies",Boolean.class,false) || !api.startsWith("https://"))) {
            throw new IllegalStateException("Em HTTPS configure JWT_SECRET, AUTH_SECURE_COOKIES=true e APP_API_URL HTTPS");
        }
        configure(env,"google","Google","https://accounts.google.com","GOOGLE_CLIENT_ID","GOOGLE_CLIENT_SECRET");
        String msId=env.getProperty("MICROSOFT_CLIENT_ID","");
        String tenant=env.getProperty("MICROSOFT_TENANT_ID","");
        if(!msId.isBlank() && !tenant.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalStateException("Configure MICROSOFT_TENANT_ID com o ID do tenant autorizado");
        configure(env,"microsoft","Microsoft","https://login.microsoftonline.com/"+tenant+"/v2.0","MICROSOFT_CLIENT_ID","MICROSOFT_CLIENT_SECRET");
    }
    private void configure(Environment env,String id,String name,String issuer,String idKey,String secretKey) {
        String clientId=env.getProperty(idKey,""),secret=env.getProperty(secretKey,"");
        if(clientId.isBlank() && secret.isBlank()) return;
        if(clientId.isBlank() || secret.isBlank()) throw new IllegalStateException("Configuração OAuth incompleta para "+id);
        var registration=ClientRegistrations.fromIssuerLocation(issuer).registrationId(id)
            .clientId(clientId).clientSecret(secret).clientName(name).scope("openid","profile","email")
            .redirectUri(api+"/login/oauth2/code/"+id)
            .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build()).build();
        clients.put(id,registration);
    }
    static String validUrl(String text) {
        var uri=java.net.URI.create(text);
        boolean local="http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1").contains(Objects.toString(uri.getHost(),""));
        if((!"https".equals(uri.getScheme()) && !local) || uri.getHost()==null || uri.getUserInfo()!=null
            || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("A URL pública deve ser uma origem HTTPS; HTTP somente em localhost");
        return text.replaceAll("/+$","");
    }
    public String frontend() { return frontend; }
    public String authorization(String id) {
        if(!clients.containsKey(id)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Provedor indisponível");
        return api+"/oauth2/authorization/"+id;
    }
    @Override public ClientRegistration findByRegistrationId(String id) { return clients.get(id); }
    @Override public Iterator<ClientRegistration> iterator() { return clients.values().iterator(); }
    public List<Map<String,String>> available() {
        return clients.values().stream().map(c -> Map.of("id",c.getRegistrationId(),"nome",c.getClientName(),"url",authorization(c.getRegistrationId()))).toList();
    }
}
