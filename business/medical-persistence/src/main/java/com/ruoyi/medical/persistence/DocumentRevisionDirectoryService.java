package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;
/** Bounded live authorized history navigation. No body bytes or content-read receipt are produced. */
@Component
@EnableConfigurationProperties(DocumentRevisionDirectoryPolicy.class)
@ConditionalOnProperty(prefix="medical.document-history",name="enabled",havingValue="true")
public final class DocumentRevisionDirectoryService{
 private final EnterpriseAccess access;private final PermissionMapper permissions;private final ResourceReadAuthority resources;private final DocumentReadService documents;
 private final DocumentRevisionDirectoryMapper mapper;private final DocumentRevisionDirectoryPolicy policy;private final DocumentRevisionDirectoryCursor cursors;private final PermissionSchema json;private final DocumentRevisionDirectorySchema schema;
 public DocumentRevisionDirectoryService(EnterpriseAccess access,PermissionMapper permissions,ResourceReadAuthority resources,DocumentReadService documents,DocumentRevisionDirectoryMapper mapper,DocumentRevisionDirectoryPolicy policy,DocumentRevisionDirectoryCursor cursors,PermissionSchema json)throws Exception{
  this.access=access;this.permissions=permissions;this.resources=resources;this.documents=documents;this.mapper=mapper;this.policy=policy;this.cursors=cursors;this.json=json;schema=new DocumentRevisionDirectorySchema();
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String document,int pageSize,String cursor)throws Exception{
  policy.requireConfigured();WireValues.uuid(document);PermissionSchema.revision(context);if(pageSize<1||pageSize>policy.maximumPageSize())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(cursor!=null)WireValues.uuid(cursor);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
   var owner=one(permissions.resourceOwner("DOCUMENT",document));if(owner.projectId()==null||!document.equals(owner.id()))deny();
   var scope=new PermissionProtocol.Scope(a.context().tenant_id(),owner.projectId(),"DOCUMENT",document);resources.require(a,scope,"document.read","DOCUMENT_BODY");
   var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("projectId",owner.projectId());v.put("documentId",document);
   var head=one(mapper.head(v));same(a.context().tenant_id(),head.tenantId());same(owner.projectId(),head.projectId());same(document,head.documentId());if(head.currentDocumentRevision()!=null)WireValues.uuid(head.currentDocumentRevision());PermissionSchema.revision(head.revision().toString());
   var cursorOwner=new DocumentRevisionDirectoryCursor.Owner(a.context().tenant_id(),a.actorId(),subject.sessionId(),context,a.context().auth_epoch(),a.context().governance_epoch(),document,head.revision().toString(),pageSize);
   var after=cursors.resolve(cursor,cursorOwner);v.put("afterCreatedAt",after==null?null:after.createdAt());v.put("afterDocumentRevision",after==null?null:after.revision());v.put("limit",policy.maximumScanRows()+1);
   var candidates=mapper.scan(v);if(candidates.size()>policy.maximumScanRows()+1)mismatch();
   var items=json.object().arrayNode();var proofs=new ArrayList<DocumentReadService.MetadataRead>();DocumentRevisionDirectoryCursor.Position last=null;int scanned=0;
   for(var row:candidates){if(scanned>=policy.maximumScanRows()||items.size()>=pageSize)break;
    WireValues.uuid(row.documentRevision());WireValues.hash(row.bodySha256());same(row.bodySha256(),row.contentSha256());if(row.createdAt()==null)mismatch();
    if(after!=null&&!before(row.createdAt(),row.documentRevision(),after))mismatch();if(last!=null&&!before(row.createdAt(),row.documentRevision(),last))mismatch();
    scanned++;last=new DocumentRevisionDirectoryCursor.Position(row.createdAt(),row.documentRevision());
    var refs=new HashMap<>(v);refs.put("documentRevision",row.documentRevision());refs.put("limit",policy.maximumReferences()+1);
    var checks=bounded(mapper.checks(refs));var reviews=bounded(mapper.reviews(refs));var signatures=bounded(mapper.signatures(refs));
    DocumentReadService.MetadataRead proof;
    try{proof=documents.authorizeMetadata(a,context,document,row.documentRevision());}
    catch(TenantAccessException failure){if(hidden(failure.code()))continue;throw failure;}
    var h=proof.header();same(document,h.documentId());same(owner.projectId(),h.projectId());same(row.documentRevision(),h.documentRevision());same(row.bodySha256(),h.bodySha256());same(row.createdAt(),h.createdAt());same(row.createdBy(),h.createdBy());same(head.currentDocumentRevision(),h.currentDocumentRevision());same(head.revision(),h.revision());
    var item=json.object().put("document_revision",row.documentRevision()).put("body_sha256",row.bodySha256()).put("created_by",row.createdBy()).put("created_at",row.createdAt().toInstant(ZoneOffset.UTC).toString())
      .put("is_current",row.documentRevision().equals(head.currentDocumentRevision())).put("read_url","/api/documents/"+document+"/revisions/"+row.documentRevision());
    item.set("check_ids",json.tree(checks));item.set("review_ids",json.tree(reviews));item.set("signature_ids",json.tree(signatures));items.add(item);proofs.add(proof);
   }
   // Rights and all accepted metadata must still be current in this tenant transaction.
   for(var proof:proofs)documents.requireMetadataCurrent(a,proof);
   resources.require(a,scope,"document.read","DOCUMENT_BODY");same(head,one(mapper.head(v)));
   boolean complete=scanned==candidates.size();String next=complete?null:cursors.issue(cursorOwner,Objects.requireNonNull(last));
   var page=json.object().put("schema_version","1.0.0").put("document_id",document).put("current_document_revision",head.currentDocumentRevision()).put("document_head_revision",head.revision().toString()).put("complete",complete);
   page.set("items",items);if(next==null)page.putNull("next_cursor");else page.put("next_cursor",next);return schema.page(page);
  }).get());
 }
 private List<String> bounded(List<String> refs){if(refs.size()>policy.maximumReferences())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var unique=new HashSet<String>();for(var id:refs){WireValues.uuid(id);if(!unique.add(id))mismatch();}return List.copyOf(refs);}
 private static boolean hidden(ErrorCode c){return c==ErrorCode.ACCESS_DENIED||c==ErrorCode.SOURCE_NOT_READY||c==ErrorCode.SOURCE_MISSING;}
 private static boolean before(LocalDateTime time,String id,DocumentRevisionDirectoryCursor.Position p){int order=time.compareTo(p.createdAt());return order<0||order==0&&id.compareTo(p.revision())<0;}
 private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
}
