package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** DC03 physical save. A human-confirmed capability is necessary but is not itself
 * a saved revision. Intent allocation precedes network I/O; registration, head CAS,
 * adoption consumption and N07 enqueue form one database transaction. */
public final class DocumentCandidateAdoptionSaveService {
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy policy;
    private final DocumentRevisionService revisions;
    private final DocumentCandidateAdoptionService adoptions;
    private final ArtifactRegistryMapper registry;
    private final DependencyManifestSchema dependencies;
    private final DependencyLineage lineage;
    private final JdbcTemplate db;
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final SchemaCatalogue schemas;
    public DocumentCandidateAdoptionSaveService(EnterpriseAccess access,SourceUploadTransport transport,
            DocumentUploadPolicy policy,DocumentRevisionService revisions,DocumentCandidateAdoptionService adoptions,
            ArtifactRegistryMapper registry,DependencyManifestSchema dependencies,DependencyLineage lineage,DataSource data)throws Exception {
        this.transport=transport;this.policy=policy;this.revisions=revisions;this.adoptions=adoptions;
        this.registry=registry;this.dependencies=dependencies;this.lineage=lineage;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        schemas=SchemaCatalogue.fromClasspath();
    }
    /** Constructor private: no arbitrary document/artifact IDs can satisfy consume. */
    public final class Saved {
        private final EnterpriseAccess.Admission admission;
        private final DocumentCandidateAdoptionService.Confirmed confirmed;
        private final String revision,head;
        private final List<Output> outputs;
        private boolean active=true;
        private Saved(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c,String revision,String head,List<Output> outputs){
            admission=a;confirmed=c;this.revision=revision;this.head=head;this.outputs=List.copyOf(outputs);
        }
        public void requireFor(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c)throws Exception {
            if(!active||!TransactionSynchronizationManager.isActualTransactionActive()||admission!=a||confirmed!=c)deny();
            verifyRegistration(c,revision,head,outputs);
        }
        public String documentRevision(){return revision;}
        public String headRevision(){return head;}
        public String bodyArtifactId(){return outputs.get(0).artifactId();}
        public String bodySha256(){return outputs.get(0).reference().sha256();}
        public CommonProtocol.ArtifactRef body()throws Exception{return reference(outputs.get(0));}
        public CommonProtocol.ArtifactRef fields()throws Exception{return reference(outputs.get(1));}
        public CommonProtocol.ArtifactRef evidence()throws Exception{return reference(outputs.get(2));}
    }
    /** Private-minted intermediate capability: normalized persistence cannot be
     * invoked with caller-selected revision/artifact IDs. Never crosses a transaction. */
    public final class Registration {
        private final EnterpriseAccess.Admission admission;
        private final DocumentCandidateAdoptionService.Confirmed confirmed;
        private final Intent intent;
        private final String planHash;
        private boolean active=true;
        private Registration(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c,Intent intent,String planHash){this.admission=a;this.confirmed=c;this.intent=intent;this.planHash=planHash;}
        public void requireFor(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c){if(!active||admission!=a||confirmed!=c||!TransactionSynchronizationManager.isActualTransactionActive())deny();}
        public String revision(){return intent.revision();}
        public String bodyArtifactId(){return intent.outputs().get(0).artifactId();}
        public String accessSetId(){return intent.accessSet();}
        public String planHash(){return planHash;}
    }
    private record Output(String artifactId,String kind,String schemaRef,String schemaHash,S3ObjectWriter.WriteIntent intent,
            String state,InterfaceProtocol.ObjectRef reference) { }
    private record Intent(String revision,String accessSet,List<Output> outputs) { }

