package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed original RUN_ADMISSION bytes must match both the saved run and its original
 * START_RUN event. This is an integrity witness, not a content/current-rights grant. */
public final class RuntimeReplayOriginalAuthority {
 private final JdbcTemplate jdbc;private final PermissionSchema json;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public RuntimeReplayOriginalAuthority(DataSource data,PermissionSchema json){this(new JdbcTemplate(data),json);}
 RuntimeReplayOriginalAuthority(JdbcTemplate jdbc,PermissionSchema json){this.jdbc=jdbc;this.json=json;}
 void require(EnterpriseAccess.Admission admission,byte[] bytes)throws Exception {
  var i=admission.formalProof().identity();
  if(!"TASK".equals(i.executionKind()))bad();
  var rows=jdbc.query("""
   SELECT r.task_id,r.replay_manifest,o.payload FROM task_run r
   JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested'
   WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK'
   LIMIT 2 FOR UPDATE
   """,(r,n)->new Original(r.getString(1),r.getString(2),r.getString(3)),i.tenant(),i.project(),i.run(),i.actor());
  if(rows.size()!=1)bad();var row=rows.get(0);
  verify(bytes,reader.readTree(row.replay()),reader.readTree(row.event()),i.tenant(),i.project(),i.actor(),i.run(),i.principal(),row.task());
 }
 void verify(byte[] bytes,JsonNode saved,JsonNode event,String tenant,String project,String actor,String run,String principal,String task)throws Exception {
  if(!java.util.Arrays.equals(bytes,json.canonical(saved).getBytes(java.nio.charset.StandardCharsets.UTF_8)))bad();
  var command=event.required("command");
  if(!"START_RUN".equals(command.required("kind").asText())||!"1.0.0".equals(command.required("schema_version").asText())
    ||!tenant.equals(command.required("tenant_id").asText())||!project.equals(command.required("project_id").asText())
    ||!run.equals(command.required("run_id").asText())||!principal.equals(command.required("principal_id").asText())
    ||!task.equals(command.required("task_id").asText())||!(tenant+"/"+run).equals(command.required("workflow_id").asText())
    ||!actor.equals(event.required("decision").required("actor_id").asText())
    ||!WireValues.sha256(bytes).equals(command.required("replay_sha256").asText()))bad();
  var original=event.deepCopy();((ObjectNode)original.required("command")).remove("command_sha256");
  if(!WireValues.sha256(json.canonical(original).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(command.required("command_sha256").asText()))bad();
 }
 private record Original(String task,String replay,String event){}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
