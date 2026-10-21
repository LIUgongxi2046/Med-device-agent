package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

/** Real code exchange only. No client success flags, unsigned JWTs, or permissive provider adapter. */
public final class SigningOidcProvider {
 private final ClientRegistrationRepository registrations; private final SecretKeySpec key;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema(); private final SecureRandom random=new SecureRandom();
 public SigningOidcProvider(ClientRegistrationRepository registrations,String encryptionKey){this.registrations=Objects.requireNonNull(registrations);byte[] k=Base64.getDecoder().decode(encryptionKey);if(k.length!=32)throw new IllegalArgumentException("Signing PKCE encryption requires 256-bit key");key=new SecretKeySpec(k,"AES");Arrays.fill(k,(byte)0);}
 public record Start(String authorizationUrl,String stateHash,String nonceHash,String encryptedVerifier,String providerSha256){}
 public static final class Verified {
  private final JsonNode evidence;
  private Verified(JsonNode evidence){this.evidence=evidence.deepCopy();}
  JsonNode evidence(){return evidence.deepCopy();}
 }
 JsonNode configuration(String id)throws Exception{
  var r=registration(id);return json.tree(Map.of("registration_id",id,"issuer",r.getProviderDetails().getIssuerUri(),"client_id",r.getClientId(),"authorization_uri",r.getProviderDetails().getAuthorizationUri(),"token_uri",r.getProviderDetails().getTokenUri(),"jwk_set_uri",r.getProviderDetails().getJwkSetUri(),"redirect_uri",r.getRedirectUri(),"algorithm","RS256","client_authentication",r.getClientAuthenticationMethod().getValue()));
 }
 private ClientRegistration registration(String id){
  var r=registrations.findByRegistrationId(id);if(r==null||!AuthorizationGrantType.AUTHORIZATION_CODE.equals(r.getAuthorizationGrantType())||!r.getScopes().contains("openid")||!Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC,ClientAuthenticationMethod.NONE).contains(r.getClientAuthenticationMethod()))unconfigured();
  for(String uri:List.of(r.getProviderDetails().getIssuerUri(),r.getProviderDetails().getAuthorizationUri(),r.getProviderDetails().getTokenUri(),r.getProviderDetails().getJwkSetUri(),r.getRedirectUri()))https(uri);
  if(!"/api/signing/oidc/callback".equals(URI.create(r.getRedirectUri()).getPath())||URI.create(r.getRedirectUri()).getQuery()!=null)unconfigured();
  if(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(r.getClientAuthenticationMethod())&&(r.getClientSecret()==null||r.getClientSecret().isBlank()))unconfigured();return r;
 }
 Start begin(String registration,JsonNode policy,String challenge)throws Exception{
  var r=registration(registration);String state=secret(),nonce=secret(),verifier=secret();
  var values=new LinkedHashMap<String,String>();values.put("response_type","code");values.put("client_id",r.getClientId());values.put("redirect_uri",r.getRedirectUri());values.put("scope","openid");values.put("state",state);values.put("nonce",nonce);values.put("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))));values.put("code_challenge_method","S256");values.put("prompt","login");values.put("max_age","0");values.put("acr_values",policy.required("required_acr").asText());
  String uri=r.getProviderDetails().getAuthorizationUri();if(URI.create(uri).getQuery()!=null)unconfigured();return new Start(uri+"?"+form(values),hash(state),hash(nonce),encrypt(verifier,challenge),json.hash(configuration(registration)));
 }
 Verified exchange(String registration,JsonNode policy,String challenge,String encryptedVerifier,String code,String nonceHash,ApplicationSessionIdentity.Verified actor,Instant started,Instant now)throws Exception{
  if(code==null||code.isBlank()||code.length()>8192)denied();var r=registration(registration);var binding=actor.binding().view();if(!"oidc".equals(binding.mode())||!r.getProviderDetails().getIssuerUri().equals(binding.issuer()))denied();
  var values=new LinkedHashMap<String,String>();values.put("grant_type","authorization_code");values.put("code",code);values.put("redirect_uri",r.getRedirectUri());values.put("code_verifier",decrypt(encryptedVerifier,challenge));values.put("client_id",r.getClientId());
  var request=HttpRequest.newBuilder(URI.create(r.getProviderDetails().getTokenUri())).timeout(Duration.ofSeconds(15)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(form(values)));
  if(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(r.getClientAuthenticationMethod()))request.header("Authorization","Basic "+Base64.getEncoder().encodeToString((encode(r.getClientId())+":"+encode(r.getClientSecret())).getBytes(StandardCharsets.UTF_8)));
  // No retries: an uncertain exchange leaves this challenge unusable; the human starts a new challenge.
  var response=http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var in=response.body()){bytes=in.readNBytes(65537);}if(response.statusCode()!=200||bytes.length>65536)denied();JsonNode body=json.read(bytes);String raw=body.path("id_token").asText();if(raw.isBlank())denied();
  var decoder= NimbusJwtDecoder.withJwkSetUri(r.getProviderDetails().getJwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(r.getProviderDetails().getIssuerUri()));var token=decoder.decode(raw);
  if(!token.getAudience().contains(r.getClientId())||(token.getAudience().size()>1&&!r.getClientId().equals(token.getClaimAsString("azp")))||token.hasClaim("azp")&&!r.getClientId().equals(token.getClaimAsString("azp"))||!binding.subject().equals(token.getSubject())||!nonceHash.equals(hash(token.getClaimAsString("nonce"))))denied();
  if(token.getExpiresAt()==null||!token.getExpiresAt().isAfter(now)||token.getNotBefore()!=null&&token.getNotBefore().isAfter(now.plusSeconds(5)))denied();
  Instant auth=token.getClaimAsInstant("auth_time");if(auth==null||auth.isBefore(started.minusSeconds(5))||auth.isAfter(now.plusSeconds(5))||auth.plusSeconds(policy.required("maximum_auth_age_seconds").asInt()).isBefore(now)||token.getIssuedAt()==null||token.getIssuedAt().isBefore(started.minusSeconds(5))||token.getIssuedAt().isAfter(now.plusSeconds(5)))denied();
  if(!policy.required("required_acr").asText().equals(token.getClaimAsString("acr")))denied();var amr=token.getClaimAsStringList("amr");if(amr==null)denied();for(var required:policy.required("required_amr"))if(!amr.contains(required.asText()))denied();
  ObjectNode evidence=json.tree(Map.of("issuer",binding.issuer(),"subject_sha256",hash(binding.subject()),"audience",r.getClientId(),"nonce_sha256",nonceHash,"auth_time",auth.toString(),"acr",token.getClaimAsString("acr"),"amr",amr,"id_token_sha256",hash(raw),"provider_sha256",json.hash(configuration(registration)))).deepCopy();return new Verified(evidence);
 }
 private String encrypt(String plaintext,String aad)throws Exception{byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(aad.getBytes(StandardCharsets.US_ASCII));byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.US_ASCII));return Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(encrypted);}
 private String decrypt(String value,String aad)throws Exception{String[] pieces=value.split("\\.",-1);if(pieces.length!=2)denied();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(pieces[0])));cipher.updateAAD(aad.getBytes(StandardCharsets.US_ASCII));return new String(cipher.doFinal(Base64.getDecoder().decode(pieces[1])),StandardCharsets.US_ASCII);}
 private String secret(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static String hash(String s)throws Exception{if(s==null)denied();return WireValues.sha256(s.getBytes(StandardCharsets.UTF_8));}
 private static String form(Map<String,String> fields){return fields.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
 private static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 private static void https(String value){URI u=URI.create(value);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)unconfigured();}
 private static void unconfigured(){throw new TenantAccessException(ErrorCode.SIGNING_POLICY_UNCONFIGURED);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
