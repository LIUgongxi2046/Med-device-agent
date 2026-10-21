package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic input integrity tests, not publication, permission or database evidence. */
class RuntimeReplayAssemblerTest {
    private final PermissionSchema json=new PermissionSchema();
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
    RuntimeReplayAssemblerTest() throws Exception { }
    private JsonNode fixture() throws Exception {return reader.readTree(RuntimeReplaySchemaTest.fixture());}
    private ObjectNode assemble(JsonNode v) throws Exception {
        var request=new RunCreateSchema.Create("1.0.0",v.required("task_revision").asText(),v.required("plan_revision").asText(),v.required("snapshot_id").asText(),v.required("budget_revision").asText(),"1");
        var models=new ArrayList<JsonNode>();v.required("model_bindings").forEach(models::add);
        var versions=new ArrayList<ArtifactRegistryProtocol.VersionRow>();
        for(var row:v.required("dependency_versions"))versions.add(new ArtifactRegistryProtocol.VersionRow(row.required("version_id").asText(),row.required("project_id").isNull()?null:row.required("project_id").asText(),row.required("entity_kind").asText(),row.required("content_sha256").asText()));
        var edges=new ArrayList<SourceReadProtocol.ParentRow>();for(var row:v.required("dependency_edges"))edges.add(reader.treeToValue(row,SourceReadProtocol.ParentRow.class));
        var auth=v.required("authorizations");
        return RuntimeReplayAssembler.compose(json,v.required("tenant_id").asText(),v.required("project_id").asText(),v.required("initiator_id").asText(),v.required("task_id").asText(),v.required("run_id").asText(),v.required("principal_id").asText(),request,
                reader.treeToValue(v.required("task_input"),TaskProtocol.Create.class),v.required("plan"),v.required("scope_snapshot"),v.required("agent_release"),v.required("agent_release_sha256").asText(),v.required("publication_approval_sha256").asText(),v.required("execution_policy"),reader.treeToValue(v.required("execution_policy_version"),PermissionProtocol.Basis.class),v.required("project_profile"),v.required("template"),v.required("budget_approval"),models,versions,edges,v.required("scope_manifest"),v.required("principal_manifest"),v.required("retention"),auth.required("context_revision").asText(),auth.required("auth_epoch").asText(),auth.required("governance_epoch").asText(),reader.readTree(auth.required("canonical_witnesses").asText()));
    }
    private RuntimeReplaySchema.VerifiedInputs validate(JsonNode value) throws Exception {
        byte[] raw=json.canonical(value).getBytes(StandardCharsets.UTF_8);
        return new RuntimeReplaySchema(json).read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest"));
    }
    @Test void reconstructsCompleteSyntheticAdmissionAndRetainsOnlyActualInputFields() throws Exception {
        var original=fixture();var result=assemble(original);validate(result);assertEquals(original,result);
        assertEquals("NOT_YET_PRODUCED",result.required("future_execution_records").asText());assertEquals(0,result.required("prior_inputs").size());assertFalse(result.has("started_at"));assertFalse(result.has("result"));
    }
    @Test void mutableInputNodesCannotChangeAssembledManifest() throws Exception {
        var original=fixture();var result=assemble(original);String before=json.canonical(result);
        ((ObjectNode)original.required("execution_policy")).put("locale","changed");((ObjectNode)original.required("principal_manifest")).put("principal_id","changed");
        assertEquals(before,json.canonical(result));validate(result);
    }
    @Test void unknownProviderVersionIsReportedNotInvented() throws Exception {
        var original=fixture();((ObjectNode)original.required("model_bindings").get(0)).put("model_version","UNKNOWN");var result=assemble(original);validate(result);
        assertEquals("ORIGINAL_VERSION_UNAVAILABLE",result.required("provider_reexecution").asText());
    }
    @Test void originalDocumentCannotBeSilentlyOmittedFromPriorInputs() throws Exception {
        var original=fixture();((ObjectNode)original.required("task_input")).put("based_on_document_revision","00000000-0000-4000-8000-999999999999");
        var input=reader.treeToValue(original.required("task_input"),TaskProtocol.Create.class);
        // build() applies this gate whenever no private signed-continuation proof exists.
        // compose() only assembles structure, shared with the separately verified signed path.
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,
                ()->RuntimeReplayAssembler.requireFirstRunInputs(input)).code());
    }
    @Test void firstRunWithoutOriginalDocumentPassesTheUnsignedAdmissionGate() throws Exception {
        var input=reader.treeToValue(fixture().required("task_input"),TaskProtocol.Create.class);
        assertNull(input.based_on_document_revision());
        assertDoesNotThrow(()->RuntimeReplayAssembler.requireFirstRunInputs(input));
    }
    @Test void recomputedOuterDigestCannotHideMismatchedTemplate() throws Exception {
        var original=fixture();((ObjectNode)original.required("template")).put("template_version","00000000-0000-4000-8000-999999999999");var assembled=assemble(original);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->validate(assembled)).code());
    }
    @Test void registryClosureIsPreservedAndMissingPlanIsRejected() throws Exception {
        var original=fixture();var dependencies=(com.fasterxml.jackson.databind.node.ArrayNode)original.required("dependency_versions");
        for(int i=0;i<dependencies.size();i++)if(dependencies.get(i).required("version_id").equals(original.required("plan_revision"))){dependencies.remove(i);break;}
        var assembled=assemble(original);assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->validate(assembled)).code());
    }
    @Test void modelProofsAreDeduplicatedOnlyWhenIdenticalAndDefensivelyCopied() throws Exception {
        var binding=fixture().required("model_bindings").get(0);var authorizations=json.object();var runtime=authorizations.putObject("dependencies").putArray("runtime");
        var one=runtime.addObject().put("model_revision",binding.required("model_config_revision").asText());one.set("model_binding",binding.deepCopy());runtime.add(one.deepCopy());
        var result=RuntimeReplayAssembler.modelBindings(authorizations);assertEquals(1,result.size());((ObjectNode)one.required("model_binding")).put("model_version","CHANGED");assertNotEquals("CHANGED",result.get(0).required("model_version").asText());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.modelBindings(authorizations)).code());
    }
    @Test void missingRealModelEligibilityCannotYieldEmptyModelList() {
        var authorizations=json.object();authorizations.putObject("dependencies").putArray("runtime");assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.modelBindings(authorizations)).code());
    }
    @Test void substitutedModelIdentityFailsBeforeAssembly() throws Exception {
        var binding=fixture().required("model_bindings").get(0);var authorizations=json.object();var node=authorizations.putObject("dependencies").putArray("runtime").addObject();node.put("model_revision","00000000-0000-4000-8000-999999999999");node.set("model_binding",binding);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.modelBindings(authorizations)).code());
    }
    @Test void genuineExtraDependencyPinsMustMatchActualRegistryClosure() {
        String id="00000000-0000-4000-8000-999999999999",hash="a".repeat(64);
        var pin=new PermissionProtocol.Basis(id,hash);
        var row=new ArtifactRegistryProtocol.VersionRow(id,null,"TASK_DELEGATION_RULE",hash);
        assertDoesNotThrow(()->RuntimeReplayAssembler.requireExtraDependencies(List.of(pin),List.of(row)));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.requireExtraDependencies(List.of(pin),List.of())).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.requireExtraDependencies(List.of(new PermissionProtocol.Basis(id,"b".repeat(64))),List.of(row))).code());
    }
    @Test void extraDependencyListCannotDuplicateOrContradictVersionIdentity() {
        String id="00000000-0000-4000-8000-999999999999",hash="a".repeat(64);
        var pin=new PermissionProtocol.Basis(id,hash);var row=new ArtifactRegistryProtocol.VersionRow(id,null,"TASK_DELEGATION_RULE",hash);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.requireExtraDependencies(List.of(pin,pin),List.of(row))).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->RuntimeReplayAssembler.requireExtraDependencies(List.of(pin),List.of(row,row))).code());
    }
}
