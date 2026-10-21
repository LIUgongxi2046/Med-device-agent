package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mutual SSLEngine TLS with disposable test-only keys, no sockets/network,
 * external services, system trust imports or production identity registration. */
class ServiceClientTlsStoreTest {
 private static final JsonMapper JSON=JsonMapper.builder().build();
 @TempDir Path temporary;private Path files;private ObjectNode manifest,client,trust;private ServiceCertificateTrustStore trustStore;
 @BeforeEach void fixtures()throws Exception{
  Path root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();files=temporary.toRealPath().resolve("service-client-tls-test-fixtures");
  var process=new ProcessBuilder(System.getProperty("medical.test.python","python3"),root.resolve("scripts/prepare_service_client_tls_test_fixtures.py").toString(),"--output",files.toString()).redirectErrorStream(true).start();
  assertTrue(process.waitFor(30,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Ephemeral test fixture creation failed");
  manifest=(ObjectNode)JSON.readTree(Files.readAllBytes(files.resolve("manifest.json")));client=(ObjectNode)JSON.readTree(Files.readAllBytes(files.resolve("client-tls.json")));trust=(ObjectNode)JSON.readTree(Files.readAllBytes(files.resolve("trust.json")));trustStore=new ServiceCertificateTrustStore(files,Files.getOwner(files),1048576);assertDoesNotThrow(()->store().contexts(remote()),"Every negative test must first establish valid protected client material");
 }
 private ServiceClientTlsStore store()throws Exception{return new ServiceClientTlsStore(files,Files.getOwner(files),1048576,trustStore::read);}
 private ServiceClientTlsStore.Remote remote(){return new ServiceClientTlsStore.Remote(URI.create("https://localhost"),manifest.required("server_service_uri").textValue(),manifest.required("server_sha256").textValue(),manifest.required("trust_revision").textValue());}
 private void write(String name,ObjectNode value)throws Exception{Files.write(files.resolve(name),JSON.writeValueAsBytes(value));Files.setPosixFilePermissions(files.resolve(name),PosixFilePermissions.fromString("rw-------"));}
 @Test void actualMutualTlsHandshakeAndPooledPeerCurrentRevalidation()throws Exception{
  var source=store().contexts(remote());var first=source.create();assertNotSame(first,source.create(),"Each request needs a fresh session cache");
  var peer=handshake(first,"server","localhost");var identity=new ServiceCertificateIdentity(trustStore::read);assertEquals(remote().serviceUri(),identity.requireServer(peer).serviceUri());assertThrows(Exception.class,()->identity.require(peer),"Server-only EKU must not become client identity");
  trust.putArray("revocations").add(Base64.getEncoder().encodeToString(Files.readAllBytes(files.resolve("revoked.crl.der"))));write("trust.json",trust);
  assertThrows(Exception.class,()->identity.requireServer(peer),"Previously accepted session must recheck current CRL");assertThrows(Exception.class,()->handshake(source.create(),"server","localhost"));
 }
 @Test void wrongRegisteredCertificatePinRejectedBeforeApplicationData()throws Exception{
  var wrong=new ServiceClientTlsStore.Remote(remote().origin(),remote().serviceUri(),manifest.required("wrong_eku_sha256").textValue(),remote().trustRevision());
  assertThrows(Exception.class,()->handshake(store().context(wrong),"server","localhost"));
 }
 @Test void registeredButWrongServerIdentityRejected()throws Exception{assertThrows(Exception.class,()->handshake(store().context(remote()),"other-server","localhost"));}
 @Test void serverAuthRequiredEvenForRegisteredMatchingPin()throws Exception{
  var wrong=new ServiceClientTlsStore.Remote(remote().origin(),remote().serviceUri(),manifest.required("wrong_eku_sha256").textValue(),remote().trustRevision());assertThrows(Exception.class,()->handshake(store().context(wrong),"wrong-eku","localhost"));
 }
 @Test void hostnameMustMatchActualCertificate()throws Exception{
  var wrong=new ServiceClientTlsStore.Remote(URI.create("https://wrong.invalid"),remote().serviceUri(),remote().certificateSha256(),remote().trustRevision());assertThrows(Exception.class,()->handshake(store().context(wrong),"server","wrong.invalid"));
 }
 @Test void changedClientMaterialCannotReplacePinnedSource()throws Exception{
  var source=store().contexts(remote());write("client-tls.json",(ObjectNode)JSON.readTree(Files.readAllBytes(files.resolve("other-client.json"))));assertThrows(Exception.class,source::create);
 }
 @Test void mismatchedPrivateKeyRejected()throws Exception{
  client.set("private_key_pkcs8",JSON.readTree(Files.readAllBytes(files.resolve("other-client.json"))).required("private_key_pkcs8"));write("client-tls.json",client);assertThrows(Exception.class,()->store().context(remote()));
 }
 @Test void unknownAlgorithmFieldAndDuplicateInputFailClosed()throws Exception{
  for(String algorithm:List.of("","DSA","UNKNOWN")){var changed=client.deepCopy().put("key_algorithm",algorithm);write("client-tls.json",changed);assertThrows(Exception.class,()->store().context(remote()));}
  write("client-tls.json",client.deepCopy().put("unknown",true));assertThrows(Exception.class,()->store().context(remote()));Files.writeString(files.resolve("client-tls.json"),"{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}");assertThrows(Exception.class,()->store().context(remote()));
 }
 @Test void changedTrustRevisionAndExpiredCrlFailClosed()throws Exception{
  var source=store().contexts(remote());trust.put("revision",UUID.randomUUID().toString());write("trust.json",trust);assertThrows(Exception.class,source::create);
  trust.put("revision",remote().trustRevision());trust.putArray("revocations").add(Base64.getEncoder().encodeToString(Files.readAllBytes(files.resolve("expired.crl.der"))));write("trust.json",trust);assertThrows(Exception.class,source::create);
 }
 @Test void privateProtectedFileRequired()throws Exception{
  Files.setPosixFilePermissions(files.resolve("client-tls.json"),PosixFilePermissions.fromString("rw-r--r--"));assertThrows(Exception.class,()->store().context(remote()));
  Files.delete(files.resolve("client-tls.json"));Files.createSymbolicLink(files.resolve("client-tls.json"),files.resolve("other-client.json"));assertThrows(Exception.class,()->store().context(remote()));
 }
 private SSLContext server(String name)throws Exception{
  var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(files.resolve(name+".p12"))){keys.load(in,"test-only".toCharArray());}var key=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());key.init(keys,"test-only".toCharArray());
  var authorities=KeyStore.getInstance("PKCS12");authorities.load(null,null);try(var in=Files.newInputStream(files.resolve("ca.der"))){authorities.setCertificateEntry("ca",CertificateFactory.getInstance("X.509").generateCertificate(in));}var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(authorities);var context=SSLContext.getInstance("TLSv1.3");context.init(key.getKeyManagers(),trust.getTrustManagers(),null);return context;
 }
 private SSLSession handshake(SSLContext context,String serverName,String host)throws Exception{
  var client=context.createSSLEngine(host,443);client.setUseClientMode(true);var parameters=client.getSSLParameters();parameters.setProtocols(new String[]{"TLSv1.3"});parameters.setEndpointIdentificationAlgorithm("HTTPS");client.setSSLParameters(parameters);
  var server=server(serverName).createSSLEngine();server.setUseClientMode(false);server.setNeedClientAuth(true);server.setEnabledProtocols(new String[]{"TLSv1.3"});client.beginHandshake();server.beginHandshake();
  var toClient=ByteBuffer.allocate(262144);var toServer=ByteBuffer.allocate(262144);var plain=ByteBuffer.allocate(262144);var empty=ByteBuffer.allocate(0);
  for(int step=0;step<1000;step++){
   tasks(client);tasks(server);
   if(client.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP)check(client.wrap(empty,toServer));
   if(server.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP)check(server.wrap(empty,toClient));
   toClient.flip();while(toClient.hasRemaining()&&client.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_UNWRAP){var result=client.unwrap(toClient,plain);check(result);if(result.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)break;tasks(client);}toClient.compact();
   toServer.flip();while(toServer.hasRemaining()&&server.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_UNWRAP){var result=server.unwrap(toServer,plain);check(result);if(result.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)break;tasks(server);}toServer.compact();
   if(client.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING&&server.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING){assertEquals(0,plain.position(),"No HTTP/application content is sent in fixture");assertNotNull(server.getSession().getPeerPrincipal());return client.getSession();}
  }
  fail("Bounded in-memory TLS handshake did not finish");return null;
 }
 private static void tasks(SSLEngine engine){Runnable task;while((task=engine.getDelegatedTask())!=null)task.run();}
 private static void check(SSLEngineResult result)throws SSLException{if(result.getStatus()==SSLEngineResult.Status.CLOSED||result.getStatus()==SSLEngineResult.Status.BUFFER_OVERFLOW)throw new SSLException("UNIT_TEST_HANDSHAKE_FAILED");}
}
