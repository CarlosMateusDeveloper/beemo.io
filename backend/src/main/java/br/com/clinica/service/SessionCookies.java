package br.com.clinica.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookies {
    public static final String NAME="clinicos_session";
    private final boolean secure;
    private final long minutos;
    public SessionCookies(@Value("${app.auth.secure-cookies:false}") boolean secure,
            @Value("${app.jwt.expiration-minutes}") long minutos) { this.secure=secure;this.minutos=minutos; }
    public static String token(HttpServletRequest req) {
        String header=req.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) return header.substring(7);
        if(req.getCookies()!=null) for(var c:req.getCookies()) if(NAME.equals(c.getName())) return c.getValue();
        return "";
    }
    public void emitir(HttpServletRequest req,HttpServletResponse res,String token) {
        res.addHeader("Set-Cookie",ResponseCookie.from(NAME,token).httpOnly(true).secure(secure||req.isSecure())
            .sameSite("Lax").path("/").maxAge(minutos*60).build().toString());
    }
    public void limpar(HttpServletRequest req,HttpServletResponse res) {
        res.addHeader("Set-Cookie",ResponseCookie.from(NAME,"").httpOnly(true).secure(secure||req.isSecure())
            .sameSite("Lax").path("/").maxAge(0).build().toString());
    }
}
