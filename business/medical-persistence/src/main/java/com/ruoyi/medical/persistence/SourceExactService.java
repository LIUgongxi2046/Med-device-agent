package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties(SourceExactPolicy.class)
public final class SourceExactService {
    private final EnterpriseAccess access;
    private final SourceExactMapper mapper;
    private final SourceReadMapper sourceMapper;
    private final SourceReadAuthority sources;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final SourceExactSchema schema;
    private final PermissionSchema json;
    private final SourceExactPolicy policy;
    private final RetrievalPolicy retrievals;
    private final IdentityDirectoryPolicy identities;
    private final OperationSchema operations;
    private final RetrievalSchema reading;
    private final JdbcTemplate jdbc;

    public SourceExactService(EnterpriseAccess access,SourceExactMapper mapper,SourceReadMapper sourceMapper,
            SourceReadAuthority sources,ResourceReadAuthority resources,PermissionMapper permissions,SourceExactSchema schema,
            PermissionSchema json,SourceExactPolicy policy,RetrievalPolicy retrievals,IdentityDirectoryPolicy identities,
            OperationSchema operations,RetrievalSchema reading,@Qualifier("masterDataSource") DataSource master) {
        this.access=access; this.mapper=mapper; this.sourceMapper=sourceMapper; this.sources=sources; this.resources=resources;
        this.permissions=permissions; this.schema=schema; this.json=json; this.policy=policy; this.retrievals=retrievals;
        this.identities=identities; this.operations=operations; this.reading=reading; jdbc=new JdbcTemplate(master);
    }

