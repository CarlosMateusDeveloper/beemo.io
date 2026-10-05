package br.com.clinica.config;

import java.util.Locale;
import java.util.UUID;

public final class MicrosoftIssuer {
    private static final String CONSUMERS="9188040d-6c67-4c5b-b112-36a304b66dad";
    private MicrosoftIssuer() {}
    public static String audience(String configured) {
        String value=configured==null || configured.isBlank()?"common":configured.strip().toLowerCase(Locale.ROOT);
        if(!java.util.Set.of("common","organizations","consumers").contains(value) && !uuid(value))
            throw new IllegalStateException("MICROSOFT_TENANT_ID deve ser common, organizations, consumers ou UUID do diretorio.");
        return value;
    }
    public static boolean accepts(String configured,String tenant,String issuer) {
        if(!uuid(tenant) || !("https://login.microsoftonline.com/"+tenant+"/v2.0").equals(issuer)) return false;
        String audience=audience(configured);
        return audience.equals("common") || audience.equals(tenant)
            || audience.equals("organizations") && !CONSUMERS.equals(tenant)
            || audience.equals("consumers") && CONSUMERS.equals(tenant);
    }
    private static boolean uuid(String value) {
        if(value==null) return false;
        try { return UUID.fromString(value).toString().equals(value); } catch(IllegalArgumentException e) { return false; }
    }
}
