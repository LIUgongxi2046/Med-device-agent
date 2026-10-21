package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Product model/condition scope is taken from independently approved original
 * source spans, never from a client's labels or an empty-array fallback. */
@Component
public final class SharedFactScopeAuthority {
 private final JdbcTemplate db;
 private final ObjectProvider<TaskStrictFieldContextService> contexts;
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 public SharedFactScopeAuthority(@Qualifier("masterDataSource")DataSource data,ObjectProvider<TaskStrictFieldContextService> contexts){this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.contexts=contexts;}
 public Scope require(EnterpriseAccess.Admission a,String project,JsonNode content)throws Exception {
  boolean models=scoped(content,"model_scope"),conditions=scoped(content,"condition_scope");
  var basis=content.path("scope_context");
  if(basis.isMissingNode()||basis.isNull()){
   if(models||conditions)fail();
   return new Scope(List.of(),null,json.tree(Map.of("model_scope","NOT_APPLICABLE","condition_scope","NOT_APPLICABLE","model_ids",List.of())));
  }
  var service=contexts.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var context=service.requireCurrent(a,project,basis.required("version_id").asText(),basis.required("content_sha256").asText());context.requireApproved();
  var rows=db.queryForList("SELECT task_id,task_revision,plan_revision,template_version,snapshot_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND document_id=? AND document_revision=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),project,content.required("document_id").asText(),content.required("document_revision").asText());
  if(rows.size()!=1)fail();var row=rows.get(0);var fixed=context.scope();
  if(!Objects.equals(row.get("task_id"),fixed.taskId())||!Objects.equals(row.get("task_revision"),fixed.taskRevision())||!Objects.equals(row.get("plan_revision"),fixed.planRevision())||!Objects.equals(row.get("template_version"),fixed.templateVersion())||!Objects.equals(row.get("snapshot_id"),fixed.scopeSnapshot()))fail();
  if(models!=(context.models().mode()==TaskStrictFieldTypedProtocol.Mode.SCOPED)||conditions!=(context.conditions().mode()==TaskStrictFieldTypedProtocol.Mode.SCOPED))fail();
  if(models&&context.models().ids().isEmpty()||conditions&&context.conditions().ids().size()!=1)fail();
  // Link identity is logical body-link identity, not a per-node evidence row ID.
  boolean found=false;
  for(var link:content.required("evidence_link_ids")){
   var projections=db.queryForList("SELECT value_json FROM document_evidence_projection WHERE tenant_id=? AND project_id=? AND document_revision=? AND link_id=? FOR UPDATE",a.context().tenant_id(),project,content.required("document_revision").asText(),link.asText());
   for(var projection:projections){var actual=json.read(projection.get("value_json").toString());if(actual.required("evidence").equals(json.tree(context.evidence())))found=true;}
  }
  if(!found)fail();context.requireApproved();
  var witness=json.tree(Map.of("model_scope",models?"SCOPED":"NOT_APPLICABLE","condition_scope",conditions?"SCOPED":"NOT_APPLICABLE","model_ids",context.models().ids(),"condition_ids",context.conditions().ids(),"context",basis,"context_decision",json.tree(context.decision()),"context_receipt",context.receipt()));
  return new Scope(models?context.models().ids():List.of(),conditions?context.conditions().ids().get(0):null,witness);
 }
 private static boolean scoped(JsonNode content,String field){String value=content.required(field).asText();if(!Set.of("SCOPED","NOT_APPLICABLE").contains(value))fail();return "SCOPED".equals(value);}
 public record Scope(List<String> modelIds,String conditionId,JsonNode witness){public Scope{modelIds=List.copyOf(modelIds);witness=witness.deepCopy();} @Override public JsonNode witness(){return witness.deepCopy();}}
 private static void fail(){throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);}
}
