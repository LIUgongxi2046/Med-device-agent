package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeReplaySchemaTest {
    static byte[] fixture() throws java.io.IOException {
        try (var input = java.util.Objects.requireNonNull(RuntimeReplaySchemaTest.class.getResourceAsStream("/fixtures/runtime-replay.synthetic.json"))) {
            return input.readAllBytes();
        }
    }
    static SchemaCatalogue catalogue;
    static RuntimeReplaySchema schema;
    @BeforeAll static void load() throws Exception {
        catalogue=SchemaCatalogue.fromProject(Path.of(System.getProperty("medical.projectRoot", System.getProperty("simulation.bridge.project", "../.."))));
        schema=new RuntimeReplaySchema(new PermissionSchema(),catalogue);
    }
    @Test void productionConstructorLoadsPinnedClasspathExtension() throws Exception {
        byte[] raw=fixture();
        var reader=new RuntimeReplaySchema(new PermissionSchema());
        var proof=reader.read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest"));
        assertEquals(proof.runId(),proof.content().required("run_id").textValue());
    }
    @Test void completeSyntheticInputProvidesOnlyFixedBytesProofAndDefensiveViews() throws Exception {
        byte[] raw=fixture();
        var proof=schema.read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest"));
        assertEquals(proof.runId(),proof.content().required("run_id").textValue());
        assertFalse(proof.plan().sections().isEmpty());
        ((com.fasterxml.jackson.databind.node.ObjectNode)proof.content()).put("run_id","changed");
        assertNotEquals("changed",proof.runId());
        assertEquals("RUN_ADMISSION",proof.content().required("phase").textValue());
    }
    @Test void matchingRawDigestCannotHideInnerPlanIdentityMutation() throws Exception {
        var json=new PermissionSchema();var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var raw=mapper.readTree(fixture());
        ((com.fasterxml.jackson.databind.node.ObjectNode)raw.required("plan")).put("task_id","00000000-0000-4000-8000-999999999999");
        byte[] changed=json.canonical(raw).getBytes(StandardCharsets.UTF_8);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->schema.read(changed,DigestUtils.sha256Hex(changed),catalogue.schemaDigest("replay-manifest"))).code());
    }
    @Test void matchingRawDigestCannotHideRegistryPlanHashMutation() throws Exception {
        rejectMutation(value -> {
            for(var dependency:value.required("dependency_versions")) {
                if(dependency.required("version_id").equals(value.required("plan_revision")))
                    ((com.fasterxml.jackson.databind.node.ObjectNode)dependency).put("content_sha256","f".repeat(64));
            }
        });
    }
    @Test void principalCannotAddPermissionOutsideActualScope() throws Exception {
        rejectMutation(value -> ((com.fasterxml.jackson.databind.node.ObjectNode)value.required("principal_manifest")
                .required("permissions").get(0).required("scope")).put("resource_id","00000000-0000-4000-8000-999999999999"));
    }
    @Test void principalCannotSubstituteUnapprovedWorkerAudience() throws Exception {
        rejectMutation(value -> ((com.fasterxml.jackson.databind.node.ObjectNode)value.required("principal_manifest")
                .required("services").get(0)).put("audience","unapproved-worker"));
    }
    private void rejectMutation(java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> mutate) throws Exception {
        var json=new PermissionSchema();var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var value=mapper.readTree(fixture());
        mutate.accept(value);byte[] raw=json.canonical(value).getBytes(StandardCharsets.UTF_8);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
                ()->schema.read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest"))).code());
    }
    @Test void rawDigestIsCheckedBeforeAnySchemaOrCanonicalization() {
        byte[] raw=" { } ".getBytes(StandardCharsets.UTF_8);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
                ()->schema.read(raw,DigestUtils.sha256Hex("{}"),catalogue.schemaDigest("replay-manifest"))).code());
    }
    @Test void unknownSchemaDigestCannotBeAcceptedWithCorrectObjectDigest() {
        byte[] raw="{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SchemaRejection.class,()->schema.read(raw,DigestUtils.sha256Hex(raw),"f".repeat(64)));
    }
    @Test void malformedUtf8IsRejectedDespiteMatchingRawDigest() {
        byte[] raw={(byte)0xc3,(byte)0x28};
        assertThrows(java.nio.charset.CharacterCodingException.class,
                ()->schema.read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest")));
    }
    @Test void emptyObjectWithValidDigestCannotYieldFixedInputProof() {
        byte[] raw="{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SchemaRejection.class,()->schema.read(raw,DigestUtils.sha256Hex(raw),catalogue.schemaDigest("replay-manifest")));
    }
}
