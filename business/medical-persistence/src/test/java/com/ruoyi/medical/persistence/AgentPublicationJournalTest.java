package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-memory storage faults only; these mocks do not establish real DB or governance authorization. */
class AgentPublicationJournalTest {
 final PermissionSchema json=new PermissionSchema();final AgentPublicationSchema schema=new AgentPublicationSchema(json);
 final AgentPublicationMapper mapper=mock(AgentPublicationMapper.class);final PermissionMapper permissions=mock(PermissionMapper.class);
 final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);final AgentPublicationJournal journal=new AgentPublicationJournal(mapper,permissions,schema,json);
 AgentPublicationMapper.Journal row;SelectionOperationRow operation;String audit,event;ArtifactRegistryProtocol.VersionRow version;
 final String key="original-key-0000001";final JsonNode request=json.object().put("reason","original body");
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 AgentPublicationJournalTest()throws Exception {
  when(a.actorId()).thenReturn(id(2));when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,10,0));
  when(mapper.journal(anyMap())).thenAnswer(i->row==null?List.of():List.of(row));when(mapper.journalByKey(anyMap())).thenAnswer(i->row==null?List.of():List.of(row));when(mapper.version(anyMap())).thenAnswer(i->version==null?List.of():List.of(version));when(mapper.operation(anyMap())).thenAnswer(i->operation==null?List.of():List.of(operation));
  when(mapper.insertOperation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);var r=(AgentPublicationMapper.Journal)v.get("row");operation=new SelectionOperationRow(r.operationId(),r.requestSha256(),"COMPLETED",201,r.receiptJson(),r.recordId(),"1.0.0",r.schemaManifest());return 1;});
  when(mapper.insertJournal(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);row=(AgentPublicationMapper.Journal)v.get("row");return 1;});
  when(permissions.saveAudit(anyString(),isNull(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audit=i.getArgument(4);return 1;});when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{event=i.getArgument(5);return 1;});
  when(mapper.audit(anyMap())).thenAnswer(i->audit==null?List.of():List.of(audit));when(mapper.event(anyMap())).thenAnswer(i->event==null?List.of():List.of(event));
 }
 void save()throws Exception{var content=json.object().put("state","DRAFT");version=new ArtifactRegistryProtocol.VersionRow(id(3),null,"AGENT_PUBLICATION_RECORD",schema.hash(content));journal.save(a,null,id(3),"AUTHOR",id(4),key,request,content,json.object().put("fixture","no real grant"),"INTERNAL");}
 @Test void actualOriginalReceiptAndHistoricalActorRoundTrip()throws Exception{save();assertEquals(row,journal.prior(a,"AUTHOR",key,request));when(a.actorId()).thenReturn(id(20));assertEquals(row,journal.read(a,id(3)));}
 @Test void changedBodyCannotRecoverSameKey()throws Exception{save();var e=assertThrows(TenantAccessException.class,()->journal.prior(a,"AUTHOR",key,json.object().put("reason","changed")));assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,e.code());}
 @Test void missingOperationCannotBecomeNotFound()throws Exception{save();operation=null;assertThrows(TenantAccessException.class,()->journal.prior(a,"AUTHOR",key,request));}
 @Test void missingSidecarCannotBecomeNotFound()throws Exception{save();row=null;assertThrows(TenantAccessException.class,()->journal.prior(a,"AUTHOR",key,request));}
 @Test void registryHashCannotDrift()throws Exception{save();version=new ArtifactRegistryProtocol.VersionRow(id(3),null,"AGENT_PUBLICATION_RECORD","a".repeat(64));assertThrows(TenantAccessException.class,()->journal.read(a,id(3)));}
 @Test void registryKindCannotMasqueradeAsCapability()throws Exception{save();version=new ArtifactRegistryProtocol.VersionRow(id(3),null,"CAPABILITY_VERSION",version.contentSha256());assertThrows(TenantAccessException.class,()->journal.read(a,id(3)));}
 @Test void completeAuditRequestAndActorRemainBound()throws Exception{save();var changed=(com.fasterxml.jackson.databind.node.ObjectNode)schema.read(audit);changed.put("created_by",id(20));audit=schema.canonical(changed);assertThrows(TenantAccessException.class,()->journal.read(a,id(3)));}
 @Test void operationReceiptCannotDrift()throws Exception{save();operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"COMPLETED",201,"{}",operation.targetId(),"1.0.0",operation.schemaManifestSha256());assertThrows(TenantAccessException.class,()->journal.read(a,id(3)));}
 @Test void eventBodyCannotDrift()throws Exception{save();event="{}";assertThrows(TenantAccessException.class,()->journal.read(a,id(3)));}
 @Test void schemaClosesPolicyDecisionAndAllCurrentReceiptKinds()throws Exception{for(String kind:List.of("AUTHOR","POLICY_DRAFT","POLICY","QUALIFICATION","ACCEPTANCE","PUBLICATION","EXECUTION_POLICY_AUTHOR","EXECUTION_POLICY_DECISION"))assertNotNull(schema.receipt(id(5),id(6),kind,"a".repeat(64),"b".repeat(64)));var request=json.object().put("schema_version","1.0.0").put("draft_id",id(3)).put("draft_sha256","a".repeat(64)).put("decision","APPROVE").put("reason","Human decision");schema.validate("PolicyDecision",request);request.put("production_eligible",true);assertThrows(SchemaRejection.class,()->schema.validate("PolicyDecision",request));}
}
