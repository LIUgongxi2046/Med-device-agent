package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskProfessionalPackageAuthoringV2SchemaTest {
    final PermissionSchema json=new PermissionSchema();
    final TaskProfessionalPackageAuthoringSchema v1=new TaskProfessionalPackageAuthoringSchema(json),v2=v1.v2();
    TaskProfessionalPackageAuthoringV2SchemaTest()throws Exception{}
    static ObjectNode request(String kind)throws Exception{var n=new TaskProfessionalPackageAuthoringSchemaTest().request(kind);n.put("schema_version","2.0.0");if(!"ROLE".equals(kind))n.withObject("package").put("schema_version","2.0.0");if("PROMPT".equals(kind))n.withObject("package").put("input_encoding",TaskProfessionalPromptV2.ENCODING);if("PROFILE".equals(kind))n.withObject("package").put("projection",TaskProfessionalPromptV2.PROJECTION);return n;}
    @Test void explicitV2AuthoringUsesPublishedV2PromptProfileAndGenuineV1Role()throws Exception {
        for(String kind:List.of("PROMPT","PROFILE","ROLE")){var n=request(kind);assertEquals(n,v2.request(v2.bytes(n)));assertEquals("ROLE".equals(kind)?TaskProfessionalPromptCompiler.ROLE_REF:"PROMPT".equals(kind)?TaskProfessionalPromptV2.PROMPT_REF:TaskProfessionalPromptV2.PROFILE_REF,v2.ref(kind));assertEquals("ROLE".equals(kind)?"f4510bb51711d57e11aa0fb5405249ecc8e51437a21cafb644f89d7da690069e":"a559f6959098b6cac4b952d890e481a226dece72c428f4e40576d1bcebe98537",v2.packageHash(kind));assertThrows(SchemaRejection.class,()->v1.validateRequest(n));assertThrows(SchemaRejection.class,()->v2.validateRequest(new TaskProfessionalPackageAuthoringSchemaTest().request(kind)));}
        assertThrows(SchemaRejection.class,()->v2.packageHashForRef(TaskProfessionalPromptCompiler.PROMPT_REF));assertThrows(SchemaRejection.class,()->v2.packageHashForRef(TaskProfessionalPromptCompiler.PROFILE_REF));
    }
    @Test void callerCannotSelectProfileRefsRoutesOrGovernanceAndMixedPayloadsAreDenied()throws Exception {
        for(String field:List.of("schema_ref","authoring_profile","authoring_route","authoring_contract_sha256","approved","credentials","object_key")){var n=request("PROMPT");n.put(field,"caller");assertThrows(SchemaRejection.class,()->v2.validateRequest(n));}
        for(String kind:List.of("PROMPT","PROFILE")){var n=request(kind);n.withObject("package").put("schema_version","1.0.0");assertThrows(SchemaRejection.class,()->v2.validateRequest(n));}
        var role=request("ROLE");role.withObject("package").put("schema_version","2.0.0");assertThrows(SchemaRejection.class,()->v2.validateRequest(role));
        var wrong=request("PROMPT");wrong.withObject("package").put("input_encoding","TASK_NODE_INPUT_CANONICAL_JSON");assertThrows(SchemaRejection.class,()->v2.validateRequest(wrong));
    }
    @Test void sharedHttpEnvelopeAndFrozenV1ContractRemainExactlyVersionOne()throws Exception {
        assertEquals("4e1904ea23571d016a344ce4ef2212c202e8a4fad078cc7fcd305ec1d9b11e19",v1.manifest());assertEquals("1.0.0",v1.version());assertEquals(TaskProfessionalPackageAuthoringProfile.V1,v1.profile());
        var envelope=v2.envelope(json.object().put("schema_version","2.0.0"),"1");assertEquals("1.0.0",envelope.required("schema_version").textValue());SchemaCatalogue.fromClasspath().validate("interfaces",envelope,"Envelope");assertEquals("2.0.0",envelope.required("data").required("schema_version").textValue());
        var original=json.object();v1.bindProfile(original,TaskProfessionalPackageAuthoringSchemaTest.id(2));assertTrue(original.isEmpty());
    }
    @Test void v2OriginalProfileRouteAndContractDigestCannotBeSubstituted()throws Exception {
        String project=TaskProfessionalPackageAuthoringSchemaTest.id(2);var original=json.object();v2.bindProfile(original,project);v2.requireProfile(original,project);assertThrows(TenantAccessException.class,()->v1.requireProfile(original,project));
        for(String field:List.of("authoring_profile","authoring_route","authoring_contract_sha256","proof_operation")){var changed=original.deepCopy().put(field,"wrong");assertThrows(TenantAccessException.class,()->v2.requireProfile(changed,project),field);}
        assertThrows(TenantAccessException.class,()->v2.requireProfile(json.object(),project));
    }
    @Test void unchangedV1SafeguardsAlsoApplyToV2Content()throws Exception {
        var unsafe=request("PROFILE");((ObjectNode)unsafe.at("/package/files/0")).put("relative","profiles/../escape");assertThrows(SchemaRejection.class,()->v2.validateRequest(unsafe));var blank=request("PROMPT");blank.withObject("package").put("system_instructions"," \n\t");assertThrows(SchemaRejection.class,()->v2.validateRequest(blank));String wire=v2.canonical(request("PROMPT"));assertThrows(SchemaRejection.class,()->v2.request((wire+" {}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
