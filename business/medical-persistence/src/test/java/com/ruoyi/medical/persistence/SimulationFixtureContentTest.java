package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationFixtureContentTest {
    private final PermissionSchema json=new PermissionSchema();
    private final SchemaCatalogue schemas=SchemaCatalogue.fromClasspath();
    SimulationFixtureContentTest() throws Exception { }
    private record Fixture(ObjectNode configuration,ObjectNode sample,ObjectNode input,ObjectNode parameters) { }
    private Fixture fixture() throws Exception {
        ObjectNode configuration=json.object(); configuration.set("definition",json.tree(SimulationModelConfigurationFixtures.definition()));
        var input=json.object(); input.putArray("messages").addObject().put("role","user").put("content","SIMULATION 测试 🧪"); input.putArray("tools");
        var parameters=json.object(); parameters.put("max_output_tokens",32); parameters.putNull("temperature");
        parameters.put("reasoning_effort","none"); parameters.putArray("stop"); parameters.put("purpose","EVALUATION");
        var sample=json.object(); sample.put("schema_version","1.0.0"); sample.put("input_data_kind","SIMULATION");
        sample.put("case_id",SimulationModelConfigurationFixtures.CASE); sample.put("receipt_id","7aa52a97-f346-4421-aa3b-3dba36fd4e2c");
        sample.put("settings_sha256",hash(SimulationFixtureContent.settings(configuration)));
        sample.put("input_sha256",hash(input)); sample.put("parameters_sha256",hash(parameters));
        sample.putArray("blocks").addObject().put("type","text").put("text","Local fixture");
        sample.putObject("reason").put("kind","stop").putNull("failure"); sample.putNull("usage");
        ((ObjectNode)configuration.at("/definition/case_binding")).put("case_sha256",hash(sample));
        return new Fixture(configuration,sample,input,parameters);
    }
    private void verify(Fixture f) throws Exception { SimulationFixtureContent.verify(schemas,f.configuration(),f.sample(),f.input(),f.parameters()); }
    private void rebind(Fixture f) throws Exception {
        f.sample().put("input_sha256",hash(f.input())); f.sample().put("parameters_sha256",hash(f.parameters()));
        ((ObjectNode)f.configuration().at("/definition/case_binding")).put("case_sha256",hash(f.sample()));
    }
    @Test void validCanonicalFixtureIsOnlyContentValidation() throws Exception { verify(fixture()); }
    @Test void changedInputEvenWithValidShapeIsRejected() throws Exception {
        var f=fixture(); ((ObjectNode)f.input().at("/messages/0")).put("content","tampered"); assertThrows(Exception.class,()->verify(f));
    }
    @Test void declaredOnlyCaseHashCannotReplaceFullBytes() throws Exception {
        var f=fixture(); ((ObjectNode)f.sample().at("/blocks/0")).put("text","changed"); assertThrows(Exception.class,()->verify(f));
    }
    @Test void fixedConfigurationSettingsAreNotCallerAssertions() throws Exception {
        var f=fixture(); ((ObjectNode)f.configuration().at("/definition/limits")).put("deadline_seconds",61); assertThrows(Exception.class,()->verify(f));
    }
    @Test void matchingHashesDoNotPermitUnknownToolHistory() throws Exception {
        var f=fixture(); f.input().withArray("messages").addObject().put("role","tool").put("content","orphan").put("tool_call_id","missing");
        rebind(f); assertThrows(Exception.class,()->verify(f));
    }
    @Test void matchingHashesDoNotPermitAbortedFixture() throws Exception {
        var f=fixture(); ((ObjectNode)f.sample().required("reason")).put("kind","aborted"); rebind(f); assertThrows(Exception.class,()->verify(f));
    }
    @Test void parametersCannotExceedFixedTokenLimitEvenWithMatchingHashes() throws Exception {
        var f=fixture(); f.parameters().put("max_output_tokens",8001); rebind(f); assertThrows(Exception.class,()->verify(f));
    }
    @Test void tokenLimitIsNeverComparedWithUtf8ByteUnits() throws Exception {
        var f=fixture(); f.parameters().put("max_output_tokens",1); rebind(f); verify(f);
        assertTrue(ContentReadAuthorization.canonicalBytes(f.input()).length>1);
    }
    @Test void provenanceSchemaDigestMustBePublished() throws Exception {
        var f=fixture(); ((ObjectNode)f.configuration().required("definition")).put("provenance_schema_sha256","0".repeat(64));
        assertThrows(Exception.class,()->verify(f));
    }
    @Test void readProofHasNoPublicOrPackageConstructor() {
        for(var constructor:SimulationInvocationSources.VerifiedSources.class.getDeclaredConstructors()) assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(Modifier.isFinal(SimulationInvocationSources.VerifiedSources.class.getModifiers()));
    }
    private static String hash(JsonNode node) throws Exception { return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node)); }
}
