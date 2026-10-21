package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;

class TaskRunGraphCompilerTest {
    final PermissionSchema json;
    TaskRunGraphCompilerTest()throws Exception{json=new PermissionSchema();}
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    ObjectNode replay(){
        var r=json.object();for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","plan_revision","snapshot_id"))r.put(f,id(1+List.of("tenant_id","project_id","task_id","run_id","principal_id","plan_revision","snapshot_id").indexOf(f)));
        var plan=r.putObject("plan").put("content_sha256","a".repeat(64));var c=plan.putObject("content");var qs=c.putArray("questions");
        var q=qs.addObject().put("question_key","first").put("section_id",id(10)).put("question","Exact first question");q.putArray("depends_on_question_keys");
        q=qs.addObject().put("question_key","second").put("section_id",id(10)).put("question","Exact second question");q.putArray("depends_on_question_keys").add("first");r.putObject("task_input").putNull("based_on_document_revision");return r;
    }
    List<TaskRunGraphCompiler.Question> rows(){return List.of(new TaskRunGraphCompiler.Question(id(11),"first",id(10),"Exact first question",0),new TaskRunGraphCompiler.Question(id(12),"second",id(10),"Exact second question",1));}
    @Test void deterministicQuestionDagHasNoInventedLaterNodes()throws Exception{
        var a=TaskRunGraphCompiler.compile(json,replay(),rows(),10);var b=TaskRunGraphCompiler.compile(json,replay(),rows(),10);
        assertEquals(a,b);assertEquals(2,a.nodes().size());assertEquals(List.of(a.nodes().get(0).stepId()),a.nodes().get(1).dependsOnStepIds());assertEquals(List.of("N03","N04","N05","N06"),a.requiredStages());assertFalse(a.content().toString().contains("input_sha256"));
    }
    @Test void siblingRunGetsDistinctNodeIdentities()throws Exception{var r=replay();var a=TaskRunGraphCompiler.compile(json,r,rows(),10);r.put("run_id",id(99));assertNotEquals(a.nodes().get(0).stepId(),TaskRunGraphCompiler.compile(json,r,rows(),10).nodes().get(0).stepId());}
    @Test void dependencyCycleRejected(){var r=replay();((ObjectNode)r.path("plan").path("content").path("questions").get(0)).withArray("depends_on_question_keys").add("second");assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10));}
    @Test void duplicateDependencyRejected(){var r=replay();((ObjectNode)r.path("plan").path("content").path("questions").get(1)).withArray("depends_on_question_keys").add("first");assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10));}
    @Test void unknownDependencyRejected(){var r=replay();((ObjectNode)r.path("plan").path("content").path("questions").get(1)).withArray("depends_on_question_keys").add("missing");assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10));}
    @Test void exactQuestionTextAndPersistentIdentityRequired(){var rows=new ArrayList<>(rows());rows.set(0,new TaskRunGraphCompiler.Question(id(11),"first",id(10),"Different text",0));assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,replay(),rows,10));}
    @Test void missingQuestionCannotBecomeEmptyGraph(){var r=replay();((ObjectNode)r.path("plan").path("content")).putArray("questions");assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,List.of(),10));}
    @Test void boundAppliesBeforeNodeCreation(){assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,replay(),rows(),1));}
    @Test void savedBodyRequiresItsOwnActualAuthority(){var r=replay();((ObjectNode)r.path("task_input")).put("based_on_document_revision",id(42));assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10)).code());}
    @Test void malformedSavedBodyWitnessFailsWithTypedIntegrityRejection(){
        for(String witness:List.of("{", "{}")){
            var r=replay();((ObjectNode)r.path("task_input")).put("based_on_document_revision",id(42));
            r.putObject("authorizations").put("canonical_witnesses",witness);
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10)).code());
        }
    }
    @Test void nonTextualSavedBodyRevisionCannotBypassItsProof(){
        var r=replay();((ObjectNode)r.path("task_input")).put("based_on_document_revision",42);
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskRunGraphCompiler.compile(json,r,rows(),10)).code());
    }
    @Test void graphReturnedJsonCannotMutateIdentity()throws Exception{var graph=TaskRunGraphCompiler.compile(json,replay(),rows(),10);((ObjectNode)graph.content()).put("run_id",id(42));assertEquals(id(4),graph.content().path("run_id").asText());assertThrows(UnsupportedOperationException.class,()->graph.nodes().clear());}
}
