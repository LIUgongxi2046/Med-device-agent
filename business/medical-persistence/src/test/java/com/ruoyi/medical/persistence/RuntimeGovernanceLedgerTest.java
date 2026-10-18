package com.ruoyi.medical.persistence;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeGovernanceLedgerTest {
 com.fasterxml.jackson.databind.node.ObjectNode manifest;List<RuntimeGovernanceLedger.Member> members;List<RuntimeGovernanceLedger.Edge> edges;
 @BeforeEach void setup(){var j=new com.fasterxml.jackson.databind.ObjectMapper();manifest=j.createObjectNode();var versions=manifest.putArray("versions");versions.addObject().put("version_id","v1").putNull("project_id").put("entity_kind","MODEL_CONFIG").put("content_sha256","a".repeat(64));versions.addObject().put("version_id","v2").put("project_id","p").put("entity_kind","PLAN_REVISION").put("content_sha256","b".repeat(64));manifest.putArray("edges").addObject().put("consumer_version_id","v2").put("upstream_version_id","v1").put("relation_kind","FIXED_INPUT");members=List.of(new RuntimeGovernanceLedger.Member(0,"v1",null,"MODEL_CONFIG","a".repeat(64)),new RuntimeGovernanceLedger.Member(1,"v2","p","PLAN_REVISION","b".repeat(64)));edges=List.of(new RuntimeGovernanceLedger.Edge(0,"v2","v1","FIXED_INPUT"));}
 @Test void completeOrderedMembersAndEdgesPass(){RuntimeGovernanceLedger.verifyRows(manifest,members,edges);}
 @Test void omittedGovernanceMemberRejected(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,members.subList(0,1),edges));}
 @Test void omittedGovernanceEdgeRejected(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,members,List.of()));}
 @Test void sameCountWrongTypeRejected(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,List.of(new RuntimeGovernanceLedger.Member(0,"v1",null,"ROLE","a".repeat(64)),members.get(1)),edges));}
 @Test void sameCountReorderedMembersRejected(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,List.of(members.get(1),members.get(0)),edges));}
 @Test void enterpriseOwnerCannotBecomeProjectOwner(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,List.of(new RuntimeGovernanceLedger.Member(0,"v1","p","MODEL_CONFIG","a".repeat(64)),members.get(1)),edges));}
 @Test void sameEndpointsWrongRelationRejected(){assertThrows(TenantAccessException.class,()->RuntimeGovernanceLedger.verifyRows(manifest,members,List.of(new RuntimeGovernanceLedger.Edge(0,"v2","v1","SUBSTITUTED"))));}
}
