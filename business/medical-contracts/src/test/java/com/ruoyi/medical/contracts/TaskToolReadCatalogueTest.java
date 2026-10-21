package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskToolReadCatalogueTest {
 @Test void exactReadInputAndVersionedReceiptArePublishedWithoutChangingBaseIdentity()throws Exception{
  var catalogue=SchemaCatalogue.fromClasspath();var json=new JsonMapper();String id="10000000-0000-4000-8000-000000000001",hash="a".repeat(64);
  var input=json.createObjectNode().put("question_id",id).put("query","fixed question").putNull("cursor");catalogue.validate("tool-inputs",input,"SearchArgs");
  var result=json.createObjectNode().put("schema_version","1.0.0").put("kind","TASK_READ_RESULT").put("action_id",id).put("tool_id","AT-01").put("status","SUCCEEDED").put("summary","").putNull("next_cursor");result.putObject("coverage").put("requested_scope_sha256",hash).put("delivered_scope_sha256",hash).put("complete",true);result.putObject("read_receipt").put("kind","RETRIEVAL_OPERATION").put("receipt_id",id).put("receipt_sha256",hash);var payload=result.putObject("payload").put("kind","SEARCH").put("retrieval_id",id).put("match_status","EMPTY");payload.putArray("evidence_refs");payload.putArray("missing_requirement_ids");catalogue.validate("task-tool-read-success",result,null);
  result.put("artifact_id",id);assertThrows(SchemaRejection.class,()->catalogue.validate("task-tool-read-success",result,null));
 }
 @Test void schemaRefIsExplicitAndOnlyReadToolsAreAccepted()throws Exception{var catalogue=SchemaCatalogue.fromClasspath();assertEquals("https://schemas.medical-agent.invalid/1.0.0/task-tool-read-success",catalogue.schemaRef("task-tool-read-success",null));assertThrows(SchemaRejection.class,()->catalogue.validate("task-tool-read-success",new JsonMapper().createObjectNode().put("tool_id","AT-06"),null));}
}
