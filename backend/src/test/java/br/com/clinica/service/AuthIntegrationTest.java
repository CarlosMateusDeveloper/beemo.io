package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RUN_AUTH_INTEGRATION",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.jpa.show-sql=false","app.auth.frontend-url=http://localhost:5173"})
class AuthIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired MagicLinkService magic;
    @Autowired UsuarioEscritaService escrita;
    @Autowired OAuthIdentityService identities;
    @Autowired JwtService jwtSigner;
    @MockitoBean ResendEmailService email;

    private record Resposta(int status, Map<?,?> body, org.springframework.http.HttpHeaders headers) {
        String session() {
            return headers.getOrEmpty("Set-Cookie").stream().filter(c -> c.startsWith("clinicos_session="))
                .map(c -> c.substring("clinicos_session=".length(),c.indexOf(';'))).findFirst().orElseThrow();
        }
    }
    private Resposta request(String method,String path,Object body,String token) {
        return request(method,path,body,token,true);
    }
    private Resposta request(String method,String path,Object body,String token,boolean withCsrf) {
        var client=RestClient.create("http://127.0.0.1:"+port);
        var spec=client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        String cookie=token==null?"":"clinicos_session="+token;
        if(withCsrf && !method.equals("GET")) {
            var csrf=client.get().uri("/api/auth/csrf").exchange((req,res)->new Resposta(res.getStatusCode().value(),res.bodyTo(Map.class),res.getHeaders()));
            cookie+=(cookie.isEmpty()?"":"; ")+csrf.headers().getFirst("Set-Cookie").split(";")[0];
            spec.header("X-CSRF-TOKEN",(String)csrf.body().get("token"));
        }
        if(!cookie.isEmpty()) spec.header("Cookie",cookie);
        if(body!=null) spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
        return spec.exchange((req,res)->new Resposta(res.getStatusCode().value(),res.bodyTo(Map.class),res.getHeaders()));
    }
    @Test void fluxoRealDeSenhaLinkConcorrenteExpiradoEPermissoes() throws Exception {
        String address="auth-test-"+UUID.randomUUID()+"@example.invalid";
        Integer id=db.queryForObject("INSERT INTO usuario(nome,email,senha,perfil) VALUES ('Teste temporario',?,?,CAST('administrador' AS perfil_usuario)) RETURNING id",Integer.class,address,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Test-only-Password!82"));
        try {
            assertEquals(401,request("GET","/api/pacientes",null,null).status());
            assertEquals(401,request("GET","/api/auth/me",null,null).status());
            assertEquals(403,request("POST","/api/auth/login",Map.of("email",address,"senha","Test-only-Password!82"),null,false).status());
            assertEquals(400,request("POST","/api/auth/login",Map.of(),null).status());
            var errado=request("POST","/api/auth/login",Map.of("email",address,"senha","errada"),null);
            assertEquals(401,errado.status());
            assertEquals("E-mail ou senha inválidos",errado.body().get("message"));
            var login=request("POST","/api/auth/login",Map.of("email",address.toUpperCase(),"senha","Test-only-Password!82"),null);
            assertEquals(200,login.status());
            assertFalse(login.body().containsKey("token"));
            assertTrue(login.headers().getFirst("Set-Cookie").contains("HttpOnly"));
            assertTrue(login.headers().getFirst("Set-Cookie").contains("SameSite=Lax"));
            assertTrue(db.queryForObject("SELECT senha FROM usuario WHERE id=?",String.class,id).startsWith("$argon2id$"));
            String jwt=login.session();
            var noSession=new br.com.clinica.model.Usuario();noSession.setId(id);
            assertEquals(401,request("GET","/api/auth/me",null,jwtSigner.gerar(noSession)).status());
            int cors=RestClient.create("http://127.0.0.1:"+port).get().uri("/api/auth/csrf")
                .header("Origin","https://attacker.example").exchange((req,res)->res.getStatusCode().value());
            assertEquals(403,cors);
            assertEquals(403,request("POST","/api/auth/logout",null,jwt,false).status());
            assertEquals(200,request("GET","/api/auth/me",null,jwt).status());
            assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,()->identities.localizar("https://accounts.google.com","unknown"));
            identities.vincular(id,"google","https://accounts.google.com","subject-"+id);
            assertEquals(id,identities.localizar("https://accounts.google.com","subject-"+id).getId());
            Integer outro=db.queryForObject("INSERT INTO usuario(nome,email,senha) VALUES ('Teste temporario',?,?) RETURNING id",Integer.class,"auth-other-"+UUID.randomUUID()+"@example.invalid",encoder.encode("temporary-only-password"));
            try {
                assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                    ()->identities.vincular(outro,"google","https://accounts.google.com","subject-"+id));
                assertEquals(id,identities.localizar("https://accounts.google.com","subject-"+id).getId());
                assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                    ()->identities.localizar("https://different.example","subject-"+id));
            } finally { db.update("DELETE FROM auth_oauth_identity WHERE id_usuario=?",outro);db.update("DELETE FROM usuario WHERE id=?",outro); }
            assertEquals(200,request("GET","/api/auth/me",null,jwt).status());
            assertEquals(401,request("GET","/api/auth/me",null,jwt+"invalid").status());
            AtomicReference<String> link=new AtomicReference<>();
            doAnswer(inv -> { link.set(inv.getArgument(1)); return null; }).when(email).enviar(eq(address),anyString(),anyString());
            var pedido=request("POST","/api/auth/magic-link",Map.of("email",address),null);
            assertEquals(202,pedido.status());
            assertNotNull(link.get());
            String token=link.get().substring(link.get().indexOf("#token=")+7);
            assertNotEquals(token,db.queryForObject("SELECT hash FROM auth_magic_token WHERE id_usuario=?",String.class,id));
            try(var pool=Executors.newFixedThreadPool(2)) {
                Callable<Integer> verify=()->request("POST","/api/auth/magic-link/verify",Map.of("token",token),null).status();
                var resultados=pool.invokeAll(List.of(verify,verify));
                var statuses=List.of(resultados.get(0).get(),resultados.get(1).get());
                assertTrue(statuses.contains(200) && statuses.contains(401),statuses.toString());
            }
            magic.solicitar(address);
            String expirado=link.get().substring(link.get().indexOf("#token=")+7);
            db.update("UPDATE auth_magic_token SET expira_em=now()-interval '1 minute' WHERE hash=?",MagicLinkService.hash(expirado));
            assertEquals(401,request("POST","/api/auth/magic-link/verify",Map.of("token",expirado),null).status());
            clearInvocations(email);
            assertEquals(202,request("POST","/api/auth/magic-link",Map.of("email","ausente-"+UUID.randomUUID()+"@example.invalid"),null).status());
            verify(email,never()).enviar(anyString(),anyString(),anyString());
            long antes=db.queryForObject("SELECT count(*) FROM auth_magic_token WHERE id_usuario=?",Long.class,id);
            doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Falha simulada"))
                .when(email).enviar(eq(address),anyString(),anyString());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->magic.solicitar(address));
            assertEquals(antes,db.queryForObject("SELECT count(*) FROM auth_magic_token WHERE id_usuario=?",Long.class,id));
            db.update("UPDATE usuario SET perfil=CAST('medico' AS perfil_usuario) WHERE id=?",id);
            assertEquals(403,request("GET","/api/usuarios",null,jwt).status());
            assertEquals(204,request("POST","/api/auth/logout",null,jwt).status());
            assertEquals(401,request("GET","/api/auth/me",null,jwt).status());
            db.update("DELETE FROM auth_magic_token WHERE id_usuario=?",id);
            db.update("DELETE FROM auth_session WHERE id_usuario=?",id);
            db.update("DELETE FROM auth_oauth_identity WHERE id_usuario=?",id);
            db.update("DELETE FROM usuario WHERE id=?",id);
            assertEquals(401,request("GET","/api/auth/me",null,jwt).status());
        } finally {
            db.update("DELETE FROM auth_magic_token WHERE id_usuario=?",id);
            db.update("DELETE FROM auth_session WHERE id_usuario=?",id);
            db.update("DELETE FROM auth_oauth_identity WHERE id_usuario=?",id);
            db.update("DELETE FROM usuario WHERE id=?",id);
        }
    }
}
