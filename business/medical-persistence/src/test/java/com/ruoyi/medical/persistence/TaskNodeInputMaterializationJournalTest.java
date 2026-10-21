package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Pure metadata comparison only; this helper cannot create a current authority proof. */
class TaskNodeInputMaterializationJournalTest {
    private static final String ID="11111111-1111-4111-8111-111111111111",HASH="a".repeat(64);
    private final LocalDateTime created=LocalDateTime.of(2026,10,20,1,0);
    private TaskNodeInputProtocol.Input input(){var basis=new PermissionProtocol.Basis(ID,HASH);return new TaskNodeInputProtocol.Input("1.0.0",ID,ID,ID,ID,ID,ID,HASH,"N02",ID,"q1","Fixed question",ID,ID,HASH,ID,HASH,HASH,new TaskNodeInputProtocol.ReplayArtifact(ID,HASH,"physical-version"),HASH,basis,basis,List.of(),List.of());}
    private Map<String,Object> audit(){var m=new HashMap<String,Object>();m.putAll(Map.of("tenant_id",ID,"project_id",ID,"subject_id",ID,"created_by",ID,"created_at",created,"classification","RESTRICTED","schema_version","1.0.0","schema_manifest_sha256",HASH));m.putAll(Map.of("action","task.node.materialized","request_id",ID,"trace_id",ID,"resource_ref","canonical-definition","result","SUCCESS"));m.put("reason_code",null);return m;}
    private Map<String,Object> event(){return new HashMap<>(Map.of("tenant_id",ID,"aggregate_id",ID,"aggregate_revision",1,"event_type","task.node.materialized","payload","canonical-definition","occurred_at",created));}
    private void require(Map<String,Object>a,Map<String,Object>e){TaskNodeInputAuthority.requireMaterializationJournal(input(),ID,"RESTRICTED",created,HASH,"canonical-definition",a,e);}
    @Test void exactMaterializationJournalAccepted(){assertDoesNotThrow(()->require(audit(),event()));}
    @Test void actorProjectSchemaAndClassificationSubstitutionsRejected(){
        for(String field:List.of("tenant_id","project_id","subject_id","created_by","schema_version","schema_manifest_sha256","classification","action","request_id","trace_id","resource_ref","result")){
            var a=audit();a.put(field,"replacement");assertThrows(TenantAccessException.class,()->require(a,event()),field);
        }
    }
    @Test void auditTimeAndReasonTamperingRejected(){
        var a=audit();a.put("created_at",created.plusSeconds(1));assertThrows(TenantAccessException.class,()->require(a,event()));
        a.put("created_at",created);a.put("reason_code","REPLACED");assertThrows(TenantAccessException.class,()->require(a,event()));
    }
    @Test void eventIdentityBodyRevisionAndTimestampTamperingRejected(){
        for(String field:List.of("tenant_id","aggregate_id","event_type","payload")){var e=event();e.put(field,"replacement");assertThrows(TenantAccessException.class,()->require(audit(),e),field);}
        var e=event();e.put("aggregate_revision",2);assertThrows(TenantAccessException.class,()->require(audit(),e));e.put("aggregate_revision",1);e.put("occurred_at",created.plusNanos(1000));assertThrows(TenantAccessException.class,()->require(audit(),e));
    }
    @Test void missingMetadataRejected(){var a=audit();a.remove("schema_manifest_sha256");assertThrows(TenantAccessException.class,()->require(a,event()));var e=event();e.remove("payload");assertThrows(TenantAccessException.class,()->require(audit(),e));}
}
