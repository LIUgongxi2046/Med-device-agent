package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reconstructs existing authority. No grants, decisions, downloads or synthetic source relationships. */
@Component
public final class SimulationAuthorizedUseEvidenceAuthority {
    private static final String PURPOSE=SimulationAuthorizedUseEvidenceSchema.PURPOSE;
    private final SimulationAuthorizedUseEvidenceSchema schema;
    private final PermissionSchema json;
    private final EvaluationReferences references;
    private final EvaluationDependencyPolicy policy;
    private final DependencyLineage lineage;
    private final SourceReadMapper sources;
    private final SourceReadAuthority sourceReads;
    private final ArtifactRegistryMapper artifacts;
    private final SimulationFixtureMapper originals;
    private final SimulationEvaluationPreparationMapper preparations;
    private final SimulationAuthorizedUseEvidenceMapper ledger;
    private final SourceRightsSchema rights;
    private final SourceRightsDecisionSchema decisions;
    private final SimulationOpaqueRightsWitness privateWitness;
    public SimulationAuthorizedUseEvidenceAuthority(SimulationAuthorizedUseEvidenceSchema schema,PermissionSchema json,
            EvaluationReferences references,EvaluationDependencyPolicy policy,DependencyLineage lineage,SourceReadMapper sources,
            SourceReadAuthority sourceReads,ArtifactRegistryMapper artifacts,SimulationFixtureMapper originals,
            SimulationEvaluationPreparationMapper preparations,SimulationAuthorizedUseEvidenceMapper ledger,
            SourceRightsSchema rights,SourceRightsDecisionSchema decisions,SimulationOpaqueRightsWitness privateWitness) {
        this.schema=schema;this.json=json;this.references=references;this.policy=policy;this.lineage=lineage;this.sources=sources;
        this.sourceReads=sourceReads;this.artifacts=artifacts;this.originals=originals;this.preparations=preparations;
        this.ledger=ledger;this.rights=rights;this.decisions=decisions;this.privateWitness=privateWitness;
    }
    public JsonNode preview(EnterpriseAccess.Admission admission,JsonNode specification)throws Exception {
        return reconstruct(admission,specification).document();
    }
    public List<PermissionProtocol.Basis> verifySourceDocument(EnterpriseAccess.Admission admission,JsonNode document)throws Exception {
        schema.validate("Document",document);
        var rebuilt=reconstruct(admission,document.required("specification"));
        if(!rebuilt.document().equals(document))bad();return rebuilt.dependencies();
    }
    /** Only called for all-SIMULATION definitions; ordinary/mixed evaluations keep their original path. */
    public void require(EnterpriseAccess.Admission admission,EvaluationProtocol.Create request)throws Exception {
        if(request.project_id()!=null||EvaluationDependencyUse.forDefinition(request)!=EvaluationDependencyUse.SIMULATION)bad();
        var document=registered(admission,request.authorized_use_evidence(),"AUTHORIZED_USE_EVIDENCE",SimulationAuthorizedUseEvidenceSchema.REF);
        schema.validate("Document",document);
        var spec=json.object().put("schema_version","1.0.0");spec.set("dataset",json.tree(request.dataset()));
        spec.set("sample_manifest_artifact",json.tree(request.sample_manifest_artifact()));spec.set("samples",json.tree(request.samples()));
        if(!spec.equals(document.required("specification")))bad();
        verifySourceDocument(admission,document);
    }
    private record Rebuilt(JsonNode document,List<PermissionProtocol.Basis> dependencies) { }
    private Rebuilt reconstruct(EnterpriseAccess.Admission a,JsonNode spec)throws Exception {
        policy.requireConfigured();var samples=schema.samples(spec);
        var manifest=json.basis(spec.required("sample_manifest_artifact"));
        if(!registered(a,manifest,"SAMPLE_MANIFEST",EvaluationSchema.SAMPLE_MANIFEST_REF).equals(spec.required("samples")))bad();
        var roots=new TreeMap<String,PermissionProtocol.Basis>();add(roots,json.basis(spec.required("dataset")));add(roots,manifest);
        for(var sample:samples){add(roots,sample.input_ref());add(roots,sample.expected_ref());
            references.artifact(null,sample.expected_ref(),EvaluationSchema.EXPECTED_ITEMS_REF);}
        references.require(a,null,List.copyOf(roots.values()),PURPOSE);
        var versions=closure(roots.values());var businessSources=sourceSet(versions);
        if(businessSources.isEmpty())bad();
        var document=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).putNull("project_id")
            .put("purpose",PURPOSE).put("production_eligible",false);
        document.set("specification",spec.deepCopy());var coverage=document.putArray("sources");
        for(var source:businessSources.values()) {
            var current=current(a,source);var proof=SimulationAuthorizedUseRightsVerifier.verify(json,decisions,rights,source,current.rights(),ledger(a,source));
            privateWitness.require(a,proof);
            var entry=sourceIdentity(a,source,current.rights());entry.set("approval",proof.snapshot());coverage.add(entry);
        }
        // Private license proof is governance witness, not a model/input content dependency.
        // Do not filter actual business edges: roots above still require complete real SIM authorization.
        var covered=document.putArray("covered_versions");var dependencies=new ArrayList<PermissionProtocol.Basis>();
        for(var version:versions.values()){var ref=new PermissionProtocol.Basis(version.id(),version.contentSha256());covered.add(json.tree(ref));dependencies.add(ref);}
        schema.readCanonical(json.canonical(document).getBytes(StandardCharsets.UTF_8));return new Rebuilt(document,List.copyOf(dependencies));
    }
    private JsonNode registered(EnterpriseAccess.Admission a,PermissionProtocol.Basis ref,String kind,String schemaRef)throws Exception {
        var values=values(a);values.put("artifactId",ref.version_id());var row=one(preparations.registration(values));
        SimulationEvaluationPreparationEvidence.verify(a,row,preparations);
        if(!a.context().tenant_id().equals(row.tenantId())||!ref.version_id().equals(row.artifactId())||!kind.equals(row.kind())
            ||!ref.content_sha256().equals(row.documentSha256()))bad();
        var preparationSchema=new SimulationEvaluationPreparationSchema();
        if(!preparationSchema.manifest().equals(row.schemaManifest()))bad();
        var document=preparationSchema.canonicalDocument(row.canonicalDocument().getBytes(StandardCharsets.UTF_8));
        if(!schema.hash(document).equals(ref.content_sha256()))bad();
        references.artifact(null,ref,schemaRef);references.require(a,null,List.of(ref),PURPOSE);
        var source=one(sources.source(row.sourceVersion()));var current=current(a,source);
        if(!row.rightsRevision().equals(current.rights().rightsRevision()))bad();
        var artifact=one(artifacts.artifact(ref.version_id()));var binding=one(artifacts.binding(ref.version_id()));
        if(!SimulationEvaluationPreparationProfile.artifactKind(kind).equals(artifact.artifactKind())
            ||!"SOURCE".equals(binding.resourceKind())||!source.sourceId().equals(binding.resourceId())
            ||!original(a,source).reference().equals(artifact.reference())||!source.originalSha256().equals(ref.content_sha256()))bad();
        if(!lineage.inspect(ref.version_id(),policy.limits()).versions().containsKey(source.sourceVersion()))bad();
        return document;
    }
    private TreeMap<String,ArtifactRegistryProtocol.VersionRow> closure(Collection<PermissionProtocol.Basis> refs) {
        var result=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(var ref:refs){var graph=lineage.inspect(ref.version_id(),policy.limits());
            if(!ref.content_sha256().equals(graph.versions().get(ref.version_id()).contentSha256()))bad();
            graph.versions().values().forEach(v->merge(result,v));edges.addAll(graph.graph().edgeSet());
            if(result.size()>policy.maximumVersions()||edges.size()>policy.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
        return result;
    }
    private TreeMap<String,SourceReadProtocol.SourceRow> sourceSet(Map<String,ArtifactRegistryProtocol.VersionRow> versions) {
        var result=new TreeMap<String,SourceReadProtocol.SourceRow>();
        for(var v:versions.values()){var rows=sources.source(v.id());if(rows.size()>1)bad();if(!rows.isEmpty())result.put(v.id(),rows.get(0));}return result;
    }
    private SourceReadProtocol.VerifiedSource current(EnterpriseAccess.Admission a,SourceReadProtocol.SourceRow source)throws IOException {
        if(!a.context().tenant_id().equals(source.tenantId())||source.ownerProjectId()!=null||!"ENTERPRISE".equals(source.ownership()))bad();
        var verified=sourceReads.require(a,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),null,null,null,null,
            SourceReadProtocol.Use.REVIEW,PURPOSE,policy.environment(),policy.region(),null));
        var gates=sources.gates(source.sourceVersion());if(gates.isEmpty())bad();
        for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))bad();
        return verified;
    }
    private ObjectNode sourceIdentity(EnterpriseAccess.Admission a,SourceReadProtocol.SourceRow source,SourceReadProtocol.RightsRow rights)throws IOException {
        var node=json.object();node.set("source",json.tree(new PermissionProtocol.Basis(source.sourceVersion(),source.originalSha256())));
        node.set("rights",json.tree(new PermissionProtocol.Basis(rights.rightsRevision(),rights.contentSha256())));
        node.put("object_ref_sha256",schema.hash(json.tree(original(a,source).reference())));return node;
    }
    private SimulationInvocationSources.Original original(EnterpriseAccess.Admission a,SourceReadProtocol.SourceRow source) {
        var v=values(a);v.put("sourceVersion",source.sourceVersion());var original=one(originals.original(v));
        if(!original.tenantId().equals(a.context().tenant_id())||!original.sourceVersion().equals(source.sourceVersion())
            ||!original.sha256().equals(source.originalSha256()))bad();return original;
    }
    private JsonNode ledger(EnterpriseAccess.Admission a,SourceReadProtocol.SourceRow source)throws IOException {
        var v=values(a);v.put("sourceVersion",source.sourceVersion());var state=jsonNode(one(ledger.state(v)));
        v.put("decisionId",state.required("last_decision_id").textValue());var decision=jsonNode(one(ledger.decision(v)));
        v.put("operationId",decision.required("operation_id").textValue());var operation=jsonNode(one(ledger.operation(v)));
        v.put("requestId",decision.required("receipt_ref").required("request_id").textValue());v.put("revision",state.required("revision").textValue());
        var result=json.object();result.set("state",state);result.set("decision",decision);result.set("operation",operation);
        var audits=result.putArray("audits");for(String item:ledger.audits(v))audits.add(jsonNode(item));
        var events=result.putArray("outbox");for(String item:ledger.outbox(v))events.add(jsonNode(item));return result;
    }
    private JsonNode jsonNode(String value)throws IOException{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);}
    private static Map<String,Object> values(EnterpriseAccess.Admission a){var values=new HashMap<String,Object>();values.put("userId",a.actorId());return values;}
    private static void merge(Map<String,ArtifactRegistryProtocol.VersionRow> values,ArtifactRegistryProtocol.VersionRow v){if(v.projectId()!=null)bad();var old=values.putIfAbsent(v.id(),v);if(old!=null&&!old.equals(v))bad();}
    private static void add(Map<String,PermissionProtocol.Basis> values,PermissionProtocol.Basis ref){var old=values.putIfAbsent(ref.version_id(),ref);if(old!=null&&!old.equals(ref))bad();}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
