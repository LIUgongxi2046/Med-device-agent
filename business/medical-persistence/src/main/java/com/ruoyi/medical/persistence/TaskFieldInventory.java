package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Lossless inventory projection. MAPPED means supplied assignment, never verified medical fact. */
final class TaskFieldInventory {
    private final PermissionSchema json;
    TaskFieldInventory(PermissionSchema json){this.json=json;}
    ArrayNode derive(TemplateProtocol.Content template,TaskPlanProjection.Projection plan,
            List<TemplateApplicabilityProtocol.ConditionResult> conditions,JsonNode mappings,int maximum) {
        if(!mappings.isArray()||template.field_definitions().size()>maximum||mappings.size()>maximum)limit();
        var definitions=new HashMap<String,TemplateProtocol.Field>();
        for(var field:template.field_definitions())if(definitions.put(field.field_id(),field)!=null)bad();
        var parents=new HashMap<String,String>();
        for(var node:template.nodes()){if(parents.containsKey(node.node_id()))bad();parents.put(node.node_id(),node.parent_id());}
        var repeats=new HashMap<String,TemplateProtocol.Repeat>();
        for(var repeat:template.repeat_definitions())if(repeats.put(repeat.repeat_id(),repeat)!=null)bad();
        var sectionNodes=new HashSet<String>();
        for(var relation:plan.sectionNodes())sectionNodes.add(relation.sectionId()+"/"+relation.nodeId());
        var questionNodes=new HashSet<String>();
        for(var relation:plan.questionNodes())questionNodes.add(relation.questionId()+"/"+relation.nodeId());
        var questions=new HashMap<String,TaskPlanProjection.Question>();
        for(var question:plan.questions())questions.put(question.questionId(),question);
        var assignments=new HashMap<String,List<JsonNode>>();var entryKeys=new HashSet<String>();var occurrenceKeys=new HashSet<String>();
        for(var mapping:mappings){
            String fieldId=text(mapping,"field_definition_id"),section=text(mapping,"section_id"),node=text(mapping,"node_id");
            var field=definitions.get(fieldId);
            if(field==null||!node.equals(field.node_id())||!sectionNodes.contains(section+"/"+node)||!entryKeys.add(text(mapping,"entry_key")))bad();
            var row=mapping.required("row_id");if(field.repeat_id()==null&&!row.isNull())bad();
            if(!occurrenceKeys.add(fieldId+"/"+section+"/"+(row.isNull()?"":row.textValue())))bad();
            for(var id:mapping.required("question_ids")){
                var question=questions.get(id.textValue());
                if(question==null||!section.equals(question.sectionId())||!questionNodes.contains(id.textValue()+"/"+node))bad();
            }
            assignments.computeIfAbsent(fieldId+"/"+section,key->new ArrayList<>()).add(mapping);
        }
        var result=json.object().arrayNode();
        for(var field:template.field_definitions()){
            var sections=plan.sections().stream().filter(section->sectionNodes.contains(section.sectionId()+"/"+field.node_id())).toList();
            if(sections.isEmpty()){append(result,field,null,List.of(),conditions,List.of(),repeats,parents);if(result.size()>maximum)limit();}
            else for(var section:sections){
                var questionIds=plan.questions().stream().filter(q->q.sectionId().equals(section.sectionId())&&questionNodes.contains(q.questionId()+"/"+field.node_id())).map(TaskPlanProjection.Question::questionId).toList();
                append(result,field,section.sectionId(),questionIds,conditions,assignments.getOrDefault(field.field_id()+"/"+section.sectionId(),List.of()),repeats,parents);
                if(result.size()>maximum)limit();
            }
        }
        return result;
    }
    private void append(ArrayNode inventory,TemplateProtocol.Field field,String section,List<String> questions,
            List<TemplateApplicabilityProtocol.ConditionResult> all,List<JsonNode> entries,Map<String,TemplateProtocol.Repeat> repeats,Map<String,String> parents){
        var nodes=new HashSet<String>();String node=field.node_id();
        while(node!=null){if(!parents.containsKey(node)||!nodes.add(node))bad();node=parents.get(node);}
        var relevant=all.stream().filter(c->("FIELD".equals(c.scope())&&c.target_id().equals(field.field_id()))
                ||("NODE".equals(c.scope())&&nodes.contains(c.target_id()))).toList();
        if(relevant.size()!=nodes.size()+1||relevant.stream().map(c->c.scope()+"/"+c.target_id()).distinct().count()!=relevant.size())bad();
        var repeat=field.repeat_id()==null?null:repeats.get(field.repeat_id());
        if(field.repeat_id()!=null&&repeat==null)bad();
        String state;
        if(section==null)state="UNASSIGNED_SECTION";
        else if(relevant.stream().anyMatch(c->"NOT_APPLICABLE".equals(c.status())))state="NOT_APPLICABLE";
        else if(relevant.stream().anyMatch(c->!"APPLICABLE".equals(c.status())))state="UNKNOWN_CONDITION";
        else if(!"TEXT".equals(field.value_type()))state="UNSUPPORTED_TYPE";
        else if(entries.isEmpty())state="MISSING_MAPPING";
        else if(entries.stream().anyMatch(e->"UNKNOWN".equals(text(e,"applicability"))))state="UNKNOWN_CONDITION";
        else if(entries.stream().anyMatch(e->"UNRESOLVED".equals(text(e,"model_context"))||"UNRESOLVED".equals(text(e,"condition_context"))||(repeat!=null&&repeat.row_id_required()&&e.required("row_id").isNull())))state="UNRESOLVED_CONTEXT";
        else if(entries.stream().anyMatch(e->e.required("source_exact").isNull()))state="MISSING_SOURCE";
        else state="MAPPED";
        var item=json.object();item.put("field_definition_id",field.field_id());
        if(section==null)item.putNull("section_id");else item.put("section_id",section);
        item.put("node_id",field.node_id());item.set("question_ids",json.tree(questions));item.put("key",field.key());
        item.put("name",field.name());item.put("value_type",field.value_type());item.put("required",field.required());
        item.set("condition",json.tree(field.condition()));
        if(field.repeat_id()==null)item.putNull("repeat_id");else item.put("repeat_id",field.repeat_id());
        item.put("row_id_required",repeat!=null&&repeat.row_id_required());item.put("state",state);
        item.set("field_definition",json.tree(field));item.set("entry_keys",json.tree(entries.stream().map(e->text(e,"entry_key")).toList()));
        inventory.add(item);
    }
    private static String text(JsonNode node,String key){var value=node.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}