    /** Reacquires a fresh complete physical read and transaction-local confirmation
     * for each step. No OriginalReader capability crosses its transaction boundary. */
    public interface ConfirmedProvider {
        <T> T withCurrent(ConfirmedWork<T> work) throws Exception;
    }
    @FunctionalInterface public interface ConfirmedWork<T> {
        T apply(EnterpriseAccess.Admission admission,DocumentCandidateAdoptionService.Confirmed confirmed) throws Exception;
    }
    private record Transfer(Output output,String tenant,byte[] bytes) { }
    JsonNode save(ApplicationSessionIdentity.Verified subject,ConfirmedProvider provider)throws Exception {
        policy.requireConfigured();
        var intent=provider.withCurrent((a,c)->{c.requireUnchanged(a);return prepare(c);});
        for(int index=0;index<3;index++) {
            final int slot=index;
            var transfer=provider.withCurrent((a,c)->{c.requireUnchanged(a);var current=readIntent(c);same(intent.revision(),current.revision());
                var item=current.outputs().get(slot);
                if("PREPARED".equals(item.state()))changed(db.update("UPDATE document_adoption_output SET state='WRITING' WHERE tenant_id=? AND operation_id=? AND artifact_kind=? AND state='PREPARED' AND artifact_id=? AND intent_sha256=?",c.tenantId(),c.operationId(),item.kind(),item.artifactId(),json.hash(item.intent())));
                return new Transfer(item,c.tenantId(),payload(c,intent.revision(),item.kind()));});
            var output=transfer.output();
            var object=transport.writeDocumentProjection(subject,transfer.tenant(),output.intent(),transfer.bytes(),
                !"PREPARED".equals(output.state()),policy.maximumBytes().intValueExact(),
                ()->ThrowingSupplier.of(()->provider.withCurrent((a,c)->{c.requireUnchanged(a);same(intent.revision(),readIntent(c).revision());return null;})).get());
            provider.withCurrent((a,c)->{c.requireUnchanged(a);var actual=readIntent(c).outputs().get(slot);
                same(output.intent(),actual.intent());if(actual.reference()!=null)same(actual.reference(),object);
                if(actual.reference()==null)changed(db.update("UPDATE document_adoption_output SET state='STORED',object_json=? WHERE tenant_id=? AND operation_id=? AND artifact_kind=? AND state='WRITING' AND object_json IS NULL AND artifact_id=? AND intent_sha256=?",json.canonical(object),c.tenantId(),c.operationId(),output.kind(),output.artifactId(),json.hash(output.intent())));
                same(object,readIntent(c).outputs().get(slot).reference());return null;});
        }
        return provider.withCurrent((a,c)->{
            c.requireUnchanged(a); // Last old-head check, before any revision mutation.
            var fixed=readIntent(c);same(intent.revision(),fixed.revision());
            for(var item:fixed.outputs())if(!"STORED".equals(item.state())||item.reference()==null)bad();
            var header=register(a,c,fixed);
            var saved=new Saved(a,c,fixed.revision(),header.revision().toString(),fixed.outputs());
            try {saved.requireFor(a,c);return adoptions.consume(a,c,saved);}
            finally {saved.active=false;}
        });
    }
    private Intent prepare(DocumentCandidateAdoptionService.Confirmed c)throws Exception {
        var existing=db.queryForList("SELECT document_revision FROM document_adoption_save WHERE tenant_id=? AND operation_id=? FOR UPDATE",c.tenantId(),c.operationId());
        if(!existing.isEmpty())return readIntent(c);
        String revision=id(),set=id();var baseline=one(db.queryForList("SELECT body_artifact_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND document_revision=? FOR UPDATE",c.tenantId(),c.projectId(),c.baseDocumentRevision()));
        var binding=one(registry.binding(str(baseline,"body_artifact_id")));
        same(c.tenantId(),binding.tenantId());same(c.projectId(),binding.projectId());same(c.documentId(),binding.resourceId());same("DOCUMENT",binding.resourceKind());same(c.bodySchemaRef(),binding.schemaRef());same(c.bodySchemaSha256(),binding.schemaDocumentSha256());
        var publication=schemas.requirePublished(c.bodySchemaRef(),c.bodySchemaSha256());
        var at=now();
        changed(db.update("INSERT INTO document_adoption_save(tenant_id,operation_id,project_id,document_id,document_revision,access_set_id,body_sha256,created_by,created_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",c.tenantId(),c.operationId(),c.projectId(),c.documentId(),revision,set,c.bodySha256(),c.actorId(),at,"1.0.0",policy.artifactSchemaManifest(),c.classification()));
        for(String kind:List.of("DOCUMENT_BODY","DOCUMENT_FIELDS","DOCUMENT_EVIDENCE")) {
            String artifact=id();byte[] bytes=payload(c,revision,kind);if(bytes.length>policy.maximumBytes().intValueExact())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
            String ref=kind.equals("DOCUMENT_BODY")?binding.schemaRef():schemas.schemaRef(publication.name(),kind.equals("DOCUMENT_FIELDS")?"DocumentFieldProjection":"DocumentEvidenceProjection");
            String hash=kind.equals("DOCUMENT_BODY")?binding.schemaDocumentSha256():publication.schemaSha256();
            var write=new S3ObjectWriter.WriteIntent(id(),"documents/"+c.tenantId()+"/"+c.documentId()+"/"+revision+"/"+artifact,WireValues.sha256(bytes),Integer.toString(bytes.length),"application/json");
            changed(db.update("INSERT INTO document_adoption_output(tenant_id,operation_id,artifact_kind,artifact_id,schema_ref,schema_document_sha256,intent_json,intent_sha256,state,project_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,'PREPARED',?,?,?,?,?,?)",c.tenantId(),c.operationId(),kind,artifact,ref,hash,json.canonical(write),json.hash(write),c.projectId(),"1.0.0",policy.artifactSchemaManifest(),c.actorId(),at,c.classification()));
        }
        return readIntent(c);
    }
    private Intent readIntent(DocumentCandidateAdoptionService.Confirmed c)throws Exception {
        var row=one(db.queryForList("SELECT * FROM document_adoption_save WHERE tenant_id=? AND operation_id=? FOR UPDATE",c.tenantId(),c.operationId()));
        metadataEquals(c,row);same(c.projectId(),str(row,"project_id"));same(c.documentId(),str(row,"document_id"));same(c.bodySha256(),str(row,"body_sha256"));same(c.actorId(),str(row,"created_by"));
        String revision=str(row,"document_revision"),set=str(row,"access_set_id");var outputs=new ArrayList<Output>();
        var base=one(db.queryForList("SELECT body_artifact_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND document_revision=? FOR UPDATE",c.tenantId(),c.projectId(),c.baseDocumentRevision()));
        var baseBinding=one(registry.binding(str(base,"body_artifact_id")));
        same(c.tenantId(),baseBinding.tenantId());same(c.projectId(),baseBinding.projectId());same(c.documentId(),baseBinding.resourceId());same("DOCUMENT",baseBinding.resourceKind());same(c.bodySchemaRef(),baseBinding.schemaRef());same(c.bodySchemaSha256(),baseBinding.schemaDocumentSha256());
        DocumentBodySemantics.published(schemas,schemas.requirePublished(baseBinding.schemaRef(),baseBinding.schemaDocumentSha256()),c.bodyBytes(),policy.bodyLimits());
        var publication=schemas.requirePublished(c.bodySchemaRef(),c.bodySchemaSha256());
        var rows=db.queryForList("SELECT * FROM document_adoption_output WHERE tenant_id=? AND operation_id=? ORDER BY artifact_kind FOR UPDATE",c.tenantId(),c.operationId());
        if(rows.size()!=3)bad();
        for(String kind:List.of("DOCUMENT_BODY","DOCUMENT_FIELDS","DOCUMENT_EVIDENCE")) {
            var output=one(rows.stream().filter(r->kind.equals(str(r,"artifact_kind"))).toList());
            metadataEquals(c,output);same(row.get("created_at"),output.get("created_at"));
            same(kind.equals("DOCUMENT_BODY")?baseBinding.schemaRef():schemas.schemaRef(publication.name(),kind.equals("DOCUMENT_FIELDS")?"DocumentFieldProjection":"DocumentEvidenceProjection"),str(output,"schema_ref"));
            same(kind.equals("DOCUMENT_BODY")?baseBinding.schemaDocumentSha256():publication.schemaSha256(),str(output,"schema_document_sha256"));var node=json.read(str(output,"intent_json"));same(json.hash(node),str(output,"intent_sha256"));
            var intent=new S3ObjectWriter.WriteIntent(node.required("write_id").asText(),node.required("object_key").asText(),node.required("sha256").asText(),node.required("size_bytes").asText(),node.required("media_type").asText());
            byte[] bytes=payload(c,revision,kind);same(WireValues.sha256(bytes),intent.sha256());same(Integer.toString(bytes.length),intent.sizeBytes());
            same("documents/"+c.tenantId()+"/"+c.documentId()+"/"+revision+"/"+str(output,"artifact_id"),intent.objectKey());
            InterfaceProtocol.ObjectRef object=null;
            if(output.get("object_json")!=null){var o=json.read(str(output,"object_json"));object=new InterfaceProtocol.ObjectRef(o.required("object_key").asText(),o.required("object_version").asText(),o.required("sha256").asText(),o.required("size_bytes").asText(),o.required("media_type").asText());
                same(intent.sha256(),object.sha256());same(intent.objectKey(),object.objectKey());same(intent.sizeBytes(),object.sizeBytes());same(intent.mediaType(),object.mediaType());}
            String state=str(output,"state");if(!Set.of("PREPARED","WRITING","STORED").contains(state)||("STORED".equals(state)!=(object!=null)))bad();
            outputs.add(new Output(str(output,"artifact_id"),kind,str(output,"schema_ref"),str(output,"schema_document_sha256"),intent,state,object));
        }
        return new Intent(revision,set,List.copyOf(outputs));
    }
    private byte[] payload(DocumentCandidateAdoptionService.Confirmed c,String revision,String kind)throws Exception {
        var publication=schemas.requirePublished(c.bodySchemaRef(),c.bodySchemaSha256());
        var body=DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,c.body(),c.bodySchemaRef(),c.bodySchemaSha256()).read();
        if(kind.equals("DOCUMENT_BODY"))return c.bodyBytes();
        var value=kind.equals("DOCUMENT_FIELDS")?json.tree(new InterfaceProtocol.DocumentFieldProjection(body.schemaVersion(),revision,c.bodySha256(),body.fields())):
            json.tree(new InterfaceProtocol.DocumentEvidenceProjection(body.schemaVersion(),revision,c.bodySha256(),body.evidenceLinks(),body.factUsages()));
        return schemas.bind(publication.name(),value,kind.equals("DOCUMENT_FIELDS")?"DocumentFieldProjection":"DocumentEvidenceProjection").canonicalBytes();
    }
    private DocumentReadProtocol.Header register(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c,Intent intent)throws Exception {
        String revision=intent.revision(),set=intent.accessSet();var at=now();
        var plan=one(registry.version(c.planRevision()));same(c.projectId(),plan.projectId());same("PLAN_REVISION",plan.entityKind());
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",c.tenantId(),c.projectId(),set,c.contentMembers());
        String dependencyHash=json.hash(manifest);
        version(c,set,"DEPENDENCY_ACCESS_SET",dependencyHash);
        var accessRow=metadata(c,at);accessRow.remove("schema_manifest_sha256");accessRow.remove("schema_version");accessRow.putAll(Map.of("access_set_id",set,"manifest_sha256",dependencyHash,"content_sha256",dependencyHash));insert("dependency_access_set",accessRow);
        var depRow=metadata(c,at);depRow.putAll(Map.of("access_set_id",set,"schema_manifest_sha256",dependencies.manifestSha256(),"manifest_json",json.canonical(manifest),"manifest_sha256",dependencyHash,"content_sha256",dependencyHash,"member_count",c.contentMembers().size()));insert("dependency_access_manifest",depRow);
        for(var member:c.contentMembers()) {var row=metadata(c,at);row.remove("project_id");row.remove("schema_manifest_sha256");row.remove("schema_version");row.putAll(Map.of("access_set_id",set,"upstream_version_id",member.upstreamVersionId(),"rights_revision",member.rightsRevision()));insert("dependency_access_member",row);}
        dependencies.verify(one(registry.manifest(set)),registry.members(set));
        var body=intent.outputs().get(0);version(c,revision,"DOCUMENT_REVISION",c.bodySha256());
        changed(db.update("INSERT INTO document_revision(tenant_id,project_id,document_revision,document_id,snapshot_id,body_sha256,body_object_key) VALUES(?,?,?,?,?,?,?)",c.tenantId(),c.projectId(),revision,c.documentId(),c.snapshotId(),c.bodySha256(),body.reference().objectKey()));
        for(var output:intent.outputs()) {
            version(c,output.artifactId(),"ARTIFACT",output.reference().sha256());
            var row=metadata(c,at);row.putAll(Map.of("artifact_id",output.artifactId(),"document_revision",revision,"artifact_kind",output.kind(),"object_ref",StoredObjectReferences.encode(output.reference()),"object_key",output.reference().objectKey(),"object_version",output.reference().objectVersion(),"sha256",output.reference().sha256(),"size_bytes",output.reference().sizeBytes(),"media_type",output.reference().mediaType(),"dependency_access_set_id",set));row.put("content_sha256",output.reference().sha256());insert("artifact",row);
            var binding=metadata(c,at);binding.remove("schema_manifest_sha256");binding.remove("schema_version");binding.putAll(Map.of("artifact_id",output.artifactId(),"resource_kind","DOCUMENT","resource_id",c.documentId(),"schema_ref",output.schemaRef(),"schema_document_sha256",output.schemaHash(),"dependency_access_set_id",set,"dependency_manifest_sha256",dependencyHash));insert("artifact_content_binding",binding);
            for(String root:c.contentRoots())edge(c,output.artifactId(),root,"DOCUMENT_ADOPTION_INPUT",at);
            lineage.require(output.artifactId(),c.contentMembers(),policy.limits());
        }
        var meta=metadata(c,at);meta.remove("schema_manifest_sha256");
        meta.putAll(Map.of("document_revision",revision,"document_id",c.documentId(),"task_id",c.taskId(),"task_revision",c.taskRevision(),"snapshot_id",c.snapshotId(),"based_on_project_id",c.projectId(),"based_on_document_id",c.documentId(),"based_on_document_revision",c.baseDocumentRevision(),"body_object_version",body.reference().objectVersion(),"schema_revision",c.body().required("schema_version").asText()));
        meta.remove("schema_version");meta.putAll(Map.of("body_schema_revision",c.bodySchemaRevision(),"body_artifact_id",body.artifactId(),"body_sha256",c.bodySha256(),"plan_revision",c.planRevision(),"template_version",c.templateVersion(),"issue_watermark",0,"dependency_access_set_id",set,"content_sha256",c.bodySha256()));insert("document_revision_meta",meta);
        for(String root:c.contentRoots())edge(c,revision,root,"DOCUMENT_INPUT",at);
        edge(c,revision,body.artifactId(),"DOCUMENT_BODY",at);
        for(String provenance:c.provenanceRoots())edge(c,revision,provenance,"DOCUMENT_ADOPTION_PROVENANCE",at);
        var capability=new Registration(a,c,intent,plan.contentSha256());
        DocumentReadProtocol.Header header;
        try {header=revisions.persistAdoptionContent(a,c,capability);} finally {capability.active=false;}
        var receipt=json.tree(Map.of("operation_id",c.operationId(),"document_revision",revision,"head_revision",header.revision().toString(),"body",reference(intent.outputs().get(0)),"fields",reference(intent.outputs().get(1)),"evidence",reference(intent.outputs().get(2))));
        changed(db.update("UPDATE document_adoption_save SET registration_json=?,registration_sha256=? WHERE tenant_id=? AND operation_id=? AND document_revision=? AND body_sha256=? AND registration_json IS NULL",json.canonical(receipt),json.hash(receipt),c.tenantId(),c.operationId(),revision,c.bodySha256()));
        verifyRegistration(c,revision,header.revision().toString(),intent.outputs());return header;
    }
    private void verifyRegistration(DocumentCandidateAdoptionService.Confirmed c,String revision,String head,List<Output> outputs)throws Exception {
        var saved=one(db.queryForList("SELECT s.*,h.current_document_revision,h.revision AS head_revision,m.body_artifact_id,m.body_sha256 AS actual_sha256,m.dependency_access_set_id FROM document_adoption_save s JOIN document_head h ON h.tenant_id=s.tenant_id AND h.project_id=s.project_id AND h.document_id=s.document_id JOIN document_revision_meta m ON m.tenant_id=s.tenant_id AND m.project_id=s.project_id AND m.document_revision=s.document_revision WHERE s.tenant_id=? AND s.operation_id=? FOR UPDATE",c.tenantId(),c.operationId()));
        same(revision,str(saved,"document_revision"));same(revision,str(saved,"current_document_revision"));same(head,str(saved,"head_revision"));same(c.bodySha256(),str(saved,"actual_sha256"));same(outputs.get(0).artifactId(),str(saved,"body_artifact_id"));same(str(saved,"access_set_id"),str(saved,"dependency_access_set_id"));
        var receipt=json.read(str(saved,"registration_json"));same(json.hash(receipt),str(saved,"registration_sha256"));
        same(json.tree(Map.of("operation_id",c.operationId(),"document_revision",revision,"head_revision",head,"body",reference(outputs.get(0)),"fields",reference(outputs.get(1)),"evidence",reference(outputs.get(2)))),receipt);
        for(var output:outputs) {
            var artifact=one(registry.artifact(output.artifactId()));var binding=one(registry.binding(output.artifactId()));
            same(c.tenantId(),artifact.tenantId());same(c.projectId(),artifact.projectId());same(output.kind(),artifact.artifactKind());
            same(output.reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));same(policy.artifactSchemaManifest(),artifact.schemaManifest());same(output.reference().sha256(),artifact.contentSha256());
            same(c.documentId(),binding.resourceId());same("DOCUMENT",binding.resourceKind());same(c.bodySchemaRef(),binding.schemaRef());same(c.bodySchemaSha256(),binding.schemaDocumentSha256());same(output.schemaRef(),binding.schemaRef());same(output.schemaHash(),binding.schemaDocumentSha256());same(str(saved,"access_set_id"),artifact.accessSetId());same(artifact.accessSetId(),binding.accessSetId());
            var dep=one(registry.manifest(artifact.accessSetId()));var members=registry.members(artifact.accessSetId());dependencies.verify(dep,members);same(c.contentMembers(),members);same(dep.manifestSha256(),binding.dependencyManifestSha256());
            lineage.require(output.artifactId(),members,policy.limits());
            var version=one(registry.version(output.artifactId()));same("ARTIFACT",version.entityKind());same(artifact.sha256(),version.contentSha256());same(c.projectId(),version.projectId());
            var row=one(db.queryForList("SELECT document_revision FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? FOR UPDATE",c.tenantId(),c.projectId(),output.artifactId()));same(revision,str(row,"document_revision"));
        }
    }
    private static CommonProtocol.ArtifactRef reference(Output output)throws Exception {
        var object=output.reference();String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(object),0,WireValues.revision(object.sizeBytes()).longValueExact());
        return new CommonProtocol.ArtifactRef(output.artifactId(),object.sha256(),output.schemaRef(),new CommonProtocol.Coverage(range,range,true,null));
    }
    private Map<String,Object> metadata(DocumentCandidateAdoptionService.Confirmed c,LocalDateTime at) {
        var row=new LinkedHashMap<String,Object>();row.put("tenant_id",c.tenantId());row.put("project_id",c.projectId());row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",policy.artifactSchemaManifest());row.put("created_by",c.actorId());row.put("created_at",at);row.put("classification",c.classification());return row;
    }
    private void metadataEquals(DocumentCandidateAdoptionService.Confirmed c,Map<String,Object> row) {
        same(c.tenantId(),str(row,"tenant_id"));same(c.projectId(),str(row,"project_id"));same(c.actorId(),str(row,"created_by"));
        same("1.0.0",str(row,"schema_version"));same(policy.artifactSchemaManifest(),str(row,"schema_manifest_sha256"));same(c.classification(),str(row,"classification"));
        if(row.get("created_at")==null)bad();
    }
    private void version(DocumentCandidateAdoptionService.Confirmed c,String id,String kind,String hash){changed(db.update("INSERT INTO version_registry(tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES(?,?,?,?,?)",c.tenantId(),id,c.projectId(),kind,hash));}
    private void edge(DocumentCandidateAdoptionService.Confirmed c,String consumer,String upstream,String relation,LocalDateTime at){changed(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,created_at) VALUES(?,?,?,?,?,?)",c.tenantId(),consumer,upstream,relation,c.actorId(),at));}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());changed(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray()));}
    private LocalDateTime now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    private static String str(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return value.toString();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
    private static String id(){return UUID.randomUUID().toString();}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
