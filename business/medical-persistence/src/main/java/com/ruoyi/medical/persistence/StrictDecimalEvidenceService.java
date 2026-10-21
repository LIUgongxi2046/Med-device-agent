package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.StrictComparisonStore.*;

/** Actual authorized original/parsed-object reader followed by exact lexical parsing. */
public final class StrictDecimalEvidenceService {
    static final String PRODUCE="strict_decimal_evidence.produce", READ="strict_decimal_evidence.read";
    private final EnterpriseAccess access;private final StrictComparisonStore store;private final StrictComparisonCodec codec;
    private final StrictComparisonAuthority authority;private final SourceReadAuthority sources;private final TaskFieldSourceAuthority processingObjects;
    private final SourceUploadTransport transport;private final TaskFieldTargetPolicy objects;private final StrictComparisonRuntimePolicy capacity;
    private final SourceProcessingResult processing;
    StrictDecimalEvidenceService(EnterpriseAccess access,StrictComparisonStore store,StrictComparisonCodec codec,StrictComparisonAuthority authority,
            SourceReadAuthority sources,TaskFieldSourceAuthority processingObjects,SourceUploadTransport transport,TaskFieldTargetPolicy objects,StrictComparisonRuntimePolicy capacity) throws Exception {
        this.access=access;this.store=store;this.codec=codec;this.authority=authority;this.sources=sources;this.processingObjects=processingObjects;
        this.transport=transport;this.objects=objects;this.capacity=capacity;processing=new SourceProcessingResult();
    }
    public JsonNode produce(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes) throws Exception {
        capacity.requireConfigured();objects.requireConfigured();if(transport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var request=codec.bind(bytes,StrictComparisonProtocol.Parse.class);var tree=codec.tree(request);if("2.0.0".equals(request.schemaVersion()))com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("strict-field-typed-v2",tree,"Parse");TaskFieldMappingService.key(key);
        var original=access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);return require(a,project,request,PRODUCE);}));
        var old=access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);same(original,require(a,project,request,PRODUCE));return store.replay(a,project,Kind.DECIMAL,key,tree);}));
        if(old!=null)return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);return requireCurrent(a,project,old.id(),old.digest()).receipt();}));
        S3ObjectReader.Admission guard=()->access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);same(original,require(a,project,request,PRODUCE));return null;}));
        var physical=original.processing();
        transport.readDocument(subject,subjectTenant(original),objects.maximumOriginalBytes(),guard,(reader,lease)->{
            var live=TaskFieldSourceReader.guard(guard,lease);try(var input=reader.readVerified(physical.original(),live)){consume(input,objects.maximumOriginalBytes(),false,live);return null;}
        });
        byte[] parsed=transport.readDocument(subject,subjectTenant(original),objects.maximumParsedBytes(),guard,(reader,lease)->{
            var live=TaskFieldSourceReader.guard(guard,lease);try(var input=reader.readVerified(physical.parsed(),live)){return consume(input,objects.maximumParsedBytes(),true,live);}
        });
        var result=(ObjectNode)physical.result().deepCopy();result.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var verified=processing.verify(physical.command(),result,parsed);var bundle=verified.bundle();
        if(!"COMPLETE".equals(bundle.required("status").asText())||!bundle.required("issues").isEmpty()||!physical.original().sha256().equals(bundle.required("original_sha256").asText())||!physical.processorManifest().equals(bundle.required("processor_manifest")))mismatch();
        JsonNode selected=null;for(var chunk:bundle.required("chunks"))if(request.evidence().chunkId().equals(chunk.required("chunk_id").asText())){if(selected!=null)mismatch();selected=chunk;}
        if(selected==null)mismatch();String raw=selected.required("raw_text").textValue();
        if(!request.evidence().quoteHash().equals(selected.required("quote_hash").asText())||!WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8)).equals(request.evidence().quoteHash())||!codec.tree(request.evidence().locator()).equals(selected.required("locator")))mismatch();
        var decimal=new StrictDecimalLexicalParser(capacity.maximumChunkCodepoints()).parse(raw,request.evidence().quoteHash(),request.numericSpan().startCodepoint(),request.numericSpan().endCodepoint());
        String unit=unit(request,raw);
        var body=(ObjectNode)codec.tree(Map.of("request",request,"parsed",decimal,"chunk_text",raw,"original_object",physical.original(),"parsed_object",physical.parsed(),"processing_attempt",physical.attempt(),"processing_command",physical.command(),"processing_result",physical.result(),"processor_manifest",physical.processorManifest()));
        if(unit==null)body.putNull("unit");else body.put("unit",unit);
        guard.requireCurrent();
        return access.execute(subject,a->checked(()->{
            StrictComparisonAuthority.human(a,context);var now=require(a,project,request,PRODUCE);same(original,now);
            var existing=store.replay(a,project,Kind.DECIMAL,key,tree);if(existing!=null)return requireCurrent(a,project,existing.id(),existing.digest()).receipt();
            var saved=store.append(a,project,Kind.DECIMAL,null,key,tree,body,now.witness());same(now,require(a,project,request,PRODUCE));return saved.receipt();
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String project,String id) {
        return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);authority.project(a,project,READ);var row=store.read(a,project,Kind.DECIMAL,id);return requireCurrent(a,project,id,row.digest()).view();}));
    }
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String project;private final Row row;private final StrictComparisonProtocol.Parse request;private final StrictDecimalLexicalParser.Parsed parsed;private final String unit;private final JsonNode authorityWitness;private final StrictComparisonRunScope.Verified run;
        private Verified(EnterpriseAccess.Admission a,String project,Row row,StrictComparisonProtocol.Parse request,StrictDecimalLexicalParser.Parsed parsed,String unit,JsonNode witness,StrictComparisonRunScope.Verified run){this.admission=a;this.project=project;this.row=row;this.request=request;this.parsed=parsed;this.unit=unit;this.authorityWitness=witness.deepCopy();this.run=run;}
        public StrictDecimalLexicalParser.Parsed parsed(){return parsed;}public String unit(){return unit;}
        public com.ruoyi.medical.contracts.DatasetProtocol.EvidenceRef evidence(){return request.evidence();}
        public StrictComparisonProtocol.Scope scope(){return request.scope();}
        public String revisionId(){return row.id();}public String digest(){return row.digest();}
        public StrictFieldComparisonProtocol.Provenance provenance(){
            var decimal=new StrictFieldComparisonProtocol.ParsedDecimalEvidence(row.id(),row.digest(),request.parser().revision(),request.parser().sha256(),parsed.contract(),parsed.rawTextSha256(),parsed.decimalValue(),parsed.scale());
            var source=new StrictFieldComparisonProtocol.SourceIdentity("PARSED_DECIMAL_REVISION",request.evidence(),null,decimal);
            return new StrictFieldComparisonProtocol.Provenance(source,List.of(row.id()),parsed.startCodepoint(),parsed.endCodepoint(),parsed.rawTextSha256());
        }
        public com.ruoyi.medical.contracts.ArtifactProtocol.FieldValue value(){return new com.ruoyi.medical.contracts.ArtifactProtocol.FieldValue(com.ruoyi.medical.contracts.ArtifactProtocol.ValueKind.DECIMAL,parsed.rawText(),parsed.decimalValue(),(long)parsed.scale(),unit,List.of(),null);}
        public JsonNode receipt(){return row.receipt().deepCopy();}
        public JsonNode view(){var result=(ObjectNode)codec.tree(Map.of("schema_version",request.schemaVersion(),"evidence",request.evidence(),"parsed",parsed,"receipt",row.receipt(),"assessment","PARSED_SOURCE_NOT_PROFESSIONAL_APPROVAL"));if(unit==null)result.putNull("unit");else result.put("unit",unit);return result;}
        public void requireUnchanged() throws Exception {var now=run==null?requireCurrent(admission,project,row.id(),row.digest()):requireCurrentFixedRun(run,row.id(),row.digest());if(!authorityWitness.equals(now.authorityWitness))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
    Verified requireCurrent(EnterpriseAccess.Admission a,String project,String id,String digest) throws Exception {
        return current(a,project,id,digest,null);
    }
    Verified requireCurrentFixedRun(StrictComparisonRunScope.Verified run,String id,String digest) throws Exception {
        return current(run.admission(),run.project(),id,digest,run);
    }
    private Verified current(EnterpriseAccess.Admission a,String project,String id,String digest,StrictComparisonRunScope.Verified run) throws Exception {
        capacity.requireConfigured();objects.requireConfigured();var row=store.read(a,project,Kind.DECIMAL,id);if(!digest.equals(row.digest()))mismatch();
        var request=codec.bind(row.request(),StrictComparisonProtocol.Parse.class);if("2.0.0".equals(request.schemaVersion()))com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("strict-field-typed-v2",row.request(),"Parse");if(!row.request().equals(row.body().required("request")))mismatch();
        var now=require(a,project,request,READ,run);var body=row.body();var physical=now.processing();
        if(!codec.tree(physical.original()).equals(body.required("original_object"))||!codec.tree(physical.parsed()).equals(body.required("parsed_object"))||!physical.command().equals(body.required("processing_command"))||!physical.result().equals(body.required("processing_result"))||!physical.processorManifest().equals(body.required("processor_manifest"))||!physical.attempt().equals(body.required("processing_attempt").asText()))mismatch();
        String raw=body.required("chunk_text").asText();var parsed=new StrictDecimalLexicalParser(capacity.maximumChunkCodepoints()).parse(raw,request.evidence().quoteHash(),request.numericSpan().startCodepoint(),request.numericSpan().endCodepoint());
        if(!codec.tree(parsed).equals(body.required("parsed")))mismatch();String unit=unit(request,raw);
        if(!Objects.equals(unit,body.required("unit").isNull()?null:body.required("unit").textValue()))mismatch();
        return new Verified(a,project,row,request,parsed,unit,now.witness(),run);
    }
    private record Current(StrictComparisonAuthority.Current task,SourceReadProtocol.VerifiedSource source,TaskFieldSourceAuthority.Processing processing,JsonNode witness) { }
    private Current require(EnterpriseAccess.Admission a,String project,StrictComparisonProtocol.Parse request,String action) throws Exception {
        return require(a,project,request,action,null);
    }
    private Current require(EnterpriseAccess.Admission a,String project,StrictComparisonProtocol.Parse request,String action,StrictComparisonRunScope.Verified run) throws Exception {
        var current=run==null?authority.require(a,project,request.scope(),action):authority.requireRun(run,request.scope(),action);var evidence=request.evidence();com.ruoyi.medical.contracts.SourceEvidenceBindings.bind(com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath(),codec.tree(evidence)).read();if(!request.schemaVersion().equals(evidence.schemaVersion()))mismatch();
        if(!a.context().tenant_id().equals(evidence.tenantId())||!project.equals(evidence.projectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(!implementation().equals(request.parser()))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        if(request.unit().basis()==StrictComparisonProtocol.UnitBasis.UNKNOWN)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        var requirement=new SourceReadProtocol.ReadRequirement(evidence.sourceVersion(),request.rightsRevision(),project,request.scope().scopeSnapshot(),evidence.parseVersion(),null,SourceReadProtocol.Use.FORMAL,request.purpose(),objects.environment(),objects.region(),null);
        var source=sources.require(a,requirement);
        if(!source.source().sourceId().equals(evidence.sourceId())||!source.selection().approvalRevision().equals(evidence.approvalRevision())||evidence.locatorKind()==null||!evidence.locatorKind().name().equals(evidence.locator().kind()))mismatch();
        var scoped=current.sources().stream().filter(s->s.source().sourceVersion().equals(evidence.sourceVersion())).toList();
        if(scoped.size()!=1||!source.equals(scoped.get(0)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var physical=processingObjects.processing(a.context().tenant_id(),evidence.sourceVersion(),evidence.parseVersion(),source.source().originalSha256());
        var witness=codec.tree(Map.of("task",current.witness(),"source",TaskFieldAuthorityWitness.tree(source),"processing",physical,"parser",implementation()));
        current.requireUnchanged();return new Current(current,source,physical,witness);
    }
    private static String subjectTenant(Current current){return current.source().source().tenantId();}
    private static byte[] consume(S3ObjectReader.VerifiedObject input,int maximum,boolean keep,S3ObjectReader.Admission guard) throws Exception {
        int size=WireValues.revision(input.reference().sizeBytes()).intValueExact();if(size<1||size>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var hash=java.security.MessageDigest.getInstance("SHA-256");byte[] result=keep?new byte[size]:null;
        for(int offset=0;offset<size;){guard.requireCurrent();int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);byte[] part=input.chunk(offset,length);hash.update(part);if(keep)System.arraycopy(part,0,result,offset,length);offset+=length;guard.requireCurrent();}
        String digest=java.util.HexFormat.of().formatHex(hash.digest());if(!digest.equals(input.fullSha256())||!digest.equals(input.reference().sha256()))mismatch();return result;
    }
    private static String unit(StrictComparisonProtocol.Parse request,String raw) {
        if(request.unit().basis()==StrictComparisonProtocol.UnitBasis.UNKNOWN)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        if(request.unit().basis()==StrictComparisonProtocol.UnitBasis.UNITLESS)return null;
        var span=request.unit().span();if(span.endCodepoint()>raw.codePointCount(0,raw.length()))mismatch();
        if(span.startCodepoint()<request.numericSpan().endCodepoint()&&request.numericSpan().startCodepoint()<span.endCodepoint())mismatch();
        String unit=raw.substring(raw.offsetByCodePoints(0,span.startCodepoint()),raw.offsetByCodePoints(0,span.endCodepoint()));if(unit.isBlank())mismatch();return unit;
    }
    static StrictComparisonPolicyDefinition.Version implementation() throws Exception {return StrictComparisonCodec.implementation(StrictDecimalLexicalParser.class,StrictDecimalLexicalParser.CONTRACT);}
    private static void same(Current a,Current b){if(!a.witness().equals(b.witness()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
}
