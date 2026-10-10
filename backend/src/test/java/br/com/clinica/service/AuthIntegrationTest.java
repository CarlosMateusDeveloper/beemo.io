package br.com.clinica.service;

import br.com.clinica.config.OAuthClients;
import br.com.clinica.model.Usuario;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RUN_AUTH_INTEGRATION",matches="true")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.jpa.show-sql=false","app.auth.frontend-url=http://localhost:5173",
    "GOOGLE_CLIENT_ID=","GOOGLE_CLIENT_SECRET=","MICROSOFT_CLIENT_ID=","MICROSOFT_CLIENT_SECRET=",
    "MICROSOFT_TENANT_ID=common"})
class AuthIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired OAuthLoginService oauth;
    @Autowired OAuthLoginSuccessHandler callback;
    @Autowired SessionService sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ResendEmailService email;
    final List<Integer> users=new ArrayList<>();
    final List<Integer> tenants=new ArrayList<>();
    final List<String> emails=new ArrayList<>();
    record Reply(int status,Object body,org.springframework.http.HttpHeaders headers) {
        @SuppressWarnings("unchecked") Map<String,Object> map() { return (Map<String,Object>)body; }
        String session() {
            String value=headers.getOrEmpty("Set-Cookie").stream().filter(c->c.startsWith("clinicos_session=")).findFirst().orElseThrow();
            return value.substring("clinicos_session=".length(),value.indexOf(';'));
        }
    }
    Reply request(String method,String path,Object body,String token) { return request(method,path,body,token,true); }
    Reply request(String method,String path,Object body,String token,boolean withCsrf) {
        var client=RestClient.create("http://127.0.0.1:"+port);
        var spec=client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        String cookie=token==null?"":"clinicos_session="+token;
        if(withCsrf && !method.equals("GET")) {
            var csrf=request("GET","/api/auth/csrf",null,null,false);
            cookie+=(cookie.isEmpty()?"":"; ")+csrf.headers().getFirst("Set-Cookie").split(";")[0];
            spec.header("X-CSRF-TOKEN",(String)csrf.map().get("token"));
        }
        if(!cookie.isEmpty()) spec.header("Cookie",cookie);
        if(body!=null) spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
        return spec.exchange((req,res)->new Reply(res.getStatusCode().value(),res.bodyTo(Object.class),res.getHeaders()));
    }
    String address(String domain) { String value="tenant-test-"+UUID.randomUUID()+"@"+domain;emails.add(value);return value; }
    String signup(String provider,String email) throws Exception {
        String issuer=provider.equals("google")?OAuthLoginServiceTest.GOOGLE:OAuthLoginServiceTest.MICROSOFT;
        var principal=OAuthLoginServiceTest.principal(issuer,UUID.randomUUID().toString(),
            Map.of("email",email,"email_verified",true,"name","Conta de teste","tid",OAuthLoginServiceTest.TENANT));
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        var res=new org.springframework.mock.web.MockHttpServletResponse();
        var temporary=(org.springframework.mock.web.MockHttpSession)req.getSession(true);
        callback.onAuthenticationSuccess(req,res,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),provider));
        assertEquals("http://localhost:5173/login?oauth=success",res.getRedirectedUrl());
        assertTrue(temporary.isInvalid());
        String value=res.getHeader("Set-Cookie");assertNotNull(value);
        assertTrue(value.contains("HttpOnly"));
        String token=value.substring("clinicos_session=".length(),value.indexOf(';'));
        var claims=sessions.validar(token).orElseThrow();
        int id=Integer.parseInt(claims.getSubject());users.add(id);
        tenants.add(db.queryForObject("SELECT id_clinica FROM auth_session WHERE id=?",Integer.class,claims.getId()));
        return token;
    }
    int patient(String token,String name) {
        var result=request("POST","/api/pacientes",Map.of("nome",name,"cpf","00000000001","dataNascimento","1990-01-01","ddd","85","numero","999999999"),token);
        assertEquals(201,result.status(),String.valueOf(result.body()));
        return ((Number)result.map().get("id")).intValue();
    }
    <T> T scope(int tenant,Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status->{
            db.queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,Integer.toString(tenant));return work.get();
        });
    }
    @AfterEach void cleanup() {
        for(int tenant:tenants) scope(tenant,()->{
            db.update("DELETE FROM paciente");db.update("DELETE FROM convenio");return null;
        });
        for(int tenant:tenants) {
            db.update("DELETE FROM tenant_convite WHERE id_clinica=?",tenant);
            db.update("UPDATE auth_session SET id_clinica=NULL WHERE id_clinica=?",tenant);
            db.update("DELETE FROM tenant_membro WHERE id_clinica=?",tenant);
            db.update("DELETE FROM clinica WHERE id_clinica=?",tenant);
        }
        for(String address:emails) db.update("DELETE FROM auth_magic_token WHERE email=?",address);
        for(int user:new HashSet<>(users)) {
            db.update("DELETE FROM auth_session WHERE id_usuario=?",user);
            db.update("DELETE FROM auth_oauth_identity WHERE id_usuario=?",user);
            db.update("DELETE FROM auth_email_identity WHERE id_usuario=?",user);
            db.update("DELETE FROM usuario WHERE id=?",user);
        }
    }
    @Test void autenticacaoLivreAutorizacaoPorTenantEIsolamentoDeDados() throws Exception {
        String a=signup("google",address("gmail.com"));int userA=users.getLast();
        String b=signup("microsoft",address("example.com"));
        var me=request("GET","/api/auth/me",null,a);
        assertEquals(200,me.status());assertNotNull(me.map().get("tenantAtivo"));assertEquals("administrador",me.map().get("perfil"));
        assertFalse(me.map().containsKey("tenants"));
        assertNull(db.queryForObject("SELECT perfil FROM usuario WHERE id=?",String.class,userA));
        assertEquals(200,request("GET","/api/pacientes",null,a).status());
        assertEquals(200,request("GET","/api/auth/session/check",null,b).status());
        assertEquals(404,request("POST","/api/tenants",Map.of("nome","Proibido"),a).status());
        assertEquals(404,request("POST","/api/tenants/1/select",null,a).status());
        @SuppressWarnings("unchecked") var tenantA=(Map<String,Object>)me.map().get("tenantAtivo");
        @SuppressWarnings("unchecked") var tenantB=(Map<String,Object>)request("GET","/api/auth/me",null,b).map().get("tenantAtivo");
        int ta=((Number)tenantA.get("id")).intValue(),tb=((Number)tenantB.get("id")).intValue();
        int pa=patient(a,"Paciente A"),pb=patient(b,"Paciente B");
        assertEquals(200,request("GET","/api/pacientes/"+pa,null,a).status());
        assertEquals(404,request("GET","/api/pacientes/"+pb,null,a).status());
        assertEquals(404,request("DELETE","/api/pacientes/"+pb,null,a).status());
        assertEquals(200,request("GET","/api/pacientes/"+pb,null,b).status());
        int convenio=scope(tb,()->db.queryForObject("INSERT INTO convenio(nome,registro_ans) VALUES ('Convenio B','987654') RETURNING id_convenio",Integer.class));
        var error=assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->scope(ta,()->db.update("UPDATE paciente SET id_convenio=? WHERE id_paciente=?",convenio,pa)));
        assertEquals("23503",((java.sql.SQLException)error.getMostSpecificCause()).getSQLState());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM paciente",Integer.class));
        assertEquals(200,request("GET","/api/usuarios",null,a).status());
        assertEquals(200,request("GET","/api/usuarios",null,b).status());
        assertEquals(200,request("GET","/api/pacientes/"+pb,null,b).status());
        verifyNoInteractions(email);
    }
    @Test void emailExternoERepetidoNaoTomaIdentidadeExistente() throws Exception {
        String address=address("example.com");
        signup("google",address);signup("microsoft",address);
        assertNotEquals(users.get(0),users.get(1));
        for(int user:users) assertEquals(1,db.queryForObject("SELECT count(*) FROM tenant_membro WHERE id_usuario=?",Integer.class,user));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM auth_email_identity WHERE email=?",Integer.class,address));
    }
    @Test void magicLinkAceitaNovoEmailSemCriarContaAntesDaProvaEContinuaUsoUnico() throws Exception {
        String address=address("example.com");var link=new AtomicReference<String>();
        doAnswer(inv->{link.set(inv.getArgument(1));return null;}).when(email).enviar(eq(address),anyString(),anyString());
        assertEquals(202,request("POST","/api/auth/magic-link",Map.of("email",address),null).status());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM usuario WHERE email=?",Integer.class,address));
        String token=link.get().split("#token=")[1];
        assertNotEquals(token,db.queryForObject("SELECT hash FROM auth_magic_token WHERE email=?",String.class,address));
        Reply accepted;
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Reply> verify=()->request("POST","/api/auth/magic-link/verify",Map.of("token",token),null);
            var results=pool.invokeAll(List.of(verify,verify));
            var first=results.get(0).get();var second=results.get(1).get();
            assertEquals(Set.of(200,401),Set.of(first.status(),second.status()));
            accepted=first.status()==200?first:second;
        }
        String jwt=accepted.session();var claims=sessions.validar(jwt).orElseThrow();int user=Integer.parseInt(claims.getSubject());users.add(user);
        tenants.add(db.queryForObject("SELECT id_clinica FROM auth_session WHERE id=?",Integer.class,claims.getId()));
        assertNotNull(request("GET","/api/auth/me",null,jwt).map().get("tenantAtivo"));
        assertEquals(200,request("GET","/api/pacientes",null,jwt).status());
        assertEquals(403,request("POST","/api/auth/logout",null,jwt,false).status());
        assertEquals(204,request("POST","/api/auth/logout",null,jwt).status());
        assertEquals(401,request("GET","/api/auth/me",null,jwt).status());
        assertEquals(202,request("POST","/api/auth/magic-link",Map.of("email",address),null).status());
        String expired=link.get().split("#token=")[1];
        db.update("UPDATE auth_magic_token SET expira_em=now()-interval '1 minute' WHERE hash=?",MagicLinkService.hash(expired));
        assertEquals(401,request("POST","/api/auth/magic-link/verify",Map.of("token",expired),null).status());
    }
    @Test void senhaLegadaMigraELiberaTenantAutomaticamente() {
        String address=address("example.com");
        int user=db.queryForObject("INSERT INTO usuario(nome,email,senha,perfil) VALUES ('Teste BCrypt',?,?,NULL) RETURNING id",Integer.class,address,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Test-only-Password!82"));
        users.add(user);db.update("INSERT INTO auth_email_identity VALUES (?,?)",address,user);
        assertEquals(403,request("POST","/api/auth/login",Map.of("email",address,"senha","Test-only-Password!82"),null,false).status());
        assertEquals(401,request("POST","/api/auth/login",Map.of("email",address,"senha","errada"),null).status());
        var login=request("POST","/api/auth/login",Map.of("email",address.toUpperCase(),"senha","Test-only-Password!82"),null);
        assertEquals(200,login.status());assertFalse(login.map().containsKey("token"));
        var claims=sessions.validar(login.session()).orElseThrow();
        tenants.add(db.queryForObject("SELECT id_clinica FROM auth_session WHERE id=?",Integer.class,claims.getId()));
        assertTrue(db.queryForObject("SELECT senha FROM usuario WHERE id=?",String.class,user).startsWith("$argon2id$"));
        assertEquals(200,request("GET","/api/pacientes",null,login.session()).status());
    }
    @Test void primeiroSsoConcorrenteCriaSomenteUmaIdentidade() throws Exception {
        String address=address("gmail.com"),subject=UUID.randomUUID().toString();
        var principal=OAuthLoginServiceTest.principal(OAuthLoginServiceTest.GOOGLE,subject,Map.of("email",address,"email_verified",true));
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Usuario> operation=()->oauth.entrar("google",principal);
            var results=pool.invokeAll(List.of(operation,operation));
            var first=results.get(0).get();users.add(first.getId());var second=results.get(1).get();users.add(second.getId());
            assertEquals(first.getId(),second.getId());
            assertEquals(1,db.queryForObject("SELECT count(*) FROM auth_oauth_identity WHERE issuer=? AND subject=?",Integer.class,OAuthLoginServiceTest.GOOGLE,subject));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM tenant_membro WHERE id_usuario=?",Integer.class,first.getId()));
        }
    }
}
