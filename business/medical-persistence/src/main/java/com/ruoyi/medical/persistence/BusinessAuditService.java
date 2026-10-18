package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import com.ruoyi.medical.persistence.mapper.BusinessAuditMapper;
import java.util.*;import java.time.*;import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.util.function.ThrowingSupplier;
@Component
@EnableConfigurationProperties(BusinessAuditPolicy.class)
@ConditionalOnProperty(prefix="medical.business-audit",name="enabled",havingValue="true")
public final class BusinessAuditService{
 private final EnterpriseAccess access;private final BusinessAuditMapper mapper;private final BusinessAuditDocumentSavedAuthority authority;private final BusinessAuditCursor cursors;private final BusinessAuditPolicy policy;private final PermissionSchema json;private final BusinessAuditSchema schema;
 public BusinessAuditService(EnterpriseAccess access,BusinessAuditMapper mapper,BusinessAuditDocumentSavedAuthority authority,BusinessAuditCursor cursors,BusinessAuditPolicy policy,PermissionSchema json)throws Exception{this.access=access;this.mapper=mapper;this.authority=authority;this.cursors=cursors;this.policy=policy;this.json=json;schema=new BusinessAuditSchema();}
 JsonNode read(ApplicationSessionIdentity.Verified subject,String context,BusinessAuditQuery query,String cursor)throws Exception{
  policy.requireConfigured();PermissionSchema.revision(context);if(cursor!=null)WireValues.uuid(cursor);var queryJson=schema.validate("Query",query.tree(json));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
   var owner=new BusinessAuditCursor.Owner(a.context().tenant_id(),a.actorId(),subject.sessionId(),context,a.context().auth_epoch(),a.context().governance_epoch(),schema.hash(queryJson));var after=cursors.resolve(cursor,owner);
   var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("projectId",query.projectId());v.put("action",query.action());v.put("from",LocalDateTime.ofInstant(query.from(),ZoneOffset.UTC));v.put("to",LocalDateTime.ofInstant(query.to(),ZoneOffset.UTC));v.put("actorId",query.actorId());v.put("afterCreatedAt",after==null?null:after.createdAt());v.put("afterAuditId",after==null?null:after.auditId());v.put("limit",policy.maximumScanRows()+1);
   var rows=mapper.scan(v);if(rows.size()>policy.maximumScanRows()+1)bad();var items=json.object().arrayNode();var proofs=new ArrayList<BusinessAuditDocumentSavedAuthority.Proof>();BusinessAuditCursor.Position last=null;int scanned=0;
   for(var row:rows){if(scanned>=policy.maximumScanRows()||items.size()>=query.limit())break;if(row==null||row.createdAt()==null)bad();WireValues.uuid(row.auditId());if(!a.context().tenant_id().equals(row.tenantId())||!query.projectId().equals(row.projectId())||!query.action().equals(row.action())||query.actorId()!=null&&!query.actorId().equals(row.subjectId()))bad();var at=row.createdAt().toInstant(ZoneOffset.UTC);if(at.isBefore(query.from())||!at.isBefore(query.to()))bad();if(after!=null&&!before(row.createdAt(),row.auditId(),after)||last!=null&&!before(row.createdAt(),row.auditId(),last))bad();last=new BusinessAuditCursor.Position(row.createdAt(),row.auditId());scanned++;
    BusinessAuditDocumentSavedAuthority.Proof proof;try{proof=authority.read(a,context,row);}catch(TenantAccessException denial){if(hidden(denial.code()))continue;throw denial;}
    var item=proof.entry();if(query.documentId()!=null&&!query.documentId().equals(item.at("/target/document_id").textValue()))continue;items.add(item);proofs.add(proof);
   }
   for(var proof:proofs)authority.requireCurrent(a,proof);boolean complete=scanned==rows.size();var page=json.object().put("schema_version","1.0.0").put("coverage_scope","DOCUMENT_SAVE_AUDIT").put("pagination_mode","LIVE_AUTHORIZED_SCAN").put("complete",complete);page.set("current_context",json.tree(a.context()));page.set("query",queryJson);page.set("items",items);if(complete)page.putNull("next_cursor");else page.put("next_cursor",cursors.issue(owner,Objects.requireNonNull(last)));var result=schema.validate("Page",page);if(ContentReadAuthorization.canonicalBytes(result).length+512L>policy.maximumResponseBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return result;
  }).get());
 }
 private static boolean before(LocalDateTime at,String id,BusinessAuditCursor.Position p){int c=at.compareTo(p.createdAt());return c<0||c==0&&id.compareTo(p.auditId())<0;}
 private static boolean hidden(ErrorCode c){return c==ErrorCode.ACCESS_DENIED||c==ErrorCode.SOURCE_NOT_READY||c==ErrorCode.SOURCE_MISSING;}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
