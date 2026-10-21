package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic integrity fixtures only; no publication, storage or Temporal proof is manufactured. */
class TaskRunBootstrapSchemaTest {
    private final TaskRunBootstrapSchema schema=new TaskRunBootstrapSchema();
    private final PermissionSchema json=new PermissionSchema();
    TaskRunBootstrapSchemaTest()throws Exception{}
    static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    ObjectNode identity(){return json.object().put("schema_version","1.0.0").put("tenant_id",id(1))
        .put("project_id",id(2)).put("run_id",id(3)).put("principal_id",id(4)).put("task_id",id(5))
        .put("initiator_id",id(6)).put("command_id",id(7));}
    ObjectNode intent()throws Exception{
        var n=identity().put("outbox_event_id",id(8)).put("command_sha256","a".repeat(64))
            .put("write_id",id(9)).put("planned_artifact_id",id(10))
            .put("write_key",DigestUtils.sha256Hex((id(3)+"/RUN/REPLAY_MANIFEST").getBytes(StandardCharsets.US_ASCII)))
            .put("replay_schema_ref",RuntimeReplaySchema.REF).put("replay_schema_sha256","b".repeat(64))
            .put("canonical_payload","{}").put("replay_sha256",DigestUtils.sha256Hex("{}"))
            .put("size_bytes","2").put("object_key","runtime/"+id(1)+"/"+id(3)+"/"+id(9)+"/"+id(10))
            .put("media_type","application/json").put("release_id",id(11)).put("publication_approval_sha256","c".repeat(64))
            .put("authority_sha256","d".repeat(64)).put("classification","INTERNAL").put("created_at","2026-10-19T00:00:00Z");
        n.set("execution_policy",json.object().put("version_id",id(12)).put("content_sha256","e".repeat(64)));return n;
    }
    ObjectNode registration(ObjectNode intent)throws Exception{
        var n=identity().put("write_id",id(9)).put("artifact_id",id(10)).put("intent_sha256",schema.hash(intent))
            .put("replay_schema_ref",RuntimeReplaySchema.REF).put("replay_schema_sha256","b".repeat(64))
            .put("registered_at","2026-10-19T00:00:01Z").put("command_sha256",intent.required("command_sha256").asText());
        n.set("execution_policy",intent.required("execution_policy").deepCopy());
        n.put("authority_sha256",intent.required("authority_sha256").asText()).put("classification","INTERNAL").put("governance_sha256","f".repeat(64));
        n.set("content_roots",json.tree(java.util.List.of()));n.set("content_manifest",json.tree(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(2),id(15),java.util.List.of())));
        n.set("object_ref",json.object().put("object_key",intent.required("object_key").asText())
            .put("object_version","actual-version-fixture").put("sha256",intent.required("replay_sha256").asText())
            .put("size_bytes","2").put("media_type","application/json"));return n;
    }
    ObjectNode launch(ObjectNode receipt)throws Exception{
        var ref=json.object().put("schema_version","1.0.0").put("tenant_id",id(1)).put("project_id",id(2))
            .put("run_id",id(3)).put("execution_kind","TASK").put("input_manifest_id",id(10))
            .put("input_sha256",DigestUtils.sha256Hex("{}")).put("policy_revision",id(12))
            .put("deadline_epoch_millis",1800000000000L).put("maximum_commands",10).put("maximum_steps",3)
            .put("maximum_parallel_steps",2).put("read_timeout_millis",1000).put("step_timeout_millis",2000)
            .put("schedule_timeout_millis",3000).put("control_task_queue","synthetic-control")
            .put("professional_task_queue","synthetic-professional");
        var n=identity().put("launch_id",id(13)).put("command_sha256","a".repeat(64))
            .put("replay_registration_sha256",schema.hash(receipt)).put("reference_sha256",schema.hash(ref))
            .put("workflow_id",id(1)+"/"+id(3)).put("temporal_namespace","synthetic-namespace").put("service_uri","spiffe://synthetic/worker")
            .put("operational_revision",id(16)).put("operational_sha256","f".repeat(64)).put("created_at","2026-10-19T00:00:02Z");n.set("reference",ref);return n;
    }
    ObjectNode acceptance(ObjectNode launch)throws Exception{
        return identity().put("launch_id",id(13)).put("launch_intent_sha256",schema.hash(launch))
            .put("workflow_id",id(1)+"/"+id(3)).put("temporal_run_id",id(14))
            .put("reference_sha256",launch.required("reference_sha256").asText())
            .put("input_sha256",DigestUtils.sha256Hex("{}")).put("accepted_at","2026-10-19T00:00:03Z");
    }
    @Test void closedSyntheticIntegrityChainIsAcceptedWithoutCreatingAuthority()throws Exception{
        var i=intent();var r=registration(i);var l=launch(r);var a=acceptance(l);
        schema.requireRegistration(i,r);schema.requireLaunch(r,l);schema.requireAcceptance(l,a);
        assertEquals(64,schema.manifest().length());
    }
    @Test void unknownFieldsMissingFieldsAndDuplicateJsonAreRejected()throws Exception{
        var i=intent();i.put("step_id",id(99));assertThrows(SchemaRejection.class,()->schema.validate("ReplayIntent",i));
        i.remove("step_id");i.remove("outbox_event_id");assertThrows(SchemaRejection.class,()->schema.validate("ReplayIntent",i));
        assertThrows(java.io.IOException.class,()->schema.read("{\"a\":1,\"a\":2}"));
        assertThrows(java.io.IOException.class,()->schema.read("{} {}"));
        assertThrows(SchemaRejection.class,()->schema.validate("Unknown",intent()));
    }
    @Test void canonicalBytesHashSizeAndStableObjectIdentityAreRequired()throws Exception{
        for(String field:new String[]{"replay_sha256","size_bytes","write_key","object_key"}){
            var i=intent();i.put(field,field.equals("size_bytes")?"3":field.equals("object_key")?"other":"f".repeat(64));
            assertThrows(SchemaRejection.class,()->schema.validate("ReplayIntent",i),field);
        }
        var i=intent();i.put("canonical_payload","{ }").put("size_bytes","3").put("replay_sha256",DigestUtils.sha256Hex("{ }"));
        assertThrows(SchemaRejection.class,()->schema.validate("ReplayIntent",i));
    }
    @Test void registrationBindsOriginalIntentAndActualObjectVersion()throws Exception{
        var i=intent();var r=registration(i);r.put("artifact_id",id(88));assertThrows(SchemaRejection.class,()->schema.requireRegistration(i,r));
        r.put("artifact_id",id(10));((ObjectNode)r.required("object_ref")).put("object_version","");assertThrows(SchemaRejection.class,()->schema.requireRegistration(i,r));
        var wrong=registration(i);wrong.put("intent_sha256","f".repeat(64));assertThrows(SchemaRejection.class,()->schema.requireRegistration(i,wrong));
    }
    @Test void launchRequiresRegisteredArtifactAndCanonicalBoundedReference()throws Exception{
        var i=intent();var r=registration(i);var l=launch(r);
        ((ObjectNode)l.required("reference")).put("input_manifest_id",id(99));l.put("reference_sha256",schema.hash(l.required("reference")));
        assertThrows(SchemaRejection.class,()->schema.requireLaunch(r,l));
        var bounds=launch(r);((ObjectNode)bounds.required("reference")).put("maximum_parallel_steps",4);
        bounds.put("reference_sha256",schema.hash(bounds.required("reference")));assertThrows(SchemaRejection.class,()->schema.validate("WorkflowLaunchIntent",bounds));
        var policy=launch(r);((ObjectNode)policy.required("reference")).put("policy_revision",id(99));
        policy.put("reference_sha256",schema.hash(policy.required("reference")));assertThrows(SchemaRejection.class,()->schema.requireLaunch(r,policy));
        var defaults=launch(r);((ObjectNode)defaults.required("reference")).remove("read_timeout_millis");assertThrows(SchemaRejection.class,()->schema.validate("WorkflowLaunchIntent",defaults));
    }
    @Test void acceptanceCannotSubstituteWorkflowRunOrReference()throws Exception{
        var l=launch(registration(intent()));var a=acceptance(l);a.put("workflow_id",id(1)+"/"+id(99));assertThrows(SchemaRejection.class,()->schema.requireAcceptance(l,a));
        a.put("workflow_id",id(1)+"/"+id(3)).put("reference_sha256","f".repeat(64));assertThrows(SchemaRejection.class,()->schema.requireAcceptance(l,a));
        var early=acceptance(l);early.put("accepted_at","2026-10-18T00:00:00Z");assertThrows(SchemaRejection.class,()->schema.requireAcceptance(l,early));
    }
    @Test void replayIntentRequiresActualCompleteSchemaValidatedInputs()throws Exception{
        var c=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();var reader=new RuntimeReplaySchema(json);
        byte[] original=RuntimeReplaySchemaTest.fixture();var verified=reader.read(original,DigestUtils.sha256Hex(original),c.schemaDigest("replay-manifest"));
        assertThrows(SchemaRejection.class,()->schema.requireReplayIntent(intent(),verified));
        var value=verified.content();var i=intent();
        for(String name:new String[]{"tenant_id","project_id","run_id","principal_id","task_id","initiator_id"})i.set(name,value.required(name));
        i.set("release_id",value.required("agent_release_id"));i.set("publication_approval_sha256",value.required("publication_approval_sha256"));
        i.set("execution_policy",value.required("execution_policy_version"));
        String canonical=schema.canonical(value);byte[] bytes=canonical.getBytes(StandardCharsets.UTF_8);
        var canonicalVerified=reader.read(bytes,DigestUtils.sha256Hex(bytes),verified.schemaSha256());
        i.put("canonical_payload",canonical).put("size_bytes",Integer.toString(bytes.length)).put("replay_sha256",DigestUtils.sha256Hex(bytes))
            .put("replay_schema_sha256",verified.schemaSha256()).put("write_key",DigestUtils.sha256Hex((value.required("run_id").asText()+"/RUN/REPLAY_MANIFEST").getBytes(StandardCharsets.US_ASCII)))
            .put("object_key","runtime/"+value.required("tenant_id").asText()+"/"+value.required("run_id").asText()+"/"+id(9)+"/"+id(10));
        schema.requireReplayIntent(i,canonicalVerified);
    }
    @Test void migrationContainsOnlyRunOwnedEvidenceAndNoSyntheticStepOrStateWrites()throws Exception{
        var root=Path.of(System.getProperty("medical.projectRoot","../.."));
        var ddl=Files.readString(root.resolve("business/medical-data/src/main/resources/db/migration/092-task-run-bootstrap.sql"));
        assertEquals(6,ddl.split("CREATE TABLE ",-1).length-1);assertFalse(ddl.contains("step_id"));
        assertFalse(ddl.contains("UPDATE task_run"));assertFalse(ddl.contains("INSERT INTO run_step"));
        assertTrue(ddl.contains("REFERENCES artifact(tenant_id,project_key,artifact_id,sha256,object_version)"));
        assertTrue(ddl.contains("REFERENCES task_run_replay_attempt(tenant_id,project_id,run_id,write_id)"));
        assertTrue(ddl.contains("REFERENCES task_run_workflow_attempt(tenant_id,project_id,run_id,launch_id)"));
    }
}
