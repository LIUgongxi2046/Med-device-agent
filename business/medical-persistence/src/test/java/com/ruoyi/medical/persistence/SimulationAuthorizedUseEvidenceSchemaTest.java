package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationAuthorizedUseEvidenceSchemaTest {
    private final PermissionSchema json=new PermissionSchema();
    private final EvaluationSchema evaluations=new EvaluationSchema(json);
    private final SimulationAuthorizedUseEvidenceSchema schema=new SimulationAuthorizedUseEvidenceSchema(json,evaluations);
    SimulationAuthorizedUseEvidenceSchemaTest()throws Exception { }
    private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    private PermissionProtocol.Basis ref(int n,String hash){return new PermissionProtocol.Basis(id(n),hash);}
    private ObjectNode specification()throws Exception {
        var sample=new EvaluationProtocol.Sample(id(1),id(2),"ACCEPTANCE","SIM_INPUT","SIMULATION",ref(3,"a".repeat(64)),ref(4,"b".repeat(64)),List.of());
        sample=new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),ref(4,evaluations.hash(evaluations.expectedItems(sample))),sample.expectations());
        var samples=json.tree(List.of(sample));var spec=json.object().put("schema_version","1.0.0");
        spec.set("dataset",json.tree(ref(5,"c".repeat(64))));spec.set("sample_manifest_artifact",json.tree(ref(6,schema.hash(samples))));spec.set("samples",samples);return spec;
    }
    @Test void bindsExactSampleManifestAndExpectedItems()throws Exception {
        var spec=specification();assertEquals(1,schema.samples(spec).size());
        ((ObjectNode)spec.required("samples").get(0)).put("document_type","changed");
        assertThrows(TenantAccessException.class,()->schema.samples(spec));
    }
    @Test void refusesNonSimAndUserScopeFields()throws Exception {
        var spec=specification();((ObjectNode)spec.required("samples").get(0)).put("evidence_class","REAL");
        assertThrows(TenantAccessException.class,()->schema.samples(spec));
        var scoped=specification().put("project_id",id(9));assertThrows(TenantAccessException.class,()->schema.samples(scoped));
    }
    @Test void duplicateKeysAndNonCanonicalBytesFail()throws Exception {
        assertThrows(Exception.class,()->schema.readCanonical("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        var document=document();var canonical=json.canonical(document);assertEquals(document,schema.readCanonical(canonical.getBytes(StandardCharsets.UTF_8)));
        assertThrows(TenantAccessException.class,()->schema.readCanonical((canonical+"\n").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void fixedPurposeAndNoPrivateObjectFields()throws Exception {
        var doc=document();schema.validate("Document",doc);doc.put("purpose","SOURCE_RIGHTS_REVIEW");
        assertThrows(TenantAccessException.class,()->schema.validate("Document",doc));
        var privateDoc=document().put("object_key","private/path");assertThrows(TenantAccessException.class,()->schema.validate("Document",privateDoc));
        var privateEvidence=document();((ObjectNode)privateEvidence.required("sources").get(0).required("approval")).putArray("evidence").add(id(13));
        assertThrows(TenantAccessException.class,()->schema.validate("Document",privateEvidence));
        var privateSources=document();privateSources.putArray("evidence_sources");assertThrows(TenantAccessException.class,()->schema.validate("Document",privateSources));
    }
    private ObjectNode document()throws Exception {
        var doc=json.object().put("schema_version","1.0.0").put("tenant_id",id(10)).putNull("project_id").put("purpose","SIMULATION_EVALUATION").put("production_eligible",false);
        doc.set("specification",specification());doc.putArray("covered_versions").add(json.tree(ref(3,"a".repeat(64))));
        var source=doc.putArray("sources").addObject();source.set("source",json.tree(ref(11,"d".repeat(64))));source.set("rights",json.tree(ref(12,"e".repeat(64))));source.put("object_ref_sha256","f".repeat(64));var approval=source.putObject("approval").put("decision_id",id(14));
        approval.set("rights",json.tree(ref(12,"e".repeat(64))));approval.put("witness_sha256","c".repeat(64)).put("evidence_count","1");return doc;
    }
}
