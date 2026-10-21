package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred by user instruction: written, not executed. */
final class KnowledgeFeedbackSchemaTest {
    private static final String ID="00000000-0000-4000-8000-000000000001";
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static String create(String kind){return """
        {"project_id":"%s","kind":"%s","target_version":"%s","document_revision":null,
         "revised_document_revision":null,"review_id":null,"return_id":null,"reason":"A human correction needs review"}
        """.formatted(ID,kind,ID);}
    @Test void sourceAndRuleAreDistinctFromFormalAgentCandidates()throws Exception {
        var schema=new KnowledgeFeedbackSchema();
        assertEquals("SOURCE",schema.request("Create",bytes(create("SOURCE"))).required("kind").asText());
        assertEquals("RULE",schema.request("Create",bytes(create("RULE"))).required("kind").asText());
        assertThrows(SchemaRejection.class,()->schema.request("Create",bytes(create("AGENT"))));
        assertThrows(SchemaRejection.class,()->schema.request("Create",bytes(create("FACT"))));
    }
    @Test void requiredOriginalReferencesCannotBeSilentlyDropped()throws Exception {
        var schema=new KnowledgeFeedbackSchema();var node=new ObjectMapper().readTree(create("RULE"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("document_revision");
        assertThrows(SchemaRejection.class,()->schema.request("Create",bytes(node.toString())));
    }
    @Test void duplicateAndTrailingJsonAndUnknownStateAreRejected()throws Exception {
        var schema=new KnowledgeFeedbackSchema();
        assertThrows(Exception.class,()->schema.request("Create",bytes(create("RULE").replace("\"kind\":\"RULE\"","\"kind\":\"RULE\",\"kind\":\"SOURCE\""))));
        assertThrows(Exception.class,()->schema.request("Create",bytes(create("RULE")+"{}")));
        assertThrows(SchemaRejection.class,()->schema.request("Create",bytes(create("RULE").replace("\"reason\":","\"state\":\"CLOSED\",\"reason\":"))));
    }
    @Test void closureAcceptsOnlyAnApprovalReferenceNotPublicationOrSuccessFlags()throws Exception {
        var schema=new KnowledgeFeedbackSchema();String close="""
            {"expected_revision":"3","approval_id":"%s","source_selection":null,"reason":"Approved correction"}
            """.formatted(ID);
        schema.request("Close",bytes(close));
        assertThrows(SchemaRejection.class,()->schema.request("Close",bytes(close.replace("\"source_selection\":null","\"release_id\":null"))));
        assertThrows(SchemaRejection.class,()->schema.request("Close",bytes(close.replace("\"source_selection\":null","\"source_selection\":null,\"approved\":true"))));
        assertThrows(SchemaRejection.class,()->schema.request("Close",bytes(close.replace("\"expected_revision\":\"3\"","\"expected_revision\":0"))));
    }
    @Test void stateMachineCannotSkipApprovalEvidenceOrReopenHistory() {
        Map<String,Set<String>> allowed=Map.of("PENDING_REVIEW",Set.of("PROPOSAL_LINKED","REJECTED"),"PROPOSAL_LINKED",Set.of("REVIEWED","REJECTED"),"REVIEWED",Set.of("CLOSED"),"CLOSED",Set.of(),"REJECTED",Set.of());
        for(String from:allowed.keySet())for(String to:allowed.keySet()) {
            if(allowed.get(from).contains(to))assertDoesNotThrow(()->KnowledgeFeedbackService.requireTransition(from,to));
            else assertThrows(TenantAccessException.class,()->KnowledgeFeedbackService.requireTransition(from,to));
        }
    }
    @Test void classificationMayNotBeLaunderedByLowerClassifiedFeedback() {
        assertEquals("PERSONAL_HEALTH",KnowledgeFeedbackProofs.max("INTERNAL","PUBLIC","PERSONAL_HEALTH"));
        assertThrows(TenantAccessException.class,()->KnowledgeFeedbackProofs.boundedClassification(Map.of("classification","INTERNAL"),"RESTRICTED"));
        assertDoesNotThrow(()->KnowledgeFeedbackProofs.boundedClassification(Map.of("classification","RESTRICTED"),"INTERNAL"));
        assertThrows(TenantAccessException.class,()->KnowledgeFeedbackProofs.max("UNKNOWN"));
    }
}
