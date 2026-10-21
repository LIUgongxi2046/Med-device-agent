package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.WireValues;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.net.ssl.*;

/** Explicit protected client identity and pinned outbound server trust. Never creates keys,
 * registrations, credentials, permissive trust or deployment defaults. */
public final class ServiceClientTlsStore {
 private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final ProtectedConfigurationFile configuration;private final ServiceCertificateIdentity.TrustSource source;private final JsonSchema schema;
 public ServiceClientTlsStore(Path directory,UserPrincipal owner,long maximumBytes,ServiceCertificateIdentity.TrustSource trust)throws IOException,GeneralSecurityException{
  if(directory==null||!directory.isAbsolute()||!directory.normalize().equals(directory))throw new IllegalArgumentException("SERVICE_CLIENT_CONFIGURATION_REQUIRED");
  configuration=new ProtectedConfigurationFile(directory,owner,"client-tls.json",maximumBytes);source=Objects.requireNonNull(trust);
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/service-client-tls.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}schema.initializeValidators();
 }
 public record Remote(URI origin,String serviceUri,String certificateSha256,String trustRevision){
  public Remote{
   Objects.requireNonNull(origin);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||!Set.of("","/").contains(origin.getRawPath())||origin.getPort()==0||origin.getPort() < -1)throw new IllegalArgumentException("SERVICE_REMOTE_ORIGIN_REQUIRED");
   URI identity=URI.create(serviceUri);if(!identity.isAbsolute()||identity.getFragment()!=null)throw new IllegalArgumentException("SERVICE_REMOTE_IDENTITY_REQUIRED");WireValues.hash(certificateSha256);WireValues.uuid(trustRevision);
  }
 }
 private record Material(String fingerprint,PrivateKey key,X509Certificate[] chain,ServiceCertificateIdentity.Trust trust,String localService){}
 /** Immutable configured source. Each request gets a distinct TLS session cache so
  * resumed sessions cannot bypass the current trust manager before HTTP is sent. */
 public final class ContextSource {
  private final Remote remote;private final String original;
  private ContextSource(Remote remote,String original){this.remote=remote;this.original=original;}
  public SSLContext create()throws IOException,GeneralSecurityException{var material=read(remote);if(!original.equals(material.fingerprint))throw new SecurityException("SERVICE_CLIENT_CONFIGURATION_CHANGED");return build(remote,material);}
 }
 public ContextSource contexts(Remote remote)throws IOException,GeneralSecurityException{Objects.requireNonNull(remote);return new ContextSource(remote,read(remote).fingerprint);}
 public SSLContext context(Remote remote)throws IOException,GeneralSecurityException{return build(Objects.requireNonNull(remote),read(remote));}
 private SSLContext build(Remote remote,Material material)throws IOException,GeneralSecurityException{
  var keys=KeyStore.getInstance("PKCS12");keys.load(null,null);char[] password=new char[0];keys.setKeyEntry("client",material.key,password,material.chain);var factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(keys,password);
  var context=SSLContext.getInstance("TLSv1.3");context.init(factory.getKeyManagers(),new TrustManager[]{new PinnedTrust(remote,material.fingerprint)},new SecureRandom());return context;
 }
 private Material read(Remote remote)throws IOException,GeneralSecurityException{
  byte[] body=configuration.read();try{
   var value=JSON.readTree(body);if(value==null||!schema.validate(value).isEmpty())throw new SecurityException("SERVICE_CLIENT_SCHEMA_REQUIRED");
   var trust=source.read();if(!remote.trustRevision.equals(trust.revision())||!value.required("trust_revision").textValue().equals(trust.revision()))throw new SecurityException("SERVICE_CLIENT_TRUST_CHANGED");
   String service=value.required("service_uri").textValue();if(service.equals(remote.serviceUri))throw new SecurityException("INDEPENDENT_SERVICE_IDENTITY_REQUIRED");
   var certificates=new ArrayList<X509Certificate>();var cf=CertificateFactory.getInstance("X.509");
   for(var entry:value.required("certificate_chain")){byte[] der=decode(entry.textValue());var certificate=(X509Certificate)cf.generateCertificate(new ByteArrayInputStream(der));if(!Arrays.equals(der,certificate.getEncoded()))throw new SecurityException("SERVICE_CLIENT_CERTIFICATE_DER_REQUIRED");certificates.add(certificate);}
   var chain=certificates.toArray(X509Certificate[]::new);var verified=new ServiceCertificateIdentity(()->trust).requireClientCertificate(chain);
   if(!service.equals(verified.serviceUri())||!value.required("certificate_sha256").textValue().equals(verified.certificateSha256()))throw new SecurityException("SERVICE_CLIENT_IDENTITY_MISMATCH");
   var registered=trust.registeredCertificates().get(remote.serviceUri);if(registered==null||!registered.contains(remote.certificateSha256))throw new SecurityException("SERVICE_REMOTE_REGISTRATION_REQUIRED");
   String algorithm=value.required("key_algorithm").textValue();byte[] encoded=decode(value.required("private_key_pkcs8").textValue());PrivateKey key;
   try{key=KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));}finally{Arrays.fill(encoded,(byte)0);}
   String signature=switch(algorithm){case "EC"->"SHA256withECDSA";case "RSA"->"SHA256withRSA";case "Ed25519"->"Ed25519";default->throw new SecurityException("SERVICE_CLIENT_KEY_ALGORITHM_REQUIRED");};
   byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);var signer=Signature.getInstance(signature);signer.initSign(key);signer.update(challenge);byte[] proof=signer.sign();signer.initVerify(chain[0].getPublicKey());signer.update(challenge);if(!signer.verify(proof))throw new SecurityException("SERVICE_CLIENT_KEY_MISMATCH");
   return new Material(WireValues.sha256(body),key,chain,trust,service);
  }catch(IOException failure){throw new IOException("SERVICE_CLIENT_CONFIGURATION_REJECTED");}finally{Arrays.fill(body,(byte)0);}
 }
 private static byte[] decode(String value){byte[] bytes=Base64.getDecoder().decode(value);if(!Base64.getEncoder().encodeToString(bytes).equals(value))throw new SecurityException("SERVICE_CLIENT_BASE64_REQUIRED");return bytes;}
 private final class PinnedTrust extends X509ExtendedTrustManager {
  private final Remote remote;private final String original;
  private PinnedTrust(Remote remote,String original){this.remote=remote;this.original=original;}
  private X509ExtendedTrustManager verify(X509Certificate[] chain,SSLSession handshake)throws CertificateException{
   try{var material=read(remote);if(handshake==null||!material.trust.protocols().contains(handshake.getProtocol())||!material.trust.ciphers().contains(handshake.getCipherSuite()))throw new SecurityException("SERVICE_TLS_POLICY_REQUIRED");if(!original.equals(material.fingerprint))throw new SecurityException("SERVICE_CLIENT_CONFIGURATION_CHANGED");var actual=new ServiceCertificateIdentity(()->material.trust).requireServer(chain);if(!remote.serviceUri.equals(actual.serviceUri())||!remote.certificateSha256.equals(actual.certificateSha256())||!remote.trustRevision.equals(actual.trustRevision()))throw new SecurityException("SERVICE_REMOTE_IDENTITY_MISMATCH");
    var anchors=new HashSet<TrustAnchor>();for(var authority:material.trust.authorities())anchors.add(new TrustAnchor(authority,null));var parameters=new PKIXBuilderParameters(anchors,new X509CertSelector());parameters.setRevocationEnabled(true);parameters.addCertStore(CertStore.getInstance("Collection",new CollectionCertStoreParameters(material.trust.revocations())));var revocation=(PKIXRevocationChecker)CertPathValidator.getInstance("PKIX").getRevocationChecker();revocation.setOptions(Set.of(PKIXRevocationChecker.Option.PREFER_CRLS,PKIXRevocationChecker.Option.NO_FALLBACK));parameters.addCertPathChecker(revocation);
    var factory=TrustManagerFactory.getInstance("PKIX");factory.init(new CertPathTrustManagerParameters(parameters));for(var manager:factory.getTrustManagers())if(manager instanceof X509ExtendedTrustManager extended)return extended;throw new SecurityException("SERVICE_EXTENDED_TRUST_REQUIRED");
   }catch(Exception failure){throw new CertificateException("SERVICE_PINNED_TLS_REJECTED");}
  }
  private void transport(SSLParameters parameters,String host,int port)throws CertificateException{if(!"HTTPS".equals(parameters.getEndpointIdentificationAlgorithm())||!Set.of("TLSv1.3").equals(new HashSet<>(Arrays.asList(parameters.getProtocols())))||host==null||!remote.origin.getHost().equalsIgnoreCase(host)||port!=(remote.origin.getPort()==-1?443:remote.origin.getPort()))throw new CertificateException("SERVICE_FIXED_TLS_TRANSPORT_REQUIRED");}
  @Override public void checkServerTrusted(X509Certificate[] chain,String auth,SSLEngine engine)throws CertificateException{if(engine==null)throw new CertificateException("SERVICE_TLS_ENGINE_REQUIRED");transport(engine.getSSLParameters(),engine.getPeerHost(),engine.getPeerPort());verify(chain,engine.getHandshakeSession()).checkServerTrusted(chain,auth,engine);}
  @Override public void checkServerTrusted(X509Certificate[] chain,String auth,Socket socket)throws CertificateException{if(!(socket instanceof SSLSocket tls)||tls.getHandshakeSession()==null)throw new CertificateException("SERVICE_TLS_SOCKET_REQUIRED");transport(tls.getSSLParameters(),tls.getHandshakeSession().getPeerHost(),tls.getPort());verify(chain,tls.getHandshakeSession()).checkServerTrusted(chain,auth,socket);}
  @Override public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("SERVICE_HOSTNAME_CONTEXT_REQUIRED");}
  @Override public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("OUTBOUND_CLIENT_CONTEXT_ONLY");}
  @Override public void checkClientTrusted(X509Certificate[] chain,String auth,Socket socket)throws CertificateException{throw new CertificateException("OUTBOUND_CLIENT_CONTEXT_ONLY");}
  @Override public void checkClientTrusted(X509Certificate[] chain,String auth,SSLEngine engine)throws CertificateException{throw new CertificateException("OUTBOUND_CLIENT_CONTEXT_ONLY");}
  @Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
 }
}
