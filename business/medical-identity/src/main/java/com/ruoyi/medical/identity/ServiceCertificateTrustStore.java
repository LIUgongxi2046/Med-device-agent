package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

public final class ServiceCertificateTrustStore {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String CURRENT = "trust.json";
    private final ProtectedConfigurationFile configuration;
    private final JsonSchema schema;

    public ServiceCertificateTrustStore(Path directory, UserPrincipal owner, long maximumBytes)
            throws IOException, GeneralSecurityException {
        configuration = new ProtectedConfigurationFile(directory, owner, CURRENT, maximumBytes);
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("service-certificate-trust.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(JSON.readTree(input),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
        read();
    }

    public ServiceCertificateIdentity.Trust read() throws IOException, GeneralSecurityException {
        byte[] body = configuration.read();
        JsonNode value = JSON.readTree(body);
        if (value == null || !schema.validate(value).isEmpty()) throw new SecurityException("SERVICE_TRUST_SCHEMA_REQUIRED");
        var factory = CertificateFactory.getInstance("X.509");
        var authorities = new ArrayList<X509Certificate>();
        var issuers = new HashMap<X500Principal, X509Certificate>();
        for (var item : value.required("authorities")) {
            byte[] encoded = decode(item.textValue());
            var authority = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded));
            if (!Arrays.equals(encoded, authority.getEncoded())) throw new SecurityException("SERVICE_TRUST_CERTIFICATE_DER_REQUIRED");
            authority.checkValidity(); boolean[] usage = authority.getKeyUsage();
            if (authority.getBasicConstraints() < 0 || usage == null || usage.length < 7 || !usage[5] || !usage[6]
                    || issuers.putIfAbsent(authority.getSubjectX500Principal(), authority) != null) {
                throw new SecurityException("SERVICE_TRUST_AUTHORITY_REQUIRED");
            }
            authorities.add(authority);
        }
        var revocations = new ArrayList<X509CRL>();
        var covered = new HashSet<X500Principal>(); Instant now = Instant.now();
        for (var item : value.required("revocations")) {
            byte[] encoded = decode(item.textValue());
            var crl = (X509CRL) factory.generateCRL(new ByteArrayInputStream(encoded));
            if (!Arrays.equals(encoded, crl.getEncoded())) throw new SecurityException("SERVICE_TRUST_CRL_DER_REQUIRED");
            var authority = issuers.get(crl.getIssuerX500Principal());
            if (authority == null || !covered.add(crl.getIssuerX500Principal())) throw new SecurityException("SERVICE_TRUST_CRL_ISSUER_REQUIRED");
            crl.verify(authority.getPublicKey());
            if (crl.getNextUpdate() == null || crl.getThisUpdate().toInstant().isAfter(now)
                    || !crl.getNextUpdate().toInstant().isAfter(now)) throw new GeneralSecurityException("SERVICE_TRUST_CURRENT_CRL_REQUIRED");
            revocations.add(crl);
        }
        if (!covered.equals(issuers.keySet())) throw new SecurityException("SERVICE_TRUST_CRL_COVERAGE_REQUIRED");
        var registered = new HashMap<String, Set<String>>();
        value.required("registered_certificates").fields().forEachRemaining(item -> registered.put(item.getKey(), strings(item.getValue())));
        return new ServiceCertificateIdentity.Trust(value.required("revision").textValue(), authorities, revocations, registered,
            strings(value.required("protocols")), strings(value.required("ciphers")));
    }

    private static byte[] decode(String value) {
        byte[] result = Base64.getDecoder().decode(value);
        if (!Base64.getEncoder().encodeToString(result).equals(value)) throw new SecurityException("SERVICE_TRUST_BASE64_REQUIRED");
        return result;
    }

    private static Set<String> strings(JsonNode items) {
        var result = new HashSet<String>();
        for (var item : items) if (!result.add(item.textValue())) throw new SecurityException("SERVICE_TRUST_UNIQUE_VALUES_REQUIRED");
        return Set.copyOf(result);
    }
}
