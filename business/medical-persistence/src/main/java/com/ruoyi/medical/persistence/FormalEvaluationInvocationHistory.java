package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** Ordered original076 results. Never converts TASK history, caller JSON or an unbilled stop into EVAL history. */
public final class FormalEvaluationInvocationHistory {
 private final JdbcTemplate db;private final FormalEvaluationInvocationRecords records;private final FormalInvocationSchema schema;
 private final TaskInvocationResults results;private final FormalEvaluationToolReceipts tools;
 public FormalEvaluationInvocationHistory(DataSource data,FormalEvaluationInvocationRecords records,FormalInvocationSchema schema,TaskInvocationResults results,FormalEvaluationToolReceipts tools){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.records=records;this.schema=schema;this.results=results;this.tools=tools;}
 public final class Ordered {
  private final FormalEvaluationNodeRuntime.Session session;private final long ordinal;private final JsonNode data;private final List<String> roots;private final FormalEvaluationToolReceipts.History toolHistory;
  private Ordered(FormalEvaluationNodeRuntime.Session session,long ordinal,JsonNode data,List<String> roots,FormalEvaluationToolReceipts.History toolHistory){this.toolHistory=toolHistory;this.roots=List.copyOf(roots);this.session=session;this.ordinal=ordinal;this.data=data.deepCopy();}
  public JsonNode requireModelSend(String provider)throws Exception {return toolHistory.requireModelSend(session,provider);}public List<String> contentRoots(){return roots;}public JsonNode data(){return data.deepCopy();}public void requireCurrent()throws Exception {if(!data.equals(ordered(session,ordinal).data))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 Ordered ordered(FormalEvaluationNodeRuntime.Session session,long ordinal)throws Exception {
  session.requireCurrent();if(ordinal<1||ordinal>257)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var lease=session.lease();var job=lease.job();
  var rows=db.queryForList("SELECT c.ordinal,i.invocation_id FROM formal_evaluation_invocation_context c LEFT JOIN invocation i ON i.tenant_id=c.tenant_id AND i.context_manifest_id=c.context_manifest_id WHERE c.tenant_id=? AND c.run_id=? AND c.step_id=? AND c.session_id=? AND c.ordinal<? ORDER BY c.ordinal LIMIT 257",job.tenantId(),job.runId(),lease.stepId(),session.sessionId(),ordinal);
  if(rows.size()!=ordinal-1)bad();var history=TaskInvocationHistoryData.empty();long expected=1;var seen=new HashSet<String>();
  for(var row:rows){if(Long.parseLong(row.get("ordinal").toString())!=expected++||!(row.get("invocation_id") instanceof String id)||!seen.add(id))bad();String id=(String)row.get("invocation_id");var binding=records.byInvocation(job.tenantId(),id);if(!binding.session().equals(session.sessionId())||!binding.step().equals(lease.stepId())||!binding.inputHash().equals(lease.input().sha256())||!binding.generation().equals(Long.toString(lease.generation())))bad();var d=binding.evidence();var price=schema.price(d.required("model").required("definition").required("price_basis").required("version_id").asText(),d.required("price"));var value=results.verifiedSettlement(binding,id,price,true);if(!"tool-calls".equals(value.output().required("reason").required("kind").asText()))bad();((ArrayNode)history.required("model_results")).add(value.output());((ArrayNode)history.required("model_pins")).addObject().put("invocation_id",id).put("accounting_sha256",value.accounting()).put("result_registration_sha256",value.result()).put("capture_registration_sha256",value.capture());}
  var actualTools=tools.requireHistory(session,history.required("model_results"),history.required("model_pins"));var toolData=actualTools.data();history.set("tool_responses",toolData.required("tool_responses"));history.set("tool_pins",toolData.required("tool_pins"));actualTools.requireCurrent(session);TaskInvocationHistoryData.require(history,ordinal);session.requireCurrent();return new Ordered(session,ordinal,history,actualTools.contentRoots(),actualTools);
 }
 void requireOriginal(String tenant,String run,String step,String session,JsonNode history,long ordinal)throws Exception {
  TaskInvocationHistoryData.require(history,ordinal);int at=0;
  for(var result:history.required("model_results")){String id=result.required("invocation_id").asText();var binding=records.byInvocation(tenant,id);if(!run.equals(binding.run())||!step.equals(binding.step())||!session.equals(binding.session())||!Long.toString(at+1).equals(binding.evidence().required("ordinal").asText()))bad();var d=binding.evidence();var actual=results.verifiedSettlement(binding,id,schema.price(d.required("model").required("definition").required("price_basis").required("version_id").asText(),d.required("price")),true);var pin=history.required("model_pins").get(at);if(!result.equals(actual.output())||!actual.accounting().equals(pin.required("accounting_sha256").asText())||!actual.result().equals(pin.required("result_registration_sha256").asText())||!actual.capture().equals(pin.required("capture_registration_sha256").asText())||!TaskInvocationHistoryData.prefix(history,at).equals(d.required("invocation_history")))bad();at++;}
  tools.requireOriginal(tenant,run,step,session,history);
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
