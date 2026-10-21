package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic documents test only the closed deterministic ABI, never live evidence authority. */
class TaskProfessionalPromptV2Test {
    static final JsonMapper json=new JsonMapper();
    static String id(int n){return String.format("10000000-0000-4000-8000-%012d",n);}
    static ObjectNode prompt(){return json.createObjectNode().put("schema_version","2.0.0").put("node_kind","N02").put("system_instructions","Use only registered evidence. ${literal}").put("input_encoding",TaskProfessionalPromptV2.ENCODING);}
    static ObjectNode bundle(){var b=json.createObjectNode();for(String f:List.of("retrieval_ids","question_coverage","selected_refs","conflict_refs","missing_requirement_ids","read_receipt_ids"))b.putArray(f);return b;}
    static ObjectNode pin(JsonNode b)throws Exception {return json.createObjectNode().put("step_id",id(1)).put("result_artifact_id",id(2)).put("result_sha256","a".repeat(64)).put("result_object_version","result-original").put("result_receipt_sha256","b".repeat(64)).put("evidence_artifact_id",id(3)).put("evidence_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(b))).put("evidence_object_version","evidence-original").put("evidence_receipt_sha256","c".repeat(64)).put("completion_receipt_sha256","d".repeat(64));}
    static ObjectNode input(JsonNode pin){var i=json.createObjectNode().put("schema_version","1.0.0").put("node_kind","N02").put("step_id",id(4)).put("tenant_id",id(5)).put("project_id",id(6)).put("task_id",id(7)).put("run_id",id(8));i.putArray("predecessors").add(pin.deepCopy());i.putArray("fixed_sources");return i;}
    static ArrayNode evidence(JsonNode pin,JsonNode b){var a=json.createArrayNode();a.addObject().set("predecessor",pin.deepCopy());((ObjectNode)a.get(0)).set("evidence_bundle",b.deepCopy());return a;}
    @Test void explicitV2IncludesLiteralInstructionsAndExactOrderedBundleBytes()throws Exception{
        var b=bundle();var p=pin(b);var i=input(p);var result=TaskProfessionalPromptV2.compile(prompt(),i,evidence(p,b));
        assertEquals("2.0.0",result.required("schema_version").textValue());assertEquals(5,result.size());assertEquals(i,result.required("task_node_input"));assertEquals(b,result.required("predecessor_evidence").get(0).required("evidence_bundle"));
        byte[] original=ContentReadAuthorization.canonicalBytes(result);i.put("injected",true);b.put("sdk_claim",true);assertArrayEquals(original,ContentReadAuthorization.canonicalBytes(result));
    }
    @Test void noImplicitUpgradeOrMixedProjection()throws Exception{
        var v1=new TaskProfessionalPromptCompilerTest();assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.validatePrompt(v1.prompt()));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptCompiler.validatePrompt(prompt()));
        var profile=(ObjectNode)v1.profile();profile.put("schema_version","2.0.0");assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.validateProfile(profile));profile.put("projection",TaskProfessionalPromptV2.PROJECTION);assertDoesNotThrow(()->TaskProfessionalPromptV2.validateProfile(profile));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptCompiler.validateProfile(profile));
    }
    @Test void omittedExtraSubstitutedOrReorderedPredecessorsAreDenied()throws Exception{
        var b=bundle();var p=pin(b);var i=input(p);var e=evidence(p,b);
        assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),i,json.createArrayNode()));
        var extra=e.deepCopy();extra.add(e.get(0));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),i,extra));
        ((ObjectNode)e.get(0).required("predecessor")).put("completion_receipt_sha256","e".repeat(64));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),i,e));
    }
    @Test void rawSdkClaimsUnknownFieldsAndChangedBundleAreDenied()throws Exception{
        var b=bundle();var p=pin(b);var i=input(p);var e=evidence(p,b);((ObjectNode)e.get(0)).put("sdk_verified",true);assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),i,e));
        var changed=evidence(p,b);((ObjectNode)changed.get(0).required("evidence_bundle")).withArray("read_receipt_ids").add(id(60));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),i,changed));
    }
    @Test void sameStepOrDuplicateArtifactIdentityNeverCountsAsPredecessor()throws Exception{
        var b=bundle();var p=pin(b);p.put("step_id",id(4));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.compile(prompt(),input(p),evidence(p,b)));
        p.put("step_id",id(1)).put("evidence_artifact_id",id(2));assertThrows(TenantAccessException.class,()->TaskProfessionalPromptV2.predecessor(p));
    }
    @Test void originCannotCrossTenantProjectTaskRunStepArtifactVersionReceiptOrActor()throws Exception{
        var b=bundle();var p=pin(b);var i=input(p);var r=json.createObjectNode();for(String f:List.of("tenant_id","project_id","task_id","run_id"))r.set(f,i.required(f));r.put("created_by",id(10)).put("kind","EVIDENCE_BUNDLE").put("step_id",id(1)).put("artifact_id",id(3));
        var o=new InterfaceProtocol.ObjectRef("tenants/"+id(5)+"/original", "evidence-original",p.required("evidence_sha256").textValue(),"2","application/json");
        assertDoesNotThrow(()->TaskProfessionalPredecessorEvidence.requireOriginal(i,id(10),p,r,o,"c".repeat(64)));
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","artifact_id","created_by")){var changed=r.deepCopy().put(f,id(99));assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireOriginal(i,id(10),p,changed,o,"c".repeat(64)),f);}
        assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireOriginal(i,id(10),p,r,o,"e".repeat(64)));
        var changed=new InterfaceProtocol.ObjectRef(o.objectKey(),"replacement-version",o.sha256(),o.sizeBytes(),o.mediaType());assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireOriginal(i,id(10),p,r,changed,"c".repeat(64)));
    }
    @Test void predecessorSourceRootsAndRightsMustEqualCurrentFixedClosure()throws Exception{
        var roots=json.createArrayNode().add(id(1));var members=json.createArrayNode().addObject().put("upstream_version_id",id(1)).put("rights_revision",id(2));var r=json.createObjectNode();r.set("content_roots",roots.deepCopy());r.putObject("content_manifest").set("members",json.createArrayNode().add(members.deepCopy()));var fixed=r.required("content_manifest").required("members");
        assertDoesNotThrow(()->TaskProfessionalPredecessorEvidence.requireFixedContent(r,roots,fixed));
        assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireFixedContent(r,json.createArrayNode().add(id(9)),fixed));
        var changed=fixed.deepCopy();((ObjectNode)changed.get(0)).put("rights_revision",id(9));assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireFixedContent(r,roots,changed));
    }
    @Test void selectedConflictAndCoverageReferencesCannotEscapeFixedSourceSelection()throws Exception{
        var b=bundle();var p=pin(b);var i=input(p);i.withArray("fixed_sources").addObject().put("source_version",id(10)).put("parse_version",id(11)).put("approval_revision",id(12));
        var ref=json.createObjectNode().put("tenant_id",id(5)).put("project_id",id(6)).put("source_version",id(10)).put("parse_version",id(11)).put("approval_revision",id(12));
        b.withArray("selected_refs").add(ref);assertDoesNotThrow(()->TaskProfessionalPredecessorEvidence.requireFixedReferences(b,i));
        for(String f:List.of("tenant_id","project_id","source_version","parse_version","approval_revision")){var changed=b.deepCopy();((ObjectNode)changed.required("selected_refs").get(0)).put(f,id(99));assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireFixedReferences(changed,i));}
        b.withArray("conflict_refs").add(ref.deepCopy().put("source_version",id(99)));assertThrows(TenantAccessException.class,()->TaskProfessionalPredecessorEvidence.requireFixedReferences(b,i));
    }
    @Test void physicalReadCapabilityHasNoPublicFactoryOrConstructor(){assertEquals(0,TaskProfessionalPredecessorEvidence.Reads.class.getConstructors().length);}
    @Test void unchangedV1CanonicalPayloadAndPublishedSchemaBytes()throws Exception{
        var v1=new TaskProfessionalPromptCompilerTest();var i=json.createObjectNode().put("node_kind","N02").put("question_text","Literal ${notInterpolation}");
        var bytes=ContentReadAuthorization.canonicalBytes(TaskProfessionalPromptCompiler.compileDocument(v1.prompt(),i));
        assertEquals("{\"instructions\":\"Quote exact authorized evidence. No missing facts may be invented.\",\"node_kind\":\"N02\",\"schema_version\":\"1.0.0\",\"task_node_input\":{\"node_kind\":\"N02\",\"question_text\":\"Literal ${notInterpolation}\"}}",new String(bytes,StandardCharsets.UTF_8));
        try(var in=getClass().getResourceAsStream("/task-professional-package.schema.json")){assertEquals("f4510bb51711d57e11aa0fb5405249ecc8e51437a21cafb644f89d7da690069e",WireValues.sha256(Objects.requireNonNull(in).readAllBytes()));}
    }
}
