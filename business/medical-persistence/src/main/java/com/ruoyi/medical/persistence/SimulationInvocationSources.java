package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Genuine source-original consumer. Registration never approves sources or licenses. */
public final class SimulationInvocationSources {
    private static final String PURPOSE="SIMULATION_EVALUATION";
    private static final int MAXIMUM_BYTES=4*1024*1024;
    private final EnterpriseAccess access;
    private final SourceReadAuthority sources;
    private final SourceReadMapper sourceRows;
    private final SourceRightsSchema rights;
    private final SourceUploadTransport transport;
    private final SimulationFixtureMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final ModelConfigurationService configurations;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;
    private final DependencyAccessAuthority dependencies;
    private final RetrievalPolicy deployment;
    @org.springframework.beans.factory.annotation.Autowired
    private SimulationContextDependencies contextDependencies;

    public SimulationInvocationSources(EnterpriseAccess access,SourceReadAuthority sources,SourceReadMapper sourceRows,
            SourceRightsSchema rights,SourceUploadTransport transport,SimulationFixtureMapper mapper,PermissionMapper permissions,
            ResourceReadAuthority resources,ModelConfigurationService configurations,PermissionSchema json,DependencyAccessAuthority dependencies,RetrievalPolicy deployment) throws Exception {
        this.access=access; this.sources=sources; this.sourceRows=sourceRows; this.rights=rights; this.transport=transport;
        this.mapper=mapper; this.permissions=permissions; this.resources=resources; this.configurations=configurations; this.json=json;
        this.schemas=SchemaCatalogue.fromClasspath(); this.dependencies=dependencies; this.deployment=Objects.requireNonNull(deployment);
    }

    public record Registration(String tenantId,String projectId,String caseId,String configRevision,String caseSourceVersion,
            String caseRightsRevision,String inputSourceVersion,String inputRightsRevision,String caseSha256,String inputSha256,
            String settingsSha256,String parametersSha256,String caseReceiptId,String schemaVersion,String manifest,
            String createdBy,LocalDateTime createdAt,String classification) { }
    public record Original(String tenantId,String sourceVersion,String sha256,String objectKey,String objectVersion,
            BigInteger sizeBytes,String mediaType) {
        ObjectRef reference() { return new ObjectRef(objectKey,objectVersion,sha256,sizeBytes.toString(),mediaType); }
    }
    public record Context(String tenantId,String projectId,String contextId,String exactInputRef,String accessSetId,
            String contentHash,String registryHash,String executionMode,String sourceManifest,String promptRevision,
            String schemaVersion,String manifest,String createdBy,LocalDateTime createdAt,String classification) { }
    private record Selection(String project,String config,String caseSource,String caseRights,String inputSource,String inputRights) { }
    private record State(String digest,JsonNode configuration,Original caseOriginal,Original inputOriginal,String classification) { }
    /** Cannot be deserialized or constructed from caller JSON, hashes, or receipt assertions. */
    public static final class VerifiedSources {
        private final SimulationInvocationSources owner;
        private final Object actorProof;
        private final Registration registration;
        private final State state;
        private final String requestHash;
        private VerifiedSources(SimulationInvocationSources owner,Object actorProof,
                Registration registration,State state,String requestHash) {
            this.owner=owner; this.actorProof=actorProof; this.registration=registration; this.state=state; this.requestHash=requestHash;
        }
    }

