package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure compiler, not authority. Technical v1 cardinality is one N02 per confirmed
 * question. Later stages are required obligations, not invented executable nodes.
 * LLD-Agent 4.1 N02-N07: research, comparison, drafting, checking, review, saved-body handoff. */
final class TaskRunGraphCompiler {
    static final String REF="urn:medical:task-run-planned-graph:1.0.0";
    record Question(String id,String key,String section,String text,int position) { }
    record Node(String questionId,String questionKey,String stepId,String questionText,String sectionId,List<String> dependsOnStepIds) {
        Node { dependsOnStepIds=List.copyOf(dependsOnStepIds); }
    }
    record Graph(List<Node> nodes,List<String> requiredStages,JsonNode content,String sha256) {
        Graph { nodes=List.copyOf(nodes);requiredStages=List.copyOf(requiredStages);content=content.deepCopy(); }
        @Override public JsonNode content(){return content.deepCopy();}
    }
    static Graph compile(PermissionSchema json,JsonNode replay,List<Question> rows,int maximum)throws Exception {
        if(maximum<1)throw new IllegalArgumentException("maximum");
        String tenant=text(replay,"tenant_id"),run=text(replay,"run_id");
        var plan=replay.required("plan");var content=plan.required("content");var questions=content.required("questions");
        if(!questions.isArray()||questions.isEmpty()||questions.size()>maximum||questions.size()!=rows.size())bad();
        // Signed continuations need their actual fixed predecessor proof; malformed
        // proof structure must fail with the compiler's typed integrity rejection.
        if(replay.required("task_input").hasNonNull("based_on_document_revision")) {
            try {
                WireValues.uuid(text(replay.required("task_input"),"based_on_document_revision"));
                if(TaskSignedContinuationInputs.replayBody(replay)==null)bad();
            } catch(IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException malformed) {
                bad();
            }
        }
        var byKey=new LinkedHashMap<String,Question>();var ids=new HashSet<String>();var steps=new LinkedHashMap<String,String>();
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);WireValues.uuid(row.id());WireValues.uuid(row.section());
            if(row.position()!=i||byKey.put(row.key(),row)!=null||!ids.add(row.id()))bad();
            var q=questions.get(i);if(!row.key().equals(text(q,"question_key"))||!row.section().equals(text(q,"section_id"))||!row.text().equals(text(q,"question")))bad();
            steps.put(row.key(),UUID.nameUUIDFromBytes((REF+"\n"+tenant+"\n"+run+"\n"+text(plan,"content_sha256")+"\n"+row.id()).getBytes(StandardCharsets.UTF_8)).toString());
        }
        var nodes=new ArrayList<Node>();var dependencies=new LinkedHashMap<String,List<String>>();
        for(var q:questions){
            String key=text(q,"question_key");var deps=new ArrayList<String>();var seen=new HashSet<String>();
            for(var dep:q.required("depends_on_question_keys")){
                if(!dep.isTextual()||!steps.containsKey(dep.textValue())||key.equals(dep.textValue())||!seen.add(dep.textValue()))bad();
                deps.add(steps.get(dep.textValue()));
            }
            var row=byKey.get(key);dependencies.put(steps.get(key),List.copyOf(deps));
            nodes.add(new Node(row.id(),key,steps.get(key),row.text(),row.section(),deps));
        }
        var pending=new HashSet<>(dependencies.keySet());var done=new HashSet<String>();
        while(!pending.isEmpty()){
            var ready=pending.stream().filter(n->done.containsAll(dependencies.get(n))).toList();
            if(ready.isEmpty())bad();pending.removeAll(ready);done.addAll(ready);
        }
        var stages=List.of("N03","N04","N05","N06");
        var out=json.object().put("schema_version","1.0.0").put("compiler_ref",REF);
        for(String field:List.of("tenant_id","project_id","task_id","run_id","principal_id","plan_revision","snapshot_id"))out.set(field,replay.required(field));
        out.put("plan_sha256",text(plan,"content_sha256"));out.set("confirmed_plan",plan.deepCopy());
        out.set("nodes",json.tree(nodes));out.set("required_stages",json.tree(stages));
        out.put("saved_body_handoff","NOT_APPLICABLE_FIRST_RUN");
        return new Graph(nodes,stages,out,WireValues.sha256(json.canonical(out).getBytes(StandardCharsets.UTF_8)));
    }
    private static String text(JsonNode n,String f){var v=n.required(f);if(!v.isTextual()||v.textValue().isBlank())bad();return v.textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
