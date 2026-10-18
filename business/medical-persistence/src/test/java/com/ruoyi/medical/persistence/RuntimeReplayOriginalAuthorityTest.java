package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.WireValues;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RuntimeReplayOriginalAuthorityTest {
 PermissionSchema json;RuntimeReplayOriginalAuthority authority;ObjectNode replay,event;byte[] bytes;
 String tenant=id(1),project=id(2),actor=id(3),run=id(4),principal=id(5),task=id(6);
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 @BeforeEach void setup()throws Exception{json=new PermissionSchema();authority=new RuntimeReplayOriginalAuthority(mock(org.springframework.jdbc.core.JdbcTemplate.class),json);replay=json.object().put("fixed","original");bytes=json.canonical(replay).getBytes(java.nio.charset.StandardCharsets.UTF_8);event=json.object();event.putObject("command").put("kind","START_RUN").put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("principal_id",principal).put("task_id",task).put("workflow_id",tenant+"/"+run).put("replay_sha256",WireValues.sha256(bytes));event.putObject("decision").put("actor_id",actor);rehash();}
 void rehash()throws Exception{var command=(ObjectNode)event.required("command");command.remove("command_sha256");command.put("command_sha256",WireValues.sha256(json.canonical(event).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 void verifyOriginal()throws Exception{authority.verify(bytes,replay,event,tenant,project,actor,run,principal,task);}
 @Test void exactOriginalBytesAndCommandPass()throws Exception{verifyOriginal();}
 @Test void changedSavedReplayCannotBorrowOriginalOutbox(){replay.put("fixed","tampered");assertThrows(TenantAccessException.class,this::verifyOriginal);}
 @Test void rehashedWrongRunCannotBorrowOriginalIntent()throws Exception{((ObjectNode)event.required("command")).put("run_id",id(55));rehash();assertThrows(TenantAccessException.class,this::verifyOriginal);}
 @Test void rehashedWrongActorCannotBorrowOriginalIntent()throws Exception{((ObjectNode)event.required("decision")).put("actor_id",id(55));rehash();assertThrows(TenantAccessException.class,this::verifyOriginal);}
 @Test void rehashedWrongCommandKindRejected()throws Exception{((ObjectNode)event.required("command")).put("kind","RESUME_RUN");rehash();assertThrows(TenantAccessException.class,this::verifyOriginal);}
 @Test void changedUnhashedDecisionRejected(){((ObjectNode)event.required("decision")).put("unrecorded",true);assertThrows(TenantAccessException.class,this::verifyOriginal);}
 @Test void canonicalEquivalentReformattedBytesRejected(){bytes="{ \"fixed\": \"original\" }".getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThrows(TenantAccessException.class,this::verifyOriginal);}
}