    /** Opaque preparation produced only after authenticated reads of registered immutable originals. */
    public static final class PreparedFixture {
        private final SimulationInvocationSources owner;
        private final Object actorProof;
        private final Registration registration;
        private final State state;
        private final JsonNode fixture,input,parameters;
        private PreparedFixture(SimulationInvocationSources owner,Object actorProof,
                Registration registration,State state,JsonNode fixture,JsonNode input,JsonNode parameters) {
            this.owner=owner;this.actorProof=actorProof;this.registration=registration;this.state=state;
            this.fixture=fixture.deepCopy();this.input=input.deepCopy();this.parameters=parameters.deepCopy();
        }
        public Registration registration(){return registration;}
        public JsonNode configuration(){return state.configuration().deepCopy();}
        public JsonNode fixture(){return fixture.deepCopy();}
        public JsonNode exactInput(){return input.deepCopy();}
        public Original caseOriginal(){return state.caseOriginal();}
        public Original inputOriginal(){return state.inputOriginal();}
    }
    public PreparedFixture prepareForContext(ApplicationSessionIdentity.Verified subject,JsonNode start) throws Exception {
        return prepareForContext(new ReaderActor(subject),start);
    }
    public PreparedFixture prepareForContext(SimulationWorkerAccess worker,jakarta.servlet.http.HttpServletRequest http,String tenant,String run,JsonNode start) throws Exception {
        return prepareForContext(new ReaderActor(worker,http,tenant,run),start);
    }
    private PreparedFixture prepareForContext(ReaderActor actor,JsonNode start) throws Exception {
        outside();
        Registration row=actor.execute(a->one(mapper.registration(values(a,nullable(start,"project_id"),
            start.required("model_config_revision").textValue(),start.required("case_id").textValue()))));
        String context=actor.execute(a->a.context().context_revision());
        Selection selected=selection(row);State initial=state(actor,context,selected,false);
        same(actor.execute(a->a.context().tenant_id()),row.tenantId());
        same(nullable(start,"project_id"),row.projectId());same(start.required("model_config_revision").textValue(),row.configRevision());
        same(start.required("case_id").textValue(),row.caseId());
        JsonNode fixture=payload(read(actor,context,selected,initial,initial.caseOriginal(),false),"SimulationCase");
        JsonNode input=payload(read(actor,context,selected,initial,initial.inputOriginal(),false),"ExactInput");
        verifyContent(initial.configuration(),fixture,input,start.required("request_parameters"));
        var request=json.object();var binding=request.putObject("binding");
        binding.set("configuration_sha256",initial.configuration().required("content_sha256"));
        request.set("settings",SimulationFixtureContent.settings(initial.configuration()));
        verifyRegistration(row,initial.configuration(),fixture,input,request);
        PreparedFixture prepared=new PreparedFixture(this,actor.execute(admission -> admission.actorProof()),row,initial,fixture,input,start.required("request_parameters"));
        actor.execute(a->{revalidateForContext(a,prepared);return null;});return prepared;
    }
    public void revalidateForContext(EnterpriseAccess.Admission admission,PreparedFixture prepared) {
        ThrowingSupplier.of(()->{
            if(prepared==null||prepared.owner!=this||!prepared.actorProof.equals(admission.actorProof()))denied();
            Registration row=one(mapper.registration(values(admission,prepared.registration.projectId(),prepared.registration.configRevision(),prepared.registration.caseId())));
            if(!row.equals(prepared.registration))mismatch();
            State current=state(admission,admission.context().context_revision(),selection(row),false);
            same(prepared.state.digest(),current.digest());
            verifyContent(current.configuration(),prepared.fixture,prepared.input,prepared.parameters);return true;
        }).get();
    }

    public void requirePreparedContext(EnterpriseAccess.Admission admission,PreparedFixture prepared,JsonNode proof)throws Exception {
        revalidateForContext(admission,prepared);
        same(admission.context().tenant_id(),proof.required("tenant_id").textValue());
        same(prepared.registration.projectId(),nullable(proof,"project_id"));
        same(admission.actorId(),proof.required("actor_id").textValue());
        requireContext(admission,proof,prepared.registration,prepared.state);
    }