    public JsonNode publish(ApplicationSessionIdentity.Verified subject,String context,String key,String source,
            SourceExactProtocol.PublicationInput input) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); WireValues.uuid(source); policy.requireConfigured();
            if(input.fields().size()>policy.maximumFields()) fail(ErrorCode.CONTEXT_LIMIT);
            var proof=basis(admission,source,input); requireApproval(admission,proof);
            var values=values(admission); values.put("sourceVersion",source); putBasis(values,input);
            values.put("operationName","KN-EXACT-PUBLISH"); values.put("key",key);
            values.put("requestHash",schema.requestHash("/api/source-versions/"+source+"/exact-fields",input,context,subject.sessionId()));
            var previous=mapper.operation(values);
            if(!previous.isEmpty()) {
                var response=replay(previous,values,"PublicationReceipt"); values.put("fieldRevision",single(previous).targetId());
                if(!verifiedRevision(admission,values).equals(response.required("data").required("manifest"))) mismatch(); return response;
            }
            var heads=mapper.head(values); if(heads.size()>1) mismatch();
            String expected=heads.isEmpty()?"0":integer(heads.get(0),"revision").toString();
            if(!expected.equals(input.expected_revision())) fail(ErrorCode.REVISION_CONFLICT);
            verifyFields(values,input.fields());
            String fieldRevision=UUID.randomUUID().toString(),revision=PermissionSchema.next(expected);
            var now=Objects.requireNonNull(permissions.databaseTime());
            var manifest=json.object(); manifest.put("schema_version","1.0.0"); manifest.put("tenant_id",admission.context().tenant_id());
            manifest.put("source_version",source); manifest.put("field_revision",fieldRevision); manifest.put("revision",revision);
            manifest.put("original_sha256",proof.source().originalSha256()); manifest.set("request",json.tree(input));
            manifest.put("actor_id",subject.user().user_id()); manifest.put("created_at",time(now)); schema.require("Manifest",manifest);
            String hash=hash(manifest); values.put("fieldRevision",fieldRevision); values.put("versionId",fieldRevision);
            values.put("entityKind","SOURCE_EXACT_REVISION"); values.put("ownerProjectId",proof.source().ownerProjectId());
            values.put("contentSha256",hash); values.put("manifest",json.canonical(manifest)); values.put("now",now);
            values.put("fieldCount",input.fields().size()); values.put("expectedRevision",expected); values.put("resultingRevision",revision);
            one(mapper.saveVersion(values)); one(mapper.saveRevision(values));
            for(int ordinal=0;ordinal<input.fields().size();ordinal++) {
                var field=input.fields().get(ordinal); values.put("ordinal",ordinal); values.put("field",field);
                values.put("rawSha256",textHash(field.raw_value())); values.put("aliasSha256",field.alias_value()==null?null:textHash(field.alias_value()));
                values.put("fieldManifest",json.canonicalObject(field)); one(mapper.saveField(values));
            }
            if(heads.isEmpty()) one(mapper.createHead(values)); else one(mapper.updateHead(values));
            var parents=new TreeSet<>(List.of(source,input.parse_version(),input.index_version(),input.approval_revision(),
                input.rights_revision(),input.binding_revision()));
            for(String parent:parents) { values.put("upstreamId",parent); values.put("relationKind","EXACT_FIELD_BASIS"); one(mapper.saveLineage(values)); }
            var stored=verifiedRevision(admission,values);
            if(!stored.equals(manifest) || !fieldRevision.equals(text(single(mapper.head(values)),"field_revision"))
                    || !new BigInteger(revision).equals(integer(single(mapper.head(values)),"revision"))) mismatch();
            if(!proof.equals(basis(admission,source,input))) fail(ErrorCode.SCOPE_STALE); requireApproval(admission,proof);
            var currentContext=advanceContext(admission,values);
            String operationId=UUID.randomUUID().toString(),requestId=UUID.randomUUID().toString();
            var receipt=json.object(); receipt.put("schema_version","1.0.0"); receipt.put("operation_id",operationId);
            receipt.put("source_version",source); receipt.put("field_revision",fieldRevision); receipt.put("revision",revision);
            receipt.put("manifest_sha256",hash); receipt.put("field_count",input.fields().size()); receipt.set("manifest",manifest);
            receipt.set("current_context",currentContext);
            var response=schema.envelope("PublicationReceipt",receipt,requestId,revision);
            saveOperation(values,operationId,fieldRevision,response); audit(admission,input.project_id(),"source.exact_fields_approved",fieldRevision,hash,requestId);
            var event=json.object(); event.put("source_version",source); event.put("field_revision",fieldRevision); event.put("manifest_sha256",hash);
            event.put("field_count",input.fields().size());
            one(permissions.saveEvent(subject.user().user_id(),UUID.randomUUID().toString(),source,revision,"source.exact_fields_approved",json.canonical(event)));
            if(!response.equals(replay(mapper.operation(values),values,"PublicationReceipt"))) mismatch(); return response;
        }).get());
    }

    public JsonNode fields(ApplicationSessionIdentity.Verified subject,String context,String source,String revision,String project) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); policy.requireConfigured(); WireValues.uuid(source); WireValues.uuid(revision); WireValues.uuid(project);
            var values=values(admission); values.put("sourceVersion",source); values.put("fieldRevision",revision); values.put("projectId",project);
            var manifest=verifiedRevision(admission,values); var input=schema.storedInput(manifest.required("request"));
            if(!input.project_id().equals(project)) fail(ErrorCode.ACCESS_DENIED);
            var proof=basis(admission,source,input); if(!proof.source().originalSha256().equals(manifest.required("original_sha256").textValue())) mismatch();
            return schema.envelope("Manifest",manifest,UUID.randomUUID().toString(),manifest.required("revision").textValue());
        }).get());
    }

    public JsonNode currentFields(ApplicationSessionIdentity.Verified subject,String context,String source,String project,String parse,String approval) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); policy.requireConfigured();
            for(String id:List.of(source,project,parse,approval)) WireValues.uuid(id);
            var values=values(admission); values.put("sourceVersion",source); values.put("projectId",project);
            values.put("parseVersion",parse); values.put("approvalRevision",approval); var head=single(mapper.head(values));
            values.put("fieldRevision",text(head,"field_revision")); var manifest=verifiedRevision(admission,values);
            var input=schema.storedInput(manifest.required("request"));
            if(!input.project_id().equals(project) || !parse.equals(input.parse_version()) || !approval.equals(input.approval_revision())
                    || !integer(head,"revision").toString().equals(manifest.required("revision").textValue())) mismatch();
            basis(admission,source,input);
            return schema.envelope("Manifest",manifest,UUID.randomUUID().toString(),manifest.required("revision").textValue());
        }).get());
    }

    public JsonNode search(ApplicationSessionIdentity.Verified subject,String context,String key,SourceExactProtocol.SearchInput input) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); policy.requireConfigured(); requireProject(admission,input.project_id());
            var values=values(admission); values.put("projectId",input.project_id()); values.put("fieldKind",input.field_kind());
            values.put("queryText",input.query()); values.put("querySha256",textHash(input.query())); values.put("operationName","KN-14-EXACT");
            values.put("key",key); values.put("requestHash",schema.requestHash("/api/retrievals/exact",input,context,subject.sessionId()));
            var previous=mapper.operation(values); if(!previous.isEmpty()) return replay(previous,values,null);
            values.put("matchLimit",policy.maximumMatches()+1); values.put("environment",retrievals.environment()); values.put("region",retrievals.region());
            var candidates=mapper.candidates(values); if(candidates.size()>policy.maximumMatches()) fail(ErrorCode.CONTEXT_LIMIT);
            var revisions=new HashMap<String,JsonNode>(); var selected=new LinkedHashMap<String,ObjectNode>();
            for(var candidate:candidates) {
                String source=text(candidate,"source_version"),fieldRevision=text(candidate,"field_revision"),chunk=text(candidate,"chunk_id");
                values.put("sourceVersion",source); values.put("fieldRevision",fieldRevision);
                if(!revisions.containsKey(fieldRevision)) revisions.put(fieldRevision,verifiedRevision(admission,values));
                var manifest=revisions.get(fieldRevision); var published=schema.storedInput(manifest.required("request"));
                int ordinal=integer(candidate,"ordinal").intValueExact(); if(ordinal>=published.fields().size()) mismatch();
                var field=published.fields().get(ordinal);
                if(!json.tree(field).equals(schema.parse(text(candidate,"field_manifest"))) || !field.field_kind().equals(input.field_kind())
                        || !field.raw_value().equals(input.query()) && !Objects.equals(field.alias_value(),input.query())) mismatch();
                schema.verifyField(field,chunk,text(candidate,"raw_text"),text(candidate,"quote_hash"),text(candidate,"locator"));
                var proof=candidateBasis(admission,input.project_id(),candidate);
                if(!proof.source().originalSha256().equals(manifest.required("original_sha256").textValue())) mismatch();
                String identity=source+"/"+text(candidate,"parse_version")+"/"+chunk;
                var hit=hit(candidate,manifest); var old=selected.get(identity);
                if(old==null) {
                    hit.put("rank",selected.size()+1); hit.set("matches",json.object().arrayNode()); selected.put(identity,hit); old=hit;
                    if(selected.size()>retrievals.maximumResults()) fail(ErrorCode.CONTEXT_LIMIT);
                } else {
                    var comparable=old.deepCopy(); comparable.remove(List.of("rank","matches"));
                    if(!comparable.equals(hit)) mismatch();
                }
                var match=json.object(); match.put("ordinal",ordinal); match.set("field",json.tree(field));
                var matches=(ArrayNode)old.required("matches");
                if(!matches.isEmpty() && matches.get(matches.size()-1).required("ordinal").intValue()>=ordinal) mismatch(); matches.add(match);
            }
            var items=json.object().arrayNode(); for(var item:selected.values()) { schema.require("Hit",item); items.add(item); }
            String retrievalId=UUID.randomUUID().toString(),operationId=UUID.randomUUID().toString(),requestId=UUID.randomUUID().toString();
            String selection=selectionHash(input,items); var now=Objects.requireNonNull(permissions.databaseTime());
            values.put("retrievalId",retrievalId); values.put("versionId",retrievalId); values.put("ownerProjectId",input.project_id());
            values.put("entityKind","EXACT_RETRIEVAL"); values.put("contentSha256",selection); values.put("requestManifest",json.canonicalObject(input));
            values.put("now",now); values.put("resultCount",items.size()); values.put("matchCount",candidates.size()); values.put("status",items.isEmpty()?"EMPTY":"FOUND");
            values.put("authEpoch",admission.context().auth_epoch()); values.put("governanceEpoch",admission.context().governance_epoch());
            one(mapper.saveVersion(values)); one(mapper.saveQuery(values)); var dependencies=new TreeSet<String>();
            for(var item:items) {
                putHit(values,item); values.put("hitManifest",json.canonical(item)); one(mapper.saveHit(values));
                for(String name:List.of("source_version","parse_version","index_version","approval_revision","rights_revision","binding_revision","field_revision"))
                    dependencies.add(item.required(name).textValue());
            }
            for(String id:dependencies) {
                values.put("upstreamId",id); values.put("relationKind","EXACT_RETRIEVAL_INPUT"); one(mapper.saveLineage(values));
                values.put("holdId",UUID.randomUUID().toString()); one(mapper.saveHold(values));
            }
            var operation=new InterfaceProtocol.Operation(operationId,retrievalId,InterfaceProtocol.OperationStatus.COMPLETED,"/api/operations/"+operationId);
            var response=operations.operation(operation,requestId); saveOperation(values,operationId,retrievalId,response);
            var stored=verifiedQuery(admission,values); if(!stored.items().equals(items) || !stored.input().equals(input)) mismatch();
            audit(admission,input.project_id(),"retrieval.exact_created",retrievalId,selection,requestId);
            if(!response.equals(replay(mapper.operation(values),values,null))) mismatch(); return response;
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id,RetrievalProtocol.PageQuery page) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); policy.requireConfigured(); WireValues.uuid(id);
            var values=values(admission); values.put("retrievalId",id); var query=verifiedQuery(admission,values);
            var row=query.row(); var input=query.input(); var items=query.items();
            long offset=Math.multiplyExact((long)page.page()-1,page.pageSize());
            if(offset>Integer.MAX_VALUE) fail(ErrorCode.CONTEXT_LIMIT);
            if(offset>=items.size() && (offset>0 || !items.isEmpty())) fail(ErrorCode.OBJECT_UNAVAILABLE);
            int start=Math.toIntExact(offset),end=Math.min(items.size(),Math.addExact(start,page.pageSize()));
            var result=json.object(); result.put("schema_version","1.0.0"); result.put("retrieval_id",id); result.put("project_id",input.project_id());
            result.put("field_kind",input.field_kind()); result.put("query",input.query()); result.put("purpose",input.purpose()); result.put("mode","EXACT");
            result.put("status",text(row,"status")); result.put("created_at",time(date(row,"created_at")));
            result.put("selection_sha256",text(row,"selection_sha256")); result.put("total",Integer.toString(items.size()));
            result.put("page",page.page()); result.put("page_size",page.pageSize());
            if(end<items.size()) result.put("next_page",Math.addExact(page.page(),1)); else result.putNull("next_page");
            var delivered=json.object().arrayNode(); for(int index=start;index<end;index++) delivered.add(items.get(index)); result.set("items",delivered);
            return schema.envelope("Page",result,UUID.randomUUID().toString(),null);
        }).get());
    }

    /** Read-only original verification for EVAL. No human session, publication or retrieval is minted. */
    JsonNode requireEvaluationOriginal(EnterpriseAccess.Admission admission,String project,StrictFieldComparisonProtocol.SourceIdentity identity)throws Exception {
        FormalEvaluationExecutionAdmission.current(admission);policy.requireConfigured();var evidence=Objects.requireNonNull(identity.evidenceRef());var pin=Objects.requireNonNull(identity.sourceExact());
        if(!"SOURCE_EXACT_REVISION".equals(identity.kind())||identity.parsedDecimal()!=null||!project.equals(evidence.projectId())||!admission.context().tenant_id().equals(evidence.tenantId()))mismatch();
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("identityMode",admission.bindingView().mode());values.put("schemaManifest",schema.manifestSha256());values.put("classification",identities.classification());values.put("projectId",project);values.put("sourceVersion",evidence.sourceVersion());values.put("fieldRevision",pin.fieldRevision());
        var manifest=verifiedRevision(admission,values);if(!hash(manifest).equals(pin.fieldManifestSha256()))mismatch();var input=schema.storedInput(manifest.required("request"));if(pin.ordinal()<0||pin.ordinal()>=input.fields().size())mismatch();var field=input.fields().get(pin.ordinal());
        if(!field.field_kind().equals(pin.fieldKind())||!field.chunk_id().equals(evidence.chunkId())||!field.quote_hash().equals(evidence.quoteHash())||!input.parse_version().equals(evidence.parseVersion())||!input.approval_revision().equals(evidence.approvalRevision()))mismatch();var current=basis(admission,evidence.sourceVersion(),input);requireApproval(admission,current);
        return json.tree(Map.of("source",identity,"field",field,"manifest",manifest));
    }

    private JsonNode verifiedRevision(EnterpriseAccess.Admission admission,Map<String,Object> values) throws IOException {
        var row=single(mapper.revision(values)); var manifest=schema.parse(text(row,"manifest")); schema.require("Manifest",manifest);
        var input=schema.storedInput(manifest.required("request")); String source=text(row,"source_version");
        if(!source.equals(values.get("sourceVersion")) || !text(row,"field_revision").equals(values.get("fieldRevision"))
                || !text(row,"tenant_id").equals(admission.context().tenant_id())
                || !text(row,"manifest_sha256").equals(hash(manifest)) || !text(row,"registered_sha256").equals(hash(manifest))
                || !"SOURCE_EXACT_REVISION".equals(text(row,"entity_kind"))
                || !Objects.equals(row.get("owner_project_id"),row.get("registered_project_id"))
                || !source.equals(manifest.required("source_version").textValue()) || !text(row,"field_revision").equals(manifest.required("field_revision").textValue())
                || !text(row,"tenant_id").equals(manifest.required("tenant_id").textValue())
                || !text(row,"created_by").equals(manifest.required("actor_id").textValue())
                || !time(date(row,"created_at")).equals(manifest.required("created_at").textValue())
                || !integer(row,"publication_revision").toString().equals(manifest.required("revision").textValue())
                || !schema.manifestSha256().equals(text(row,"schema_manifest_sha256")) || !"1.0.0".equals(text(row,"schema_version"))) mismatch();
        for(var entry:Map.of("parse_version",input.parse_version(),"index_version",input.index_version(),
                "approval_revision",input.approval_revision(),"rights_revision",input.rights_revision()).entrySet())
            if(!entry.getValue().equals(text(row,entry.getKey()))) mismatch();
        if(!integer(row,"field_count").equals(BigInteger.valueOf(input.fields().size()))) mismatch();
        if(input.fields().size()>policy.maximumFields()) fail(ErrorCode.CONTEXT_LIMIT);
        String project=(String)Objects.requireNonNull(values.get("projectId"));
        var current=sources.require(admission,new SourceReadProtocol.ReadRequirement(source,input.rights_revision(),project,null,null,null,
            SourceReadProtocol.Use.REVIEW,rightsPurpose(source,input.rights_revision()),retrievals.environment(),retrievals.region(),null));
        if(!current.source().originalSha256().equals(manifest.required("original_sha256").textValue())) mismatch();
        values.put("parseVersion",input.parse_version());
        values.put("fieldLimit",policy.maximumFields()+1); var fields=mapper.fields(values);
        if(fields.size()!=input.fields().size()) mismatch();
        for(int ordinal=0;ordinal<fields.size();ordinal++) {
            var field=input.fields().get(ordinal); var item=fields.get(ordinal);
            if(!BigInteger.valueOf(ordinal).equals(integer(item,"ordinal")) || !json.tree(field).equals(schema.parse(text(item,"field_manifest")))
                    || !field.field_kind().equals(text(item,"field_kind")) || !field.raw_value().equals(text(item,"raw_value"))
                    || !textHash(field.raw_value()).equals(text(item,"raw_value_sha256")) || !Objects.equals(field.alias_value(),item.get("alias_value"))
                    || !Objects.equals(field.alias_value()==null?null:textHash(field.alias_value()),item.get("alias_value_sha256"))
                    || !field.chunk_id().equals(text(item,"chunk_id"))) mismatch();
            for(String name:List.of("source_version","parse_version","approval_revision","field_revision","schema_version","schema_manifest_sha256","created_by","created_at","classification"))
                if(!Objects.equals(row.get(name),item.get(name))) mismatch();
        }
        verifyFields(values,input.fields()); return manifest;
    }

    private record StoredQuery(Map<String,Object> row,SourceExactProtocol.SearchInput input,ArrayNode items) { }
    private StoredQuery verifiedQuery(EnterpriseAccess.Admission admission,Map<String,Object> values) throws IOException {
        var row=single(mapper.query(values));
        if(!admission.context().tenant_id().equals(text(row,"tenant_id")) || !admission.subject().user().user_id().equals(text(row,"subject_id"))
                || !text(row,"subject_id").equals(text(row,"created_by")) || !values.get("retrievalId").equals(text(row,"retrieval_id"))
                || !"EXACT_RETRIEVAL".equals(text(row,"entity_kind")) || !"1.0.0".equals(text(row,"schema_version"))
                || !text(row,"project_id").equals(text(row,"registered_project_id"))
                || !schema.manifestSha256().equals(text(row,"schema_manifest_sha256"))) mismatch();
        var input=schema.search(text(row,"request_manifest").getBytes(StandardCharsets.UTF_8));
        if(!input.project_id().equals(text(row,"project_id")) || !textHash(input.query()).equals(text(row,"query_sha256"))) mismatch();
        requireProject(admission,input.project_id());
        values.put("projectId",input.project_id());
        int expected=integer(row,"result_count").intValueExact();
        if(expected>retrievals.maximumResults() || integer(row,"match_count").compareTo(BigInteger.valueOf(policy.maximumMatches()))>0) fail(ErrorCode.CONTEXT_LIMIT);
        values.put("hitLimit",retrievals.maximumResults()+1); var hits=mapper.hits(values);
        if(hits.size()!=expected || !text(row,"status").equals(expected==0?"EMPTY":"FOUND")) mismatch();
        var items=json.object().arrayNode(); var revisions=new HashMap<String,JsonNode>(); int matched=0;
        for(int index=0;index<hits.size();index++) {
            var saved=hits.get(index); var hit=schema.parse(text(saved,"hit_manifest")); schema.require("Hit",hit);
            if(hit.required("rank").intValue()!=index+1 || !BigInteger.valueOf(index+1).equals(integer(saved,"rank_no"))) mismatch();
            for(String name:List.of("source_version","parse_version","index_version","approval_revision","rights_revision","binding_revision","chunk_id","field_revision"))
                if(!hit.required(name).textValue().equals(text(saved,name))) mismatch();
            for(String name:List.of("schema_version","schema_manifest_sha256","created_by","created_at","classification"))
                if(!Objects.equals(saved.get(name),row.get(name))) mismatch();
            putHit(values,hit); String fieldRevision=hit.required("field_revision").textValue();
            if(!revisions.containsKey(fieldRevision)) revisions.put(fieldRevision,verifiedRevision(admission,values));
            var manifest=revisions.get(fieldRevision); var published=schema.storedInput(manifest.required("request"));
            for(var entry:Map.of("parse_version",published.parse_version(),"index_version",published.index_version(),
                    "approval_revision",published.approval_revision(),"rights_revision",published.rights_revision()).entrySet())
                if(!entry.getValue().equals(hit.required(entry.getKey()).textValue())) mismatch();
            if(!hash(manifest).equals(hit.required("field_manifest_sha256").textValue())) mismatch();
            var chunk=single(mapper.chunk(values)); var matches=hit.required("matches"); int previous=-1;
            for(var match:matches) {
                int ordinal=match.required("ordinal").intValue(); if(ordinal<=previous || ordinal>=published.fields().size()) mismatch(); previous=ordinal;
                var field=published.fields().get(ordinal);
                if(!json.tree(field).equals(match.required("field")) || !field.field_kind().equals(input.field_kind())
                        || !field.raw_value().equals(input.query()) && !Objects.equals(field.alias_value(),input.query())) mismatch();
                schema.verifyField(field,text(chunk,"chunk_id"),text(chunk,"raw_text"),text(chunk,"quote_hash"),text(chunk,"locator")); matched++;
            }
            var complete=json.object().arrayNode();
            for(int ordinal=0;ordinal<published.fields().size();ordinal++) {
                var field=published.fields().get(ordinal);
                if(field.chunk_id().equals(hit.required("chunk_id").textValue()) && field.field_kind().equals(input.field_kind())
                        && (field.raw_value().equals(input.query()) || Objects.equals(field.alias_value(),input.query()))) {
                    var match=complete.addObject(); match.put("ordinal",ordinal); match.set("field",json.tree(field));
                }
            }
            if(!complete.equals(matches)) mismatch();
            var proof=candidateBasis(admission,input.project_id(),saved);
            if(!proof.source().sourceId().equals(hit.required("source_id").textValue())
                    || !proof.source().originalSha256().equals(manifest.required("original_sha256").textValue())
                    || !schema.locator(text(chunk,"locator")).equals(hit.required("locator"))
                    || !text(chunk,"quote_hash").equals(hit.required("quote_hash").textValue())
                    || !excerpt(text(chunk,"raw_text")).equals(hit.required("excerpt").textValue())
                    || (text(chunk,"raw_text").codePointCount(0,text(chunk,"raw_text").length())>1200)!=hit.required("excerpt_truncated").booleanValue()) mismatch();
            items.add(hit);
        }
        if(!integer(row,"match_count").equals(BigInteger.valueOf(matched)) || !selectionHash(input,items).equals(text(row,"selection_sha256"))
                || !text(row,"selection_sha256").equals(text(row,"registered_sha256"))) mismatch();
        return new StoredQuery(row,input,items);
    }

    private ObjectNode hit(Map<String,Object> row,JsonNode manifest) throws IOException {
        var result=json.object();
        for(String name:List.of("source_id","source_version","parse_version","index_version","chunk_id","approval_revision","rights_revision","binding_revision","field_revision","title","quote_hash"))
            result.put(name,text(row,name));
        String raw=text(row,"raw_text"); result.put("excerpt",excerpt(raw)); result.put("excerpt_truncated",raw.codePointCount(0,raw.length())>1200);
        result.set("locator",schema.locator(text(row,"locator"))); result.put("field_manifest_sha256",hash(manifest)); return result;
    }
    private void verifyFields(Map<String,Object> values,List<SourceExactProtocol.Field> fields) throws IOException {
        for(var field:fields) {
            values.put("chunkId",field.chunk_id()); var chunk=single(mapper.chunk(values));
            schema.verifyField(field,text(chunk,"chunk_id"),text(chunk,"raw_text"),text(chunk,"quote_hash"),text(chunk,"locator"));
        }
    }
    private SourceReadProtocol.VerifiedSource basis(EnterpriseAccess.Admission admission,String source,SourceExactProtocol.PublicationInput input) throws IOException {
        return sources.requireCandidate(admission,new SourceReadProtocol.SnapshotRequirement(source,input.rights_revision(),input.project_id(),input.parse_version(),
            input.index_version(),input.approval_revision(),input.binding_revision(),rightsPurpose(source,input.rights_revision()),retrievals.environment(),retrievals.region()));
    }
    private SourceReadProtocol.VerifiedSource candidateBasis(EnterpriseAccess.Admission admission,String project,Map<String,Object> row) throws IOException {
        return sources.requireCandidate(admission,new SourceReadProtocol.SnapshotRequirement(text(row,"source_version"),text(row,"rights_revision"),project,
            text(row,"parse_version"),text(row,"index_version"),text(row,"approval_revision"),text(row,"binding_revision"),
            rightsPurpose(text(row,"source_version"),text(row,"rights_revision")),retrievals.environment(),retrievals.region()));
    }
    private String rightsPurpose(String source,String rights) {
        var row=single(sourceMapper.rights(rights)); if(!source.equals(row.sourceVersion())) mismatch(); return row.purpose();
    }
    private void requireApproval(EnterpriseAccess.Admission admission,SourceReadProtocol.VerifiedSource proof) throws IOException {
        resources.require(admission,new PermissionProtocol.Scope(proof.source().tenantId(),proof.source().ownerProjectId(),"SOURCE",proof.source().sourceId()),
            "source.approve","SOURCE_REVIEW");
    }
    private void requireProject(EnterpriseAccess.Admission admission,String project) throws IOException {
        resources.require(admission,new PermissionProtocol.Scope(admission.context().tenant_id(),project,"PROJECT",project),"project.read","PROJECT_READING");
    }
    private JsonNode replay(List<SelectionOperationRow> rows,Map<String,Object> values,String definition) throws IOException {
        var row=single(rows);
        if(!row.requestSha256().equals(values.get("requestHash"))) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
        if(!"COMPLETED".equals(row.state()) || !Integer.valueOf(201).equals(row.httpStatus()) || row.responseRef()==null
                || !"1.0.0".equals(row.schemaVersion()) || !schema.manifestSha256().equals(row.schemaManifestSha256())) mismatch();
        var response=schema.receipt(row.responseRef());
        if(definition==null) {
            var operation=reading.operationReceipt(row.responseRef());
            if(!row.operationId().equals(operation.required("data").required("operation_id").textValue())
                    || !row.targetId().equals(operation.required("data").required("resource_id").textValue())) mismatch();
            return operation;
        }
        schema.require(definition,response.required("data"));
        if(!row.targetId().equals(response.required("data").required("field_revision").textValue())
                || !row.operationId().equals(response.required("data").required("operation_id").textValue())
                || !response.required("resource_revision").equals(response.required("data").required("revision"))) mismatch();
        return response;
    }
    private void saveOperation(Map<String,Object> values,String operation,String target,JsonNode response) throws IOException {
        values.put("operationId",operation); values.put("targetId",target); values.put("response",json.canonical(response)); one(mapper.saveOperation(values));
    }
    private void audit(EnterpriseAccess.Admission admission,String project,String action,String target,String hash,String request) throws IOException {
        var resource=json.object(); resource.put("resource_id",target); resource.put("manifest_sha256",hash);
        one(permissions.saveAudit(admission.subject().user().user_id(),project,UUID.randomUUID().toString(),action,json.canonical(resource),request,
            schema.manifestSha256(),identities.classification()));
    }
    private JsonNode advanceContext(EnterpriseAccess.Admission admission,Map<String,Object> values) {
        values.put("governanceEpoch",admission.context().governance_epoch()); one(mapper.advanceGovernance(values));
        String epoch=PermissionSchema.next(admission.context().governance_epoch()),revision=PermissionSchema.next(admission.context().context_revision());
        one(jdbc.update("""
            UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
            """,revision,epoch,admission.contextId(),admission.subject().sessionId(),admission.subject().user().user_id(),admission.context().tenant_id(),
            admission.context().context_revision(),admission.context().auth_epoch(),admission.context().governance_epoch()));
        var result=json.object(); result.put("tenant_id",admission.context().tenant_id()); result.put("context_revision",revision);
        result.put("auth_epoch",admission.context().auth_epoch()); result.put("governance_epoch",epoch); return result;
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var result=new HashMap<String,Object>(); result.put("userId",admission.subject().user().user_id()); result.put("identityMode",admission.subject().binding().view().mode());
        result.put("schemaManifest",schema.manifestSha256()); result.put("classification",identities.classification()); return result;
    }
    private static void putBasis(Map<String,Object> values,SourceExactProtocol.PublicationInput input) {
        values.put("parseVersion",input.parse_version()); values.put("indexVersion",input.index_version()); values.put("approvalRevision",input.approval_revision());
        values.put("rightsRevision",input.rights_revision()); values.put("projectId",input.project_id());
    }
    private static void putHit(Map<String,Object> values,JsonNode hit) {
        for(var entry:Map.of("sourceVersion","source_version","parseVersion","parse_version","indexVersion","index_version",
                "approvalRevision","approval_revision","rightsRevision","rights_revision","bindingRevision","binding_revision",
                "chunkId","chunk_id","fieldRevision","field_revision").entrySet()) values.put(entry.getKey(),hit.required(entry.getValue()).textValue());
        values.put("rank",hit.required("rank").intValue());
    }
    private String selectionHash(SourceExactProtocol.SearchInput input,ArrayNode items) throws IOException {
        var value=json.object(); value.set("request",json.tree(input)); value.set("items",items); return hash(value);
    }
    private String hash(JsonNode value) throws IOException { return DigestUtils.sha256Hex(json.canonical(value)); }
    private static String textHash(String value) { return DigestUtils.sha256Hex(value.getBytes(StandardCharsets.UTF_8)); }
    private static String excerpt(String value) { return value.substring(0,value.offsetByCodePoints(0,Math.min(1200,value.codePointCount(0,value.length())))); }
    private static String text(Map<String,Object> row,String name) {
        var value=row.get(name); if(!(value instanceof String result)) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); return result;
    }
    private static BigInteger integer(Map<String,Object> row,String name) {
        Object value=row.get(name); if(!(value instanceof Number)) mismatch(); return PermissionSchema.revision(Objects.requireNonNull(value).toString());
    }
    private static LocalDateTime date(Map<String,Object> row,String name) {
        Object value=row.get(name); if(value instanceof LocalDateTime result) return result;
        if(value instanceof java.sql.Timestamp result) return result.toLocalDateTime(); throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static <T> T single(List<T> rows) { if(rows.size()!=1) fail(rows.isEmpty()?ErrorCode.OBJECT_UNAVAILABLE:ErrorCode.CONTENT_MISMATCH); return rows.get(0); }
    private static void current(EnterpriseAccess.Admission admission,String context) { if(!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED); }
    private static void one(int value) { if(value!=1) mismatch(); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
}
