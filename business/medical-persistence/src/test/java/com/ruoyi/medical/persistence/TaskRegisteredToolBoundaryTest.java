package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRegisteredToolBoundaryTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private static final String ID="10000000-0000-4000-8000-000000000001",OTHER="10000000-0000-4000-8000-000000000002";
    @Test void roleBoundaryDoesNotPretendAllPublishedRolesImplemented(){
        assertDoesNotThrow(()->TaskRegisteredToolAuthority.requireNode("N02","Researcher","AT-03"));
        for(String node:new String[]{"N01","N03","N04","N05","N06","N07","unknown"})assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.requireNode(node,"Researcher","AT-03"));
        assertDoesNotThrow(()->TaskRegisteredToolAuthority.requireRole("NetworkResearcher","AT-04"));
        for(String role:new String[]{"Planner","FieldVerifier","SectionWriter","SourceVerifier","Assembler"})assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.requireRole(role,"AT-03"));
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.requireRole("Researcher","AT-01"));
    }
    @Test void metadataResultIdIsNeverPredecessorContentAuthority()throws Exception{
        var input=json.readTree("{\"predecessors\":[{\"evidence_artifact_id\":\""+ID+"\",\"result_artifact_id\":\""+OTHER+"\"}]}");
        assertEquals(ID,TaskRegisteredToolAuthority.exactPredecessor(input,ID).required("evidence_artifact_id").textValue());
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.exactPredecessor(input,OTHER));
        ((com.fasterxml.jackson.databind.node.ArrayNode)input.required("predecessors")).add(input.required("predecessors").get(0).deepCopy());
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.exactPredecessor(input,ID));
    }
    @Test void actualQuestionScopeCannotChooseOwnerOrOtherSections()throws Exception{
        var input=json.createObjectNode().put("section_id",ID);var args=json.createObjectNode().put("question_type","NEED_EVIDENCE").put("prompt_text","Which evidence applies?");
        args.putArray("target_section_ids").add(ID);args.putArray("requirement_ids").add(OTHER);args.putArray("evidence_refs");
        assertDoesNotThrow(()->TaskRegisteredToolWaitingService.validateQuestion(args,input,java.util.List.of(OTHER)));
        args.put("question_type","CONFIRM_PLAN");assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingService.validateQuestion(args,input,java.util.List.of(OTHER)));
        args.put("question_type","NEED_EVIDENCE");args.withArray("target_section_ids").removeAll().add(OTHER);
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingService.validateQuestion(args,input,java.util.List.of(OTHER)));
    }
    @Test void unsupportedNodeSlicingAndForgedHashFailBeforeRead()throws Exception{
        var args=json.createObjectNode().put("artifact_id",ID).putNull("cursor");args.putArray("node_ids").add(OTHER);
        var request=json.createObjectNode().put("call_id","actual-call").put("name","read_artifact").put("arguments_sha256",TaskRegisteredToolAuthority.hash(args));request.set("arguments",args);
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.validateRequest("AT-03",request,SchemaCatalogue.fromClasspath()));
        args.withArray("node_ids").removeAll();request.put("arguments_sha256","0".repeat(64));
        assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.validateRequest("AT-03",request,SchemaCatalogue.fromClasspath()));
    }
    @Test void allProducingProofConstructorsRemainPrivate(){
        for(Class<?> type:new Class<?>[]{TaskRegisteredToolAuthority.Checked.class,TaskInvocationService.WaitingTerminal.class})
            for(var constructor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
}
