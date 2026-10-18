package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;
/** Reuses only an existing real fixed document/snapshot. No task creation or dynamic source expansion. */
@Component
public final class GruContextAuthority {
 static final String PURPOSE="DOCUMENT_BODY";
 private final DocumentReadService documents;private final ResourceReadAuthority resources;private final TaskMapper tasks;
 private final ScopeSnapshotSchema snapshots;private final SourceReadAuthority sources;private final CollectionAuthority collections;
 private final DocumentUploadPolicy policy;private final GruSchema schema;
 public GruContextAuthority(DocumentReadService documents,ResourceReadAuthority resources,TaskMapper tasks,ScopeSnapshotSchema snapshots,
  SourceReadAuthority sources,CollectionAuthority collections,DocumentUploadPolicy policy)throws Exception{
  this.documents=documents;this.resources=resources;this.tasks=tasks;this.snapshots=snapshots;this.sources=sources;this.collections=collections;this.policy=policy;schema=new GruSchema();
 }
 void project(EnterpriseAccess.Admission a,String context,String project)throws Exception {
  if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"project.read",PURPOSE);
 }
 void authorize(EnterpriseAccess.Admission a,String context,JsonNode request,boolean writing)throws Exception {
  if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  String project=request.required("project_id").textValue(),document=request.required("document_id").textValue();
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"project.read",PURPOSE);
  var scope=new PermissionProtocol.Scope(a.context().tenant_id(),project,"DOCUMENT",document);
  resources.require(a,scope,"document.read",PURPOSE);resources.require(a,scope,writing?"conversation.use":"conversation.read",PURPOSE);
 }
 JsonNode describe(EnterpriseAccess.Admission a,String context,DocumentReadService.FixedRead proof)throws Exception {
  var h=proof.header();var snapshot=one(tasks.snapshot(a.actorId(),h.projectId(),h.snapshotId()));
  var manifest=schema.read(snapshot.manifest());
  var request=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0")
   .put("project_id",h.projectId()).put("document_id",h.documentId()).put("document_revision",h.documentRevision())
   .put("scope_snapshot_id",h.snapshotId()).put("reason","读取已有固定文档上下文");
  request.set("scope",manifest.required("scope_selection"));schema.validate("Create",request);verify(a,context,request,proof,false);
  request.remove("reason");return schema.response("Context",request);
 }
 TaskProtocol.ScopeSnapshotManifest verify(EnterpriseAccess.Admission a,String context,JsonNode request,DocumentReadService.FixedRead proof,boolean writing)throws Exception{
  authorize(a,context,request,writing);documents.requireCurrent(a,proof);
  var h=proof.header();same(a.context().tenant_id(),h.tenantId());same(request.required("project_id").textValue(),h.projectId());
  same(request.required("document_id").textValue(),h.documentId());same(request.required("document_revision").textValue(),h.documentRevision());
  same(request.required("scope_snapshot_id").textValue(),h.snapshotId());
  var snapshot=one(tasks.snapshot(a.actorId(),h.projectId(),h.snapshotId()));same(snapshot.manifestHash(),snapshot.registryHash());
  var fixed=snapshots.require(snapshot.manifest(),snapshot.manifestHash(),h.tenantId(),h.projectId(),h.snapshotId(),h.profileRevision(),snapshot.authEpoch(),snapshot.governanceEpoch(),schema.scope(request.required("scope")),tasks.members(a.actorId(),h.projectId(),h.snapshotId()));
  if(fixed.scope_selection().networkEnabled())throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  // Match the existing fixed-document read boundary. Recording new messages still
  // requires formal readiness; historical read/receipt does not reopen old source selection.
  var use=!writing&&!h.documentRevision().equals(h.currentDocumentRevision())
   ?SourceReadProtocol.Use.HISTORY:SourceReadProtocol.Use.FORMAL;
  var selected=new TreeSet<String>();for(var m:fixed.members()){
   var source=sources.require(a,new SourceReadProtocol.ReadRequirement(m.source_version(),m.rights_revision(),h.projectId(),h.snapshotId(),m.parse_version(),null,
    use,PURPOSE,policy.environment(),policy.region(),null));ScopeSnapshotSchema.requireSource(m,source);selected.add(m.source_version());
  }
  var collectionSources=new TreeSet<String>();for(String id:fixed.scope_selection().collectionVersions()){
   var checked=collections.require(a,id,PURPOSE,policy.environment(),policy.region(),new DependencyLineage.Limits(512,2048));
   if(checked.head().projectId()!=null&&!h.projectId().equals(checked.head().projectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   collectionSources.addAll(checked.manifest().members().stream().map(CollectionProtocol.Member::source_version).toList());
   for(var member:checked.manifest().members()){
    var saved=fixed.members().stream().filter(m->m.source_version().equals(member.source_version())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
    same(saved.parse_version(),member.parse_version());
   }
  }
  // Existing immutable snapshot must exactly cover the explicit collection/source union. PROJECT_APPROVED never re-enumerates today's project.
  var mode=fixed.scope_selection().mode();if(mode==CommonProtocol.ScopeMode.COLLECTION_VERSIONS||mode==CommonProtocol.ScopeMode.COMBINED){
   collectionSources.addAll(fixed.scope_selection().sourceVersions());if(!collectionSources.equals(selected))mismatch();
  }
  same(h.accessSetId(),proof.dependencies().access_set_id());return fixed;
 }
 private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
