package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskCandidateAssemblyProducerSchemaTest {
    private com.fasterxml.jackson.databind.node.ObjectNode author()throws Exception {var node=new TaskNodeProducerSchemaTest().author();node.set("section_ids",node.remove("question_ids"));node.putArray("artifact_kinds").add("ASSEMBLY_MANIFEST");return node;}
    @Test void closedAuthorAcceptsOnlyDedicatedKindsAndIndependentApproval()throws Exception {
        var schema=new TaskNodeProducerSchema(TaskNodeProducerProfile.ASSEMBLY);assertNotNull(schema.validate("InputAuthor",author()));
        for(String kind:List.of("SECTION_CANDIDATE","MODEL_RESULT","PROVIDER_CAPTURE","STEP_ENVELOPE","ANY_ARTIFACT")){var n=author();n.withArray("artifact_kinds").add(kind);assertThrows(RuntimeException.class,()->schema.validate("InputAuthor",n));}
        for(String op:List.of("MODEL_INPUT_CONSUME","SUPERVISOR_PREPARE_READ","DOCUMENT_SAVE","CANDIDATE_ADOPT")){var n=author();n.withArray("operations").add(op);assertThrows(RuntimeException.class,()->schema.validate("InputAuthor",n));}
        assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireDecision("DRAFT","APPROVE","same","same"));assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireDecision("DISABLED","APPROVE","one","two"));
        var choices=TaskNodeProducerService.preparationChoices(new PermissionSchema(),TaskNodeProducerProfile.ASSEMBLY);assertFalse(choices.required("operations").toString().contains("MODEL_INPUT_CONSUME"));
    }
    @Test void profileHasDistinctGovernanceAndNoInvocationKinds()throws Exception {
        var profile=TaskNodeProducerProfile.ASSEMBLY;assertEquals("N06",profile.node());assertEquals("task_candidate_assembly_producer_",profile.table());assertEquals("TASK-CANDIDATE-ASSEMBLY-PRODUCER-",profile.operation());
        assertEquals(Set.copyOf(TaskCandidateAssemblyObjectLedger.KINDS),profile.kinds());assertFalse(profile.kinds().contains("MODEL_RESULT"));assertFalse(profile.kinds().contains("PROVIDER_CAPTURE"));
        var schema=new TaskNodeProducerSchema(profile);assertNotEquals(new TaskNodeProducerSchema(TaskNodeProducerProfile.WRITER).manifest(),schema.manifest());assertNotEquals(new TaskNodeProducerSchema(TaskNodeProducerProfile.VERIFIER).manifest(),schema.manifest());
    }
    @Test void authorCannotSelectN04N05OrModelOperations()throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream(TaskNodeProducerProfile.ASSEMBLY.resource()))){var document=JsonMapper.builder().build().readTree(in);var defs=document.required("$defs");
            assertEquals("N06",defs.required("InputManifest").required("properties").required("producer_node_kind").required("const").asText());
            for(String name:List.of("InputAuthor","InputManifest")){var props=defs.required(name).required("properties");var operations=new HashSet<String>();props.required("operations").required("items").required("enum").forEach(n->operations.add(n.asText()));assertEquals(Set.of("NODE_PRODUCE","HOST_PREDECESSOR_CONSUME"),operations);var kinds=new HashSet<String>();props.required("artifact_kinds").required("items").required("enum").forEach(n->kinds.add(n.asText()));assertEquals(TaskNodeProducerProfile.ASSEMBLY.kinds(),kinds);}
        }
    }
}
