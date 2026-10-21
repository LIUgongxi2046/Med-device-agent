package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskFieldInventoryTest {
 TaskFieldInventoryTest()throws Exception{}
 final PermissionSchema json=new PermissionSchema();final TaskFieldInventory inventory=new TaskFieldInventory(json);
 static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
 TemplateProtocol.Content template(boolean fields,boolean unsupported)throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var node=mapper.treeToValue(TaskFieldTargetSchemaTest.node(),TemplateProtocol.Node.class);
  var field=TaskFieldTargetSchemaTest.field().put("required",true);if(unsupported)field.put("value_type","DECIMAL");
  return new TemplateProtocol.Content("1.0.0","DOC","PATH",false,"PROFILE",List.of(node),fields?List.of(mapper.treeToValue(field,TemplateProtocol.Field.class)):List.of(),List.of(),List.of(),List.of(),null,List.of(),null);
 }
 TaskPlanProjection.Projection plan(){return new TaskPlanProjection.Projection(id(5),id(6),List.of(id(9)),List.of(),List.of(new TaskPlanProjection.Section(id(8),"goal",id(90),null,0)),List.of(new TaskPlanProjection.SectionNode(id(8),id(9),0)),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());}
 List<TemplateApplicabilityProtocol.ConditionResult> conditions(String state){return List.of(new TemplateApplicabilityProtocol.ConditionResult("NODE",id(9),null,null,List.of(),"APPLICABLE",List.of()),new TemplateApplicabilityProtocol.ConditionResult("FIELD",id(12),null,null,List.of(),state,List.of()));}
 @Test void missingRequiredAssignmentsDoNotBecomeEmptyInventory()throws Exception{
  var actual=inventory.derive(template(true,false),plan(),conditions("APPLICABLE"),json.object().arrayNode(),100);
  assertEquals(1,actual.size());assertTrue(actual.get(0).required("required").booleanValue());assertEquals("MISSING_MAPPING",actual.get(0).required("state").asText());assertTrue(TaskFieldTargetPreparationService.hasUnresolved(actual));
 }
 @Test void genuineNoFieldTemplateRetainsNodeButDoesNotInventField()throws Exception{var template=template(false,false);assertEquals(1,template.nodes().size());assertTrue(inventory.derive(template,plan(),conditions("APPLICABLE"),json.object().arrayNode(),100).isEmpty());}
 @Test void currentNotApplicableRetainsRequiredDefinitionAndIsNotChosenByMapping()throws Exception{
  var mapping=TaskFieldTargetSchemaTest.mappingAuthor().required("mappings");
  var actual=inventory.derive(template(true,false),plan(),conditions("NOT_APPLICABLE"),mapping,100);
  assertEquals("NOT_APPLICABLE",actual.get(0).required("state").asText());assertTrue(actual.get(0).required("field_definition").required("required").booleanValue());
  ((ObjectNode)mapping.get(0)).put("applicability","UNKNOWN");actual=inventory.derive(template(true,false),plan(),conditions("APPLICABLE"),mapping,100);
  assertEquals("UNKNOWN_CONDITION",actual.get(0).required("state").asText());assertTrue(TaskFieldTargetPreparationService.hasUnresolved(actual));
 }
 @Test void unsupportedRequiredFieldRemainsVisibleAndWaiting()throws Exception{var actual=inventory.derive(template(true,true),plan(),conditions("APPLICABLE"),TaskFieldTargetSchemaTest.mappingAuthor().required("mappings"),100);assertEquals("UNSUPPORTED_TYPE",actual.get(0).required("state").asText());assertTrue(TaskFieldTargetPreparationService.hasUnresolved(actual));}
 @Test void missingActualConditionProofCannotMasqueradeAsNotApplicable()throws Exception{assertThrows(TenantAccessException.class,()->inventory.derive(template(true,false),plan(),List.of(),TaskFieldTargetSchemaTest.mappingAuthor().required("mappings"),100));}
 @Test void trailingUnassignedFieldCannotExceedExpandedInventoryBound()throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var content=(ObjectNode)mapper.valueToTree(template(true,false));
  ((com.fasterxml.jackson.databind.node.ArrayNode)content.required("nodes")).add(TaskFieldTargetSchemaTest.node().put("node_id",id(91)));
  ((com.fasterxml.jackson.databind.node.ArrayNode)content.required("field_definitions")).add(TaskFieldTargetSchemaTest.field().put("field_id",id(92)).put("node_id",id(91)));
  var p=plan();var expanded=new TaskPlanProjection.Projection(id(5),id(6),List.of(id(9)),List.of(),
    List.of(new TaskPlanProjection.Section(id(8),"a",id(90),null,0),new TaskPlanProjection.Section(id(81),"b",id(90),null,1)),
    List.of(new TaskPlanProjection.SectionNode(id(8),id(9),0),new TaskPlanProjection.SectionNode(id(81),id(9),0)),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
  var conditions=new java.util.ArrayList<>(conditions("APPLICABLE"));
  conditions.add(new TemplateApplicabilityProtocol.ConditionResult("NODE",id(91),null,null,List.of(),"APPLICABLE",List.of()));
  conditions.add(new TemplateApplicabilityProtocol.ConditionResult("FIELD",id(92),null,null,List.of(),"APPLICABLE",List.of()));
  assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTEXT_LIMIT,assertThrows(TenantAccessException.class,
    ()->inventory.derive(mapper.treeToValue(content,TemplateProtocol.Content.class),expanded,conditions,json.object().arrayNode(),2)).code());
 }
}
