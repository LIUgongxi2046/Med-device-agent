package com.ruoyi.medical.persistence.erasure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Explicit medical-external-erasure/1 protocol, not an assertion of compatibility with any
 * commercial provider. TLS trust and dedicated bearer credential are deployment-owned. No redirects,
 * endpoint discovery, URI from user requests, automatic retry or invented provider acknowledgements. */
public final class ExternalErasureHttpApi implements Api {
    private final URI endpoint;
    private final String bearer;
    private final Proof proof;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    public ExternalErasureHttpApi(URI endpoint,String bearer,Proof proof) {
        if(!"https".equals(endpoint.getScheme()) || endpoint.getHost()==null || endpoint.getRawUserInfo()!=null ||
            endpoint.getRawQuery()!=null || endpoint.getRawFragment()!=null || !endpoint.getPath().endsWith("/"))
            throw new IllegalArgumentException("EXPLICIT_HTTPS_AUTHORITY_REQUIRED");
        if(bearer==null || bearer.isBlank() || bearer.contains("\r") || bearer.contains("\n"))
            throw new IllegalArgumentException("AUTHORITY_CREDENTIAL_REQUIRED");
        this.endpoint=endpoint;this.bearer=bearer;this.proof=Objects.requireNonNull(proof);
    }
    private <T>T post(String path,Object payload,Class<T> type)throws Exception {
        var request=HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type","application/json").header("Accept","application/json")
            .header("X-Schema-Version",VERSION).header("Authorization","Bearer "+bearer)
            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()!=200 || !VERSION.equals(response.headers().firstValue("X-Schema-Version").orElse("")) ||
                !response.headers().firstValue("Content-Type").orElse("").split(";")[0].equals("application/json"))
                throw new IllegalStateException("EXTERNAL_AUTHORITY_UNAVAILABLE");
            byte[] bytes=body.readNBytes(1_048_577);
            if(bytes.length>1_048_576)throw new IllegalStateException("EXTERNAL_RESPONSE_LIMIT");
            return mapper.readValue(bytes,type);
        }
    }
    public Page inventory(Scope scope,String cursor)throws Exception {
        var request=new LinkedHashMap<String,Object>();request.put("schemaVersion",VERSION);request.put("proof",proof);
        request.put("scope",scope);request.put("cursor",cursor);
        return post("inventory",request,Page.class);
    }
    public Copy inspect(Scope scope,String copyId,String version)throws Exception {
        return post("hold",Map.of("schemaVersion",VERSION,"proof",proof,"scope",scope,"copyId",copyId,"version",version),Copy.class);
    }
    public Receipt delete(Delete request)throws Exception {return post("delete",request,Receipt.class);}
    public record Recovery(String schemaVersion,Proof proof,Scope scope,String key,Receipt receipt) {
        public Recovery {version(schemaVersion);Objects.requireNonNull(proof);Objects.requireNonNull(scope);token(key);}
    }
    public Optional<Receipt> recover(Scope scope,String key)throws Exception {
        Recovery response=post("recovery",Map.of("schemaVersion",VERSION,"proof",proof,"scope",scope,"key",key),Recovery.class);
        if(!scope.equals(response.scope()) || !key.equals(response.key()) || response.proof()!=proof)
            throw new IllegalStateException("RECOVERY_SCOPE_MISMATCH");
        return Optional.ofNullable(response.receipt());
    }
}
