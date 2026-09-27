package br.com.clinica.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Grava somente Argon2id; aceita BCrypt existente até o próximo login válido. */
public final class MigratingPasswordEncoder implements PasswordEncoder {
    private final Argon2PasswordEncoder argon=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final BCryptPasswordEncoder legacy=new BCryptPasswordEncoder();
    @Override public String encode(CharSequence raw) { return argon.encode(raw); }
    @Override public boolean matches(CharSequence raw,String encoded) {
        if(raw==null || encoded==null) return false;
        try {
            if(encoded.startsWith("$argon2id$")) return argon.matches(raw,encoded);
            if(encoded.matches("^\\$2[aby]\\$.*")) return legacy.matches(raw,encoded);
            return false; // nunca texto puro, {noop}, ou algoritmo arbitrário
        } catch(IllegalArgumentException e) { return false; }
    }
    @Override public boolean upgradeEncoding(String encoded) {
        return encoded!=null && (!encoded.startsWith("$argon2id$") || argon.upgradeEncoding(encoded));
    }
}
