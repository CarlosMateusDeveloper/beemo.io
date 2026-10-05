package br.com.clinica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class OidcTokenValidation {
    @Bean JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(Environment env) {
        var factory=new OidcIdTokenDecoderFactory();
        String directory=MicrosoftIssuer.audience(env.getProperty("MICROSOFT_TENANT_ID","common"));
        factory.setJwtValidatorFactory(registration -> token -> {
            var effective=registration;
            if("microsoft".equals(registration.getRegistrationId())) {
                String tid=token.getClaimAsString("tid");
                String issuer=token.getIssuer()==null?"":token.getIssuer().toString();
                if(!MicrosoftIssuer.accepts(directory,tid,issuer))
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Emissor Microsoft inválido",null));
                effective=ClientRegistration.withClientRegistration(registration).issuerUri(issuer).build();
            }
            return new DelegatingOAuth2TokenValidator<Jwt>(new JwtTimestampValidator(),new OidcIdTokenValidator(effective)).validate(token);
        });
        return factory;
    }
}