    /** Reads two immutable uploaded originals; no source creation, approval, or grant mutation occurs. */
    public Registration register(ApplicationSessionIdentity.Verified subject,String contextRevision,JsonNode request) throws Exception {
        outside(); schemas.validate("simulation-execution",request,"SimulationFixtureRegistrationRequest");
        var selected=selection(request); var initial=state(subject,contextRevision,selected,true);
        byte[] caseBytes=read(subject,contextRevision,selected,initial,initial.caseOriginal(),true);
        byte[] inputBytes=read(subject,contextRevision,selected,initial,initial.inputOriginal(),true);
        JsonNode fixture=payload(caseBytes,"SimulationCase"), input=payload(inputBytes,"ExactInput");
        verifyContent(initial.configuration(),fixture,input,request.required("request_parameters"));
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            State current=state(admission,contextRevision,selected,true); same(initial.digest(),current.digest());
            String caseId=fixture.required("case_id").textValue(); var values=values(admission,selected.project(),selected.config(),caseId);
            var row=new Registration(admission.context().tenant_id(),selected.project(),caseId,selected.config(),selected.caseSource(),
                selected.caseRights(),selected.inputSource(),selected.inputRights(),hash(fixture),hash(input),
                fixture.required("settings_sha256").textValue(),fixture.required("parameters_sha256").textValue(),
                fixture.required("receipt_id").textValue(),"1.0.0",schemas.bind("simulation-execution",fixture,"SimulationCase").schemaSha256(),
                subject.user().user_id(),Objects.requireNonNull(permissions.databaseTime()),current.classification());
            var previous=mapper.registration(values);
            if(!previous.isEmpty()) {
                Registration saved=one(previous); if(!sameBinding(saved,row)) mismatch(); return saved;
            }
            values.put("row",row); if(mapper.insertRegistration(values)!=1) mismatch();
            Registration stored=one(mapper.registration(values)); if(!row.equals(stored)) mismatch();
            var audit=json.object(); audit.put("case_id",row.caseId()); audit.put("model_config_revision",row.configRevision());
            audit.put("case_source_version",row.caseSourceVersion()); audit.put("input_source_version",row.inputSourceVersion());
            audit.put("case_sha256",row.caseSha256()); audit.put("input_sha256",row.inputSha256());
            audit.put("case_original_sha256",current.caseOriginal().sha256()); audit.put("input_original_sha256",current.inputOriginal().sha256());
            if(permissions.saveAudit(subject.user().user_id(),row.projectId(),java.util.UUID.randomUUID().toString(),
                    "model.simulation.fixture.registered",json.canonical(audit),java.util.UUID.randomUUID().toString(),row.manifest(),row.classification())!=1) mismatch();
            return stored;
        }).get());
    }

    public VerifiedSources prepare(ApplicationSessionIdentity.Verified subject,JsonNode request) throws Exception {
        return prepare(new ReaderActor(subject),request);
    }
    VerifiedSources prepare(SimulationWorkerAccess worker,jakarta.servlet.http.HttpServletRequest http,String tenant,String run,JsonNode request,
            java.util.function.Consumer<EnterpriseAccess.Admission> tokenAuthority) throws Exception {
        return prepare(new ReaderActor(worker,http,tenant,run,Objects.requireNonNull(tokenAuthority)),request);
    }
    private VerifiedSources prepare(ReaderActor actor,JsonNode request) throws Exception {
        outside(); schemas.validate("simulation-execution",request,"SimulationInvocationAdmission");
        var proof=request.required("authority_proof"); var binding=request.required("binding");
        Registration registration=actor.execute(admission -> {
            same(admission.context().tenant_id(),proof.required("tenant_id").textValue());
            same(admission.actorId(),proof.required("actor_id").textValue());
            Registration row=one(mapper.registration(values(admission,nullable(proof,"project_id"),
                proof.required("model_config_revision").textValue(),binding.required("case_id").textValue())));
            same(admission.context().tenant_id(),row.tenantId()); same(nullable(proof,"project_id"),row.projectId());
            same(proof.required("model_config_revision").textValue(),row.configRevision()); same(binding.required("case_id").textValue(),row.caseId());
            return row;
        });
        Selection selected=selection(registration);
        String context=actor.execute(admission -> admission.context().context_revision());
        State initial=state(actor,context,selected,false);
        JsonNode fixture=payload(read(actor,context,selected,initial,initial.caseOriginal(),false),"SimulationCase");
        JsonNode input=payload(read(actor,context,selected,initial,initial.inputOriginal(),false),"ExactInput");
        same(fixture,request.required("simulation_case")); same(input,request.required("exact_input"));
        verifyContent(initial.configuration(),fixture,input,request.required("request_parameters"));
        verifyRegistration(registration,initial.configuration(),fixture,input,request);
        var verified=new VerifiedSources(this,actor.execute(admission -> admission.actorProof()),registration,initial,hash(request));
        actor.execute(admission -> { require(admission,request,proof,verified); return null; });
        return verified;
    }

    public void require(EnterpriseAccess.Admission admission,JsonNode request,JsonNode persistedProof,VerifiedSources verified) {
        ThrowingSupplier.of(() -> {
            if(verified==null || verified.owner!=this || !verified.actorProof.equals(admission.actorProof())) denied();
            same(verified.requestHash,hash(request)); same(request.required("authority_proof"),persistedProof);
            Registration stored=one(mapper.registration(values(admission,verified.registration.projectId(),
                verified.registration.configRevision(),verified.registration.caseId())));
            if(!stored.equals(verified.registration)) mismatch();
            State current=state(admission,admission.context().context_revision(),selection(stored),false);
            same(verified.state.digest(),current.digest());
            requireContext(admission,persistedProof,stored,current);
            same(admission.context().tenant_id(),persistedProof.required("tenant_id").textValue());
            same(admission.actorId(),persistedProof.required("actor_id").textValue());
            var now=Objects.requireNonNull(permissions.databaseTime()).toInstant(java.time.ZoneOffset.UTC);
            if(!java.time.Instant.parse(persistedProof.required("retention_deadline").textValue()).isAfter(now)) denied();
            return true;
        }).get();
    }

    private void requireContext(EnterpriseAccess.Admission admission,JsonNode proof,Registration row,State current) throws Exception {
        var values=values(admission,row.projectId(),row.configRevision(),row.caseId());
        values.put("contextId",proof.required("context_manifest_id").textValue()); Context context=one(mapper.context(values));
        same(admission.context().tenant_id(),context.tenantId()); same(row.projectId(),context.projectId());
        same(proof.required("context_manifest_id").textValue(),context.contextId()); same("SIMULATION",context.executionMode());
        same(context.contentHash(),context.registryHash());
        JsonNode reference=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(context.exactInputRef());
        same(hash(json.tree(current.inputOriginal().reference())),hash(reference));
        var manifest=json.object(); manifest.put("schema_version",context.schemaVersion()); manifest.put("input_data_kind","SIMULATION");
        manifest.put("production_eligible",false); manifest.put("tenant_id",context.tenantId()); manifest.set("project_id",json.tree(context.projectId()));
        manifest.put("context_manifest_id",context.contextId()); manifest.set("exact_input_ref",reference);
        var declaredSources=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(context.sourceManifest());
        var expectedSources=json.object().arrayNode();
        expectedSources.add(sourceManifest("CASE",row.caseSourceVersion(),row.caseRightsRevision(),current.caseOriginal().sha256()));
        expectedSources.add(sourceManifest("EXACT_INPUT",row.inputSourceVersion(),row.inputRightsRevision(),current.inputOriginal().sha256()));
        same(expectedSources,declaredSources); manifest.set("source_manifest",declaredSources);
        manifest.put("prompt_revision",context.promptRevision()); manifest.put("dependency_access_set_id",context.accessSetId());
        manifest.put("created_by",context.createdBy()); manifest.put("created_at",context.createdAt().toInstant(java.time.ZoneOffset.UTC).toString());
        manifest.put("classification",context.classification());
        var validated=schemas.bind("simulation-execution",manifest,"SimulationContextManifest");
        same(validated.schemaSha256(),context.manifest()); same(hash(manifest),context.contentHash());
        if(!classification(context.classification(),row.classification()).equals(context.classification())) mismatch();
        var selected=new java.util.LinkedHashMap<String,SourceReadProtocol.ReadRequirement>();
        selected.put(row.caseSourceVersion(),readRequirement(row.caseSourceVersion(),row.caseRightsRevision(),row.projectId()));
        var previous=selected.putIfAbsent(row.inputSourceVersion(),readRequirement(row.inputSourceVersion(),row.inputRightsRevision(),row.projectId()));
        if(previous!=null && !previous.rightsRevision().equals(row.inputRightsRevision())) mismatch();
        if(contextDependencies==null)denied();
        var completeRequirements=contextDependencies.sourceRequirements(context.accessSetId(),row.projectId());
        for(var required:selected.values())if(!completeRequirements.contains(required))mismatch();
        for(var required:completeRequirements){
            var source=one(sourceRows.source(required.sourceVersion()));
            if(!SimulationContextDependencies.maximum(context.classification(),source.classification()).equals(context.classification()))mismatch();
        }
        var verifiedDependencies=dependencies.require(admission,context.accessSetId(),new DependencyAccessProtocol.ReadRequirement(row.projectId(),PURPOSE,
            new DependencyLineage.Limits(257,1027)),completeRequirements,
            new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
        if(contextDependencies==null)denied();
        contextDependencies.require(admission,context,proof.required("agent_version_id").textValue());
    }
    private ObjectNode sourceManifest(String kind,String version,String revision,String originalHash) {
        var item=json.object(); item.put("kind",kind); item.put("source_version",version); item.put("rights_revision",revision);
        item.put("original_sha256",originalHash); item.put("purpose",PURPOSE); return item;
    }
    private SourceReadProtocol.ReadRequirement readRequirement(String version,String revision,String project) {
        return new SourceReadProtocol.ReadRequirement(version,revision,project,null,null,null,SourceReadProtocol.Use.REVIEW,PURPOSE,deployment.environment(),deployment.region(),null);
    }

    private final class ReaderActor {
        private final ApplicationSessionIdentity.Verified subject;
        private final SimulationWorkerAccess worker;
        private final jakarta.servlet.http.HttpServletRequest http;
        private final String tenant,run;
        private final java.util.function.Consumer<EnterpriseAccess.Admission> tokenAuthority;
        private ReaderActor(ApplicationSessionIdentity.Verified subject){this.subject=subject;worker=null;http=null;tenant=null;run=null;tokenAuthority=null;}
        private ReaderActor(SimulationWorkerAccess worker,jakarta.servlet.http.HttpServletRequest http,String tenant,String run){
            this(worker,http,tenant,run,null);
        }
        private ReaderActor(SimulationWorkerAccess worker,jakarta.servlet.http.HttpServletRequest http,String tenant,String run,
                java.util.function.Consumer<EnterpriseAccess.Admission> tokenAuthority){
            this.subject=null;this.worker=worker;this.http=http;this.tenant=tenant;this.run=run;this.tokenAuthority=tokenAuthority;
        }
        <T>T execute(java.util.function.Function<EnterpriseAccess.Admission,T> operation){
            return worker==null?access.execute(subject,operation):worker.execute(http,tenant,run,admission -> {
                if(tokenAuthority!=null)tokenAuthority.accept(admission);
                T result=operation.apply(admission);
                if(tokenAuthority!=null)tokenAuthority.accept(admission);
                return result;
            });
        }
        <T>T read(Original object,S3ObjectReader.Admission current,SourceUploadTransport.DocumentRead<T> operation) throws Exception {
            return worker==null?transport.readDocument(subject,object.tenantId(),MAXIMUM_BYTES,current,operation)
                :transport.readSimulationWorker(execute(admission -> admission.workerProof()),MAXIMUM_BYTES,current,operation);
        }
    }
    private byte[] read(ApplicationSessionIdentity.Verified subject,String context,Selection selected,State original,
            Original object,boolean registration) throws Exception {
        return read(new ReaderActor(subject),context,selected,original,object,registration);
    }
    private byte[] read(ReaderActor actor,String context,Selection selected,State original,
            Original object,boolean registration) throws Exception {
        S3ObjectReader.Admission current=() -> same(original.digest(),state(actor,context,selected,registration).digest());
        return actor.read(object,current,(reader,guard) -> {
            try(var verified=reader.readVerified(object.reference(),guard)) {
                if(!object.reference().equals(verified.reference()) || !object.sha256().equals(verified.fullSha256())) mismatch();
                byte[] bytes=new byte[object.sizeBytes().intValueExact()];
                for(int start=0;start<bytes.length;) {
                    int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,bytes.length-start);
                    System.arraycopy(verified.chunk(start,length),0,bytes,start,length); start+=length;
                }
                guard.requireCurrent(); return bytes;
            }
        });
    }
    private State state(ApplicationSessionIdentity.Verified subject,String context,Selection selected,boolean registration) {
        return state(new ReaderActor(subject),context,selected,registration);
    }
    private State state(ReaderActor actor,String context,Selection selected,boolean registration) {
        return actor.execute(admission -> ThrowingSupplier.of(() -> state(admission,context,selected,registration)).get());
    }
    private State state(EnterpriseAccess.Admission admission,String context,Selection selected,boolean registration) throws Exception {
        if(!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        JsonNode configuration=configurations.read(admission,selected.config(),true).required("data");
        same(selected.project(),nullable(configuration,"project_id"));
        if(registration) resources.require(admission,new PermissionProtocol.Scope(admission.context().tenant_id(),selected.project(),
            "CONFIGURATION",configuration.required("config_id").textValue()),"model.configure",PURPOSE);
        var caseSource=source(admission,selected.caseSource(),selected.caseRights(),selected.project());
        var inputSource=source(admission,selected.inputSource(),selected.inputRights(),selected.project());
        Original caseObject=original(admission,caseSource), inputObject=original(admission,inputSource);
        var identity=new HashMap<String,Object>(); identity.put("subject",admission.bindingView());
        identity.put("user",admission.actorId()); identity.put("actor_proof",admission.simulationWorker()?admission.workerProof().identity():admission.subject().sessionId());
        identity.put("context",admission.context()); identity.put("watermarks",admission.watermarks());
        identity.put("configuration_sha256",configuration.required("content_sha256")); identity.put("case_source",caseSource);
        identity.put("case_gates",sourceRows.gates(selected.caseSource())); identity.put("input_gates",sourceRows.gates(selected.inputSource()));
        identity.put("input_source",inputSource); identity.put("case_object",caseObject); identity.put("input_object",inputObject);
        String classification=classification(caseSource.source().classification(),inputSource.source().classification());
        return new State(WireValues.sha256(ContentReadAuthorization.canonicalBytes(identity)),configuration,caseObject,inputObject,classification);
    }
    private SourceReadProtocol.VerifiedSource source(EnterpriseAccess.Admission admission,String version,String revision,String project) throws Exception {
        var value=sources.require(admission,new SourceReadProtocol.ReadRequirement(version,revision,project,null,null,null,
            SourceReadProtocol.Use.REVIEW,PURPOSE,deployment.environment(),deployment.region(),null));
        if("SESSION".equals(value.source().ownership())) denied();
        rights.requireEnterpriseUse(value.rights());
        var gates=sourceRows.gates(version); if(gates.isEmpty()) denied();
        for(var gate:gates) if(!"ACTIVE".equals(gate.lifecycle()) || !"RECEIVED".equals(gate.uploadStatus())
                || !"APPROVED".equals(gate.approvalStatus())) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        return value;
    }
    private Original original(EnterpriseAccess.Admission admission,SourceReadProtocol.VerifiedSource source) {
        var values=values(admission,null,null,null); values.put("sourceVersion",source.source().sourceVersion());
        Original row=one(mapper.original(values));
        same(admission.context().tenant_id(),row.tenantId()); same(source.source().sourceVersion(),row.sourceVersion());
        same(source.source().originalSha256(),row.sha256());
        if(row.sizeBytes()==null || row.sizeBytes().signum()<=0 || row.sizeBytes().compareTo(BigInteger.valueOf(MAXIMUM_BYTES))>0)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        // Never accept caller addresses or HTTP URLs; S3 reader further fixes endpoint, bucket and version.
        if(!row.objectKey().startsWith("tenants/"+row.tenantId()+"/") || row.objectKey().contains("..")) denied();
        return row;
    }
    private JsonNode payload(byte[] bytes,String definition) throws Exception {
        String utf8=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        JsonNode value=new com.fasterxml.jackson.databind.json.JsonMapper().reader()
            .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(utf8);
        schemas.validate("simulation-execution",value,definition); return value;
    }
    private void verifyContent(JsonNode configuration,JsonNode fixture,JsonNode input,JsonNode parameters) throws Exception {
        SimulationFixtureContent.verify(schemas,configuration,fixture,input,parameters);
    }
    private void verifyRegistration(Registration row,JsonNode configuration,JsonNode fixture,JsonNode input,JsonNode request) throws Exception {
        same("1.0.0",row.schemaVersion()); same(schemas.bind("simulation-execution",fixture,"SimulationCase").schemaSha256(),row.manifest());
        same(row.caseSha256(),hash(fixture)); same(row.inputSha256(),hash(input)); same(row.caseId(),fixture.required("case_id").textValue());
        same(row.caseReceiptId(),fixture.required("receipt_id").textValue()); same(row.settingsSha256(),fixture.required("settings_sha256").textValue());
        same(row.parametersSha256(),fixture.required("parameters_sha256").textValue());
        same(configuration.required("content_sha256").textValue(),request.required("binding").required("configuration_sha256").textValue());
        same(hash(SimulationFixtureContent.settings(configuration)),hash(request.required("settings")));
    }
    private static Selection selection(JsonNode value) {
        return new Selection(nullable(value,"project_id"),value.required("model_config_revision").textValue(),
            value.required("case_source_version").textValue(),value.required("case_rights_revision").textValue(),
            value.required("input_source_version").textValue(),value.required("input_rights_revision").textValue());
    }
    private static Selection selection(Registration row) { return new Selection(row.projectId(),row.configRevision(),row.caseSourceVersion(),
        row.caseRightsRevision(),row.inputSourceVersion(),row.inputRightsRevision()); }
    private static boolean sameBinding(Registration a,Registration b) {
        return a.tenantId().equals(b.tenantId()) && selection(a).equals(selection(b)) && a.caseId().equals(b.caseId())
            && a.caseSha256().equals(b.caseSha256()) && a.inputSha256().equals(b.inputSha256())
            && a.caseReceiptId().equals(b.caseReceiptId()) && a.settingsSha256().equals(b.settingsSha256())
            && a.parametersSha256().equals(b.parametersSha256()) && a.manifest().equals(b.manifest()) && a.classification().equals(b.classification());
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission,String project,String config,String caseId) {
        var values=new HashMap<String,Object>(); values.put("userId",admission.actorId());
        values.put("projectId",project); values.put("configRevision",config); values.put("caseId",caseId); return values;
    }
    private static String classification(String a,String b) {
        var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");
        if(!levels.contains(a)||!levels.contains(b)) mismatch(); return levels.get(Math.max(levels.indexOf(a),levels.indexOf(b)));
    }
    private static String nullable(JsonNode value,String key) { return value.required(key).isNull()?null:value.required(key).textValue(); }
    private static String hash(JsonNode value) throws Exception { SimulationInvocationSchema.numbers(value); return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value)); }
    private static void outside() { if(TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) denied(); return rows.get(0); }
    private static void same(Object a,Object b) { if(!Objects.equals(a,b)) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
