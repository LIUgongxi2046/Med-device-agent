package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLSession;

public final class ServiceCertificateIdentity {
    private static final Set<String> TLS13_CIPHERS = Set.of("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256");
    private final TrustSource currentTrust;

    public ServiceCertificateIdentity(TrustSource currentTrust) { this.currentTrust = Objects.requireNonNull(currentTrust); }

    public Verified require(SSLSession session) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(session);
        Trust trust = Objects.requireNonNull(currentTrust.read());
        if (!trust.protocols().contains(session.getProtocol()) || !trust.ciphers().contains(session.getCipherSuite())) {
            throw new SecurityException("SERVICE_TLS_POLICY_REQUIRED");
        }
        var chain = new ArrayList<X509Certificate>();
        for (var certificate : session.getPeerCertificates()) {
            if (!(certificate instanceof X509Certificate x509)) throw new SecurityException("SERVICE_X509_REQUIRED");
            chain.add(x509);
        }
        return verify(chain, trust);
    }

    public Verified require(HttpServletRequest request) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(request);
        if (!request.isSecure() || request.getHeader("Cookie") != null || request.getHeader("Origin") != null) {
            throw new SecurityException("SERVICE_TRANSPORT_REQUIRED");
        }
        Trust trust = Objects.requireNonNull(currentTrust.read());
        if (!trust.ciphers().contains(request.getAttribute("jakarta.servlet.request.cipher_suite"))) {
            throw new SecurityException("SERVICE_TLS_POLICY_REQUIRED");
        }
        Object certificates = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(certificates instanceof X509Certificate[] chain) || chain.length == 0) {
            throw new SecurityException("SERVICE_X509_REQUIRED");
        }
        return verify(List.of(chain.clone()), trust);
    }

    private Verified verify(List<X509Certificate> supplied, Trust trust) throws GeneralSecurityException {
        if (supplied.isEmpty()) throw new SecurityException("SERVICE_X509_REQUIRED");
        X509Certificate leaf = supplied.get(0);
        leaf.checkValidity();
        boolean[] usage = leaf.getKeyUsage();
        if (leaf.getBasicConstraints() != -1 || usage == null || usage.length == 0 || !usage[0]
                || leaf.getExtendedKeyUsage() == null || !leaf.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.2")) {
            throw new SecurityException("SERVICE_CLIENT_CERTIFICATE_REQUIRED");
        }
        var anchors = new HashSet<TrustAnchor>();
        for (var certificate : trust.authorities()) {
            certificate.checkValidity(); boolean[] caUsage = certificate.getKeyUsage();
            if (certificate.getBasicConstraints() < 0 || caUsage == null || caUsage.length < 7 || !caUsage[5] || !caUsage[6]) {
                throw new SecurityException("SERVICE_TRUST_AUTHORITY_REQUIRED");
            }
            anchors.add(new TrustAnchor(certificate, null));
        }
        var chain = new ArrayList<>(supplied);
        if (trust.authorities().contains(chain.get(chain.size() - 1))) chain.remove(chain.size() - 1);
        if (chain.isEmpty()) throw new SecurityException("SERVICE_LEAF_CERTIFICATE_REQUIRED");
        var parameters = new PKIXParameters(anchors);
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(trust.revocations())));
        var validator = CertPathValidator.getInstance("PKIX");
        // 每次请求创建独立检查器，CRL不足或验证失败立即报告。
        var revocation = (PKIXRevocationChecker) validator.getRevocationChecker();
        revocation.setOptions(Set.of(PKIXRevocationChecker.Option.PREFER_CRLS, PKIXRevocationChecker.Option.NO_FALLBACK));
        parameters.addCertPathChecker(revocation);
        validator.validate(CertificateFactory.getInstance("X.509").generateCertPath(chain), parameters);
        var names = leaf.getSubjectAlternativeNames();
        var uris = new ArrayList<String>();
        if (names != null) for (var name : names) if (Integer.valueOf(6).equals(name.get(0))) uris.add((String) name.get(1));
        if (uris.size() != 1) throw new SecurityException("SERVICE_UNIQUE_URI_REQUIRED");
        String service = uris.get(0), digest = WireValues.sha256(leaf.getEncoded());
        Set<String> registered = trust.registeredCertificates().get(service);
        if (registered == null || !registered.contains(digest)) throw new SecurityException("SERVICE_REGISTRATION_REQUIRED");
        return new Verified(service, digest, trust.revision());
    }

    @FunctionalInterface
    public interface TrustSource { Trust read() throws IOException, GeneralSecurityException; }

    public record Trust(String revision, List<X509Certificate> authorities, List<X509CRL> revocations,
            Map<String, Set<String>> registeredCertificates, Set<String> protocols, Set<String> ciphers) {
        public Trust {
            WireValues.uuid(revision);
            authorities = List.copyOf(authorities); revocations = List.copyOf(revocations);
            protocols = Set.copyOf(protocols); ciphers = Set.copyOf(ciphers);
            if (authorities.isEmpty() || revocations.isEmpty() || !protocols.equals(Set.of("TLSv1.3"))
                    || ciphers.isEmpty() || !TLS13_CIPHERS.containsAll(ciphers)) {
                throw new IllegalArgumentException("SERVICE_TRUST_CONFIGURATION_REQUIRED");
            }
            var registered = new HashMap<String, Set<String>>();
            registeredCertificates.forEach((uri, values) -> {
                URI parsed = URI.create(uri);
                if (!parsed.isAbsolute() || parsed.getFragment() != null || values.isEmpty()) {
                    throw new IllegalArgumentException("SERVICE_REGISTRATION_CONFIGURATION_REQUIRED");
                }
                Set<String> pins = Set.copyOf(values);
                for (String pin : pins) WireValues.hash(pin);
                registered.put(uri, pins);
            });
            if (registered.isEmpty()) throw new IllegalArgumentException("SERVICE_REGISTRATION_CONFIGURATION_REQUIRED");
            registeredCertificates = Map.copyOf(registered);
        }
    }

    public static final class Verified {
        private final String serviceUri;
        private final String certificateSha256;
        private final String trustRevision;
        private Verified(String serviceUri, String certificateSha256, String trustRevision) {
            this.serviceUri = serviceUri; this.certificateSha256 = certificateSha256; this.trustRevision = trustRevision;
        }
        public String serviceUri() { return serviceUri; }
        public String certificateSha256() { return certificateSha256; }
        public String trustRevision() { return trustRevision; }
        @Override public String toString() { return "VerifiedServiceCertificate"; }
    }
}
