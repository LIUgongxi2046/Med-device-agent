package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.node.ObjectNode;import com.ruoyi.medical.contracts.*;import java.math.BigInteger;import java.nio.charset.StandardCharsets;
/** Actual OpenAI SDK/httpx2 MockTransport output; only the independent original mTLS outcome authority is mocked. */
class FormalActualSdkCaptureTest {
 FormalInvocationSchema schema;FormalProviderCaptureEvidence verifier;FormalInvocationOutcomeAuthority.VerifiedOutcome outcome;ObjectNode artifact,result;
 byte[] resource(String name)throws Exception{try(var in=getClass().getResourceAsStream("/fixtures/provider-capture/"+name)){assertNotNull(in);return in.readAllBytes();}}
 @BeforeEach void setup()throws Exception{
  schema=new FormalInvocationSchema();verifier=new FormalProviderCaptureEvidence(schema,16384,8192);
  var manifest=schema.parse(new String(resource("manifest.json"),StandardCharsets.UTF_8));assertFalse(manifest.required("external_network_sent").booleanValue());
  var files=manifest.required("files").fields();while(files.hasNext()){var f=files.next();assertEquals(f.getValue().textValue(),WireValues.sha256(resource(f.getKey())));}
  artifact=(ObjectNode)schema.parse(new String(resource("actual-sdk-capture.json"),StandardCharsets.UTF_8));result=(ObjectNode)schema.parse(new String(resource("actual-sdk-model-result.json"),StandardCharsets.UTF_8));
  var approved=schema.parse(new String(resource("approved-provider-body.json"),StandardCharsets.UTF_8));var m=artifact.required("metadata");assertEquals(schema.hash(approved),m.required("canonical_request_body_sha256").textValue());assertEquals(WireValues.sha256(resource("actual-sdk-request-body.json")),m.required("request_body_sha256").textValue());
  outcome=mock(FormalInvocationOutcomeAuthority.VerifiedOutcome.class);when(outcome.invocationId()).thenReturn(m.required("invocation_id").textValue());when(outcome.attemptId()).thenReturn(m.required("dispatch_attempt_id").textValue());when(outcome.result()).thenAnswer(x->result.deepCopy());
  ObjectNode binding=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();var send=binding.putObject("actual_send_manifest");send.put("canonical_provider_body_sha256",schema.hash(approved));send.set("provider_body",approved);when(outcome.binding()).thenReturn(binding);
 }
 @Test void actualSdkCrLfEntityAndNormalizedUsageVerify()throws Exception{var verified=verifier.require(outcome,ContentReadAuthorization.canonicalBytes(artifact));assertTrue(verified.providerTerminal());assertEquals(BigInteger.valueOf(5),verified.usage().reportedTotalTokens());assertEquals(BigInteger.valueOf(3),verified.usage().uncachedInputTokens());assertEquals(BigInteger.ONE,verified.confirmedCalls());}
 @Test void actualSdkArtifactCannotBindDifferentApprovedRequest(){((ObjectNode)artifact.required("metadata")).put("canonical_request_body_sha256","a".repeat(64));assertThrows(TenantAccessException.class,()->verifier.require(outcome,ContentReadAuthorization.canonicalBytes(artifact)));}
 @Test void actualSdkUsageCannotBeReplacedBySelfConsistentNormalizedTotal(){((ObjectNode)result.required("usage")).put("uncached_input_tokens",99).put("total_tokens",101);assertThrows(TenantAccessException.class,()->verifier.require(outcome,ContentReadAuthorization.canonicalBytes(artifact)));}
}
