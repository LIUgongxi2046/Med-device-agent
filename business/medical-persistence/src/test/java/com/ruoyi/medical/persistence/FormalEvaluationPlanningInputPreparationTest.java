package com.ruoyi.medical.persistence;

import org.mockito.ArgumentMatchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real capture/materialization/private-proof and built-in compiler. Admission, original-object
 * readers, source/model authorities and executable tokenizer are explicit doubles, not DB/S3/provider acceptance. */
class FormalEvaluationPlanningInputPreparationTest extends NodeInputObjectFixture {
    final Fixtures fixtures = new Fixtures();
    final EnterpriseAccess access = mock(EnterpriseAccess.class);
    final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationContextStore contexts = mock(FormalEvaluationContextStore.class);
    final FormalEvaluationModelPreparation models = mock(FormalEvaluationModelPreparation.class);
    final FormalCapabilityIdentityAuthority capabilities = mock(FormalCapabilityIdentityAuthority.class);
    final FormalRuntimeCapabilityIdentityAuthority components = mock(FormalRuntimeCapabilityIdentityAuthority.class);
    final FormalEvaluationContextPackages packages = mock(FormalEvaluationContextPackages.class);
    final ScopeSnapshotStore snapshots = mock(ScopeSnapshotStore.class);
    final SourceReadAuthority sources = mock(SourceReadAuthority.class);
    final FormalInvocationSourceOriginals originals = mock(FormalInvocationSourceOriginals.class);
    final FormalTokenizerRunner tokenizer = mock(FormalTokenizerRunner.class);
    final ModelConfigurationService configurations = mock(ModelConfigurationService.class);
    final RunTokenAuthoritySchema roles = mock(RunTokenAuthoritySchema.class);
    final DependencyLineage lineage = mock(DependencyLineage.class);
    final FormalEvaluationExecutionInputs.Prepared inputs = mock(FormalEvaluationExecutionInputs.Prepared.class);
    final FormalEvaluationExecutionScopes.Prepared inputScope = mock(FormalEvaluationExecutionScopes.Prepared.class);
    final FormalEvaluationExecutionAdmission.Prepared root = mock(FormalEvaluationExecutionAdmission.Prepared.class);
    final FormalEvaluationContextStore.Approved approved = mock(FormalEvaluationContextStore.Approved.class);
    final FormalEvaluationModelPreparation.Prepared model = mock(FormalEvaluationModelPreparation.Prepared.class);
    final FormalEvaluationPlanningSource.Prepared planning = mock(FormalEvaluationPlanningSource.Prepared.class);
    final FormalCapabilityIdentityAuthority.VerifiedCapability prompt = mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class);
    final FormalCapabilityIdentityAuthority.VerifiedCapability role = mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class);
    final FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability tool = mock(FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability.class);
    final FormalEvaluationContextPackages.Plan toolPlan = mock(FormalEvaluationContextPackages.Plan.class);
    final FormalEvaluationContextPackages.Plan promptPlan = mock(FormalEvaluationContextPackages.Plan.class);
    final FormalEvaluationContextPackages.Plan tokenizerPlan = mock(FormalEvaluationContextPackages.Plan.class);
    final FormalEvaluationContextPackages.Read toolRead = mock(FormalEvaluationContextPackages.Read.class);
    final FormalEvaluationContextPackages.Read promptRead = mock(FormalEvaluationContextPackages.Read.class);
    final FormalEvaluationContextPackages.Read tokenizerRead = mock(FormalEvaluationContextPackages.Read.class);
    final FormalInvocationSourceOriginals.Read executableRead = mock(FormalInvocationSourceOriginals.Read.class);
    final ObjectNode roleScope = json.object();
    final byte[] executableBytes = "fixture-tokenizer-original".getBytes(StandardCharsets.UTF_8);
    final PermissionProtocol.Basis executableBasis = new PermissionProtocol.Basis(id(91), WireValues.sha256(executableBytes));
    final FormalEvaluationContextProtocol.Node binding = fixtures.binding();
    final FormalEvaluationExecutionProtocol.Node node;
    final FormalEvaluationPlanningInputPreparation service;
    final Map<String, DependencyLineage.VerifiedLineage> graphs = new HashMap<>();
    final ScopeSnapshotStore.Stored promptSnapshot, planningSnapshot;
    Runnable onToolRead = () -> {}, onCompilation = () -> {}, onTokenization = () -> {};
    byte[] measured;

    FormalEvaluationPlanningInputPreparationTest() throws Exception {
        node = new FormalEvaluationExecutionProtocol.Node(checked.node().cell(), "N01", binding.role(), basis(23),
                new PermissionProtocol.Basis(id(24), executableBasis.content_sha256()), hash('c'));
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(actor, "fixture", null, "USER"));
        when(subject.sessionId()).thenReturn("fixture-session");
        when(admission.subject()).thenReturn(subject);
        when(access.execute(eq(subject), any())).thenAnswer(i -> tx(() -> ((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission)));
        when(inputs.requireCurrent(admission)).thenReturn(root);
        when(inputs.document(admission, node.cell().sampleId())).thenAnswer(i -> fixtures.doc());
        when(inputs.receipt(admission, node.cell().sampleId())).thenReturn(json.object().put("original", true));
        when(root.nodes()).thenReturn(List.of(node)); when(root.projectId()).thenReturn(project);
        when(root.canonical()).thenReturn("original-root"); when(root.classification()).thenReturn("INTERNAL");
        when(root.request()).thenReturn(checked.original().request().start());
        when(inputScope.profileRevision()).thenReturn(id(31)); when(inputScope.snapshot()).thenReturn(basis(33));
        var policy = mock(AgentExecutionPolicyProtocol.Definition.class);
        when(root.policy()).thenReturn(policy); when(policy.environment()).thenReturn("PRODUCTION"); when(policy.region()).thenReturn("cn");
        var declaredTokenizer = mock(AgentReleaseProtocol.SchemaBoundArtifact.class, RETURNS_DEEP_STUBS);
        when(declaredTokenizer.schema().schemaRef()).thenReturn("fixture-tokenizer-schema");
        when(declaredTokenizer.schema().schemaSha256()).thenReturn(hash('a'));
        var parameters = json.object().put("reasoning_effort", "none").put("max_output_tokens", 10);
        when(policy.bindings()).thenReturn(List.of(new AgentExecutionPolicyProtocol.NodeBinding("N01", node.role(), node.model(), node.tokenizer(), declaredTokenizer, parameters, List.of())));
        when(model.node()).thenReturn(node);
        when(model.model()).thenReturn(new ModelEligibilityBinding("1.0.0", "deepseek", "model", "v1", node.model().version_id(), id(40), hash('a'), id(41), id(42), id(43), id(44), id(45), List.of("INTERNAL"), List.of("cn"), false));
        when(models.require(admission, inputs, inputScope, node)).thenReturn(model);
        when(approved.binding()).thenReturn(basis(48)); when(approved.node("N01")).thenReturn(binding);
        when(contexts.requireApproved(admission, basis(48), node.cell().candidate(), node.cell().policy())).thenReturn(approved);
        when(components.require(admission, binding.contextAssembler(), "TOOL", project, "EVALUATION_RESULTS")).thenReturn(tool);
        when(capabilities.require(admission, binding.prompt(), "PROMPT", project, "EVALUATION_RESULTS")).thenReturn(prompt);
        when(capabilities.require(admission, node.role(), "ROLE", project, "EVALUATION_RESULTS")).thenReturn(role);
        when(tool.sourceArtifact()).thenReturn(basis(50)); when(prompt.sourceArtifact()).thenReturn(basis(51));
        when(tool.authorizations()).thenReturn(json.object()); when(prompt.authorizations()).thenReturn(json.object()); when(role.authorizations()).thenReturn(json.object());
        var roleDocument = json.object(); roleDocument.putObject("request").set("tool_scope", roleScope);
        when(role.canonicalDefinition()).thenReturn(roleDocument); when(roles.read(eq("RoleScope"), anyString())).thenReturn(roleScope);
        var permissions = roleScope.putArray("permissions");
        permissions.add(json.tree(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant, project, "PROJECT", project), "project.read", "AGENT_PUBLICATION")));
        for (String action : List.of("source.read", "source.external.process")) permissions.add(json.tree(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant, null, "SOURCE", id(70)), action, "AGENT_PUBLICATION")));
        when(planning.node()).thenReturn(node); when(planning.classification()).thenReturn("INTERNAL");
        when(planning.sourceVersions(admission)).thenReturn(List.of(id(80)));
        when(planning.planning(admission)).thenAnswer(i -> { onCompilation.run(); return fixtures.planning(); });
        when(planning.evidence(admission)).thenReturn(json.object().put("original090", true));
        when(planning.requireModelSend(eq(admission), any(), eq("deepseek"), eq("PRODUCTION"), eq("cn"), any())).thenReturn(json.object().put("planning_send", true));
        promptSnapshot = snapshot(60, List.of(new TaskProtocol.ScopeSnapshotMember(id(70), id(71), id(72), id(73), id(74), id(75), id(76), hash('a'))));
        planningSnapshot = snapshot(61, List.of(new TaskProtocol.ScopeSnapshotMember(id(79), id(80), id(81), id(82), id(83), id(84), id(85), hash('a'))));
        when(snapshots.saveForEvaluation(eq(admission), eq(project), eq(id(31)), any(), eq(evaluation), anyString(), eq("PRODUCTION"), eq("cn"), any())).thenAnswer(i -> "AGENT_PUBLICATION".equals(i.getArgument(5)) ? promptSnapshot : planningSnapshot);
        when(snapshots.readForEvaluation(eq(admission), eq(project), eq(id(31)), any(), eq(evaluation), anyString(), eq("PRODUCTION"), eq("cn"), any(), any())).thenAnswer(i -> "AGENT_PUBLICATION".equals(i.getArgument(5)) ? promptSnapshot : planningSnapshot);
        when(sources.require(eq(admission), any())).thenReturn(new SourceReadProtocol.VerifiedSource(new SourceReadProtocol.SourceRow(tenant, id(70), id(71), null, "ENTERPRISE", "INTERNAL", id(76), hash('a'), hash('a'), null), null, null, null, null, null, null));
        String abi = fixtures.catalogue.schemaDigest("evaluation-planning-context-compiler");
        when(packages.plan(admission, "1", project, basis(50), BuiltinEvaluationPlanningContextCompiler.PACKAGE, abi)).thenReturn(toolPlan);
        when(packages.plan(admission, "1", project, basis(51), BuiltinEvaluationPlanningContextCompiler.PROMPT, abi)).thenReturn(promptPlan);
        when(packages.plan(admission, "1", project, node.tokenizer(), "fixture-tokenizer-schema", hash('a'))).thenReturn(tokenizerPlan);
        when(toolPlan.artifact()).thenReturn(basis(50)); when(promptPlan.artifact()).thenReturn(basis(51));
        when(toolPlan.classification()).thenReturn("INTERNAL"); when(promptPlan.classification()).thenReturn("INTERNAL");
        when(promptPlan.sources()).thenReturn(List.of(id(71))); when(tokenizerPlan.sources()).thenReturn(List.of(executableBasis.version_id()));
        when(packages.read(subject, toolPlan)).thenAnswer(i -> { onToolRead.run(); return toolRead; });
        when(packages.read(subject, promptPlan)).thenReturn(promptRead); when(packages.read(subject, tokenizerPlan)).thenReturn(tokenizerRead);
        when(toolRead.bytes()).thenReturn(fixtures.bytes(fixtures.tool())); when(promptRead.bytes()).thenReturn(fixtures.bytes(fixtures.prompt())); when(tokenizerRead.bytes()).thenReturn(executableBytes);
        when(tokenizer.configuredSourceBasis()).thenReturn(executableBasis);
        when(originals.read(subject, "1", executableBasis, "MODEL_CONFIGURATION", project)).thenReturn(executableRead);
        when(executableRead.bytes()).thenReturn(executableBytes);
        when(tokenizer.count(any(), eq(executableBasis.version_id()), eq(executableBasis.content_sha256()), any())).thenAnswer(i -> {
            measured = ((byte[])i.getArgument(0)).clone(); onTokenization.run();
            var result = mock(FormalTokenizerRunner.Result.class); when(result.inputSha256()).thenReturn(WireValues.sha256(measured));
            when(result.tokenizerVersion()).thenReturn(executableBasis.version_id()); when(result.executableSha256()).thenReturn(executableBasis.content_sha256());
            when(result.tokenCount()).thenReturn(BigInteger.valueOf(3)); return result;
        });
        var limits = new ModelConfigurationProtocol.Limits("100000", "100", "1", "100", "100000", 10, false, List.of("none"), 0);
        var definition = new ModelConfigurationProtocol.Definition("deepseek", "https://fixture.invalid", "cn", "model", "v1", null, id(42), id(43), limits, basis(44), "USD", null, null, null, null);
        when(configurations.requireCurrentExternal(admission, node.model().version_id(), "RUN_CREATION", project)).thenReturn(new ModelConfigurationProtocol.Fixed("1.0.0", tenant, project, id(40), node.model().version_id(), null, definition, "fixture", id(46), "1", actor, "2026-10-18T00:00:00Z"));
        for (var b : List.of(basis(50), basis(51), node.tokenizer())) graph(b, "ORIGINAL_PACKAGE_SOURCE");
        when(lineage.inspect(anyString(), any())).thenAnswer(i -> Objects.requireNonNull(graphs.get(i.getArgument(0))));
        service = new FormalEvaluationPlanningInputPreparation(access, contexts, models, capabilities, components, packages, snapshots, sources, originals, tokenizer, configurations, roles, json, fixtures.catalogue, lineage);
    }
    ScopeSnapshotStore.Stored snapshot(int n, List<TaskProtocol.ScopeSnapshotMember> members) {
        return new ScopeSnapshotStore.Stored(new TaskProtocol.Snapshot(tenant, project, id(n), "{}", hash('a'), BigInteger.ONE, BigInteger.ONE, now, hash('a')),
                new TaskProtocol.ScopeSnapshotManifest("1.0.0", tenant, project, id(n), id(31), null, "1", "1", members), null, List.of());
    }
    void graph(PermissionProtocol.Basis b, String relation) {
        String source = id(95); var graph = new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null, null, false);
        graph.addVertex(b.version_id()); graph.addVertex(source); graph.addEdge(b.version_id(), source, new SourceReadProtocol.ParentRow(b.version_id(), source, relation));
        graphs.put(b.version_id(), new DependencyLineage.VerifiedLineage(b.version_id(), graph, Map.of(b.version_id(), new ArtifactRegistryProtocol.VersionRow(b.version_id(), project, "ARTIFACT", b.content_sha256()), source, new ArtifactRegistryProtocol.VersionRow(source, null, "SOURCE_VERSION", hash('a')))));
    }
    FormalEvaluationPlanningInputPreparation.Capture capture() throws Exception { return tx(() -> service.capture(admission, inputs, inputScope, model, approved, node, planning)); }
    void denyPromptSource() throws Exception { when(sources.require(eq(admission), any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)); }

    @Test void actualCaptureCompilerAndMaterializationMintPrivateConsumableProof() throws Exception {
        var captured = capture(); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
        var prepared = service.materialize(subject, "1", captured);
        assertNotNull(measured); assertEquals(WireValues.sha256(measured), prepared.inputSha256());
        var exact = new com.fasterxml.jackson.databind.ObjectMapper().readTree(measured);
        assertEquals(fixtures.planning(), new com.fasterxml.jackson.databind.ObjectMapper().readTree(exact.required("messages").get(2).required("content").asText()).required("planning"));
        tx(() -> { prepared.consume(admission, root, node, bytes -> assertArrayEquals(measured, bytes)); return null; });
        verify(sources, atLeastOnce()).require(admission, new SourceReadProtocol.ReadRequirement(id(71), id(76), project, id(60), id(72), null, SourceReadProtocol.Use.MODEL_SEND, "AGENT_PUBLICATION", "PRODUCTION", "cn", "deepseek"));
        verify(planning, atLeastOnce()).requireModelSend(admission, roleScope, "deepseek", "PRODUCTION", "cn", planningSnapshot);
    }
    @Test void sameVersionTypedEdgeMutationBeforeFirstGetIsRejected() throws Exception {
        var captured = capture(); graph(basis(50), "MUTATED_RELATION");
        assertThrows(TenantAccessException.class, () -> service.materialize(subject, "1", captured)); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
    }
    @Test void sameVersionTypedEdgeMutationDuringOriginalGetStopsBeforeNextGet() throws Exception {
        var captured = capture(); onToolRead = () -> graph(basis(50), "MUTATED_RELATION");
        assertThrows(TenantAccessException.class, () -> service.materialize(subject, "1", captured)); verify(packages, never()).read(subject, promptPlan); verify(tokenizer, never()).count(any(), anyString(), anyString(), any());
    }
    @Test void roleRevocationDuringCompilationStopsBeforeTokenizer() throws Exception {
        var captured = capture(); onCompilation = () -> ((com.fasterxml.jackson.databind.node.ArrayNode)roleScope.required("permissions")).remove(2);
        assertThrows(TenantAccessException.class, () -> service.materialize(subject, "1", captured)); verify(tokenizer, never()).count(any(), anyString(), anyString(), any());
    }
    @Test void topologyMutationDuringTokenizerPreventsProofPublication() throws Exception {
        var captured = capture(); onTokenization = () -> graph(node.tokenizer(), "MUTATED_RELATION");
        assertThrows(TenantAccessException.class, () -> service.materialize(subject, "1", captured)); assertNotNull(measured);
    }
    @Test void sourceRevocationAfterCaptureRejectsBeforeFirstGet() throws Exception {
        var captured = capture(); denyPromptSource();
        assertThrows(TenantAccessException.class, () -> service.materialize(subject, "1", captured)); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
    }
    @Test void roleCannotSubstituteReadForExternalProcessing() throws Exception {
        ((com.fasterxml.jackson.databind.node.ArrayNode)roleScope.required("permissions")).remove(2);
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, this::capture).code()); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
    }
    @Test void planningSendDenialCannotBecomeCompilationAuthority() throws Exception {
        when(planning.requireModelSend(eq(admission), any(), anyString(), anyString(), anyString(), any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class, this::capture); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
    }
    @Test void completedProofRechecksSourceAuthorityBeforeConsumerReceivesBytes() throws Exception {
        var prepared = service.materialize(subject, "1", capture()); denyPromptSource(); var consumed = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(TenantAccessException.class, () -> tx(() -> { prepared.consume(admission, root, node, bytes -> consumed.set(true)); return null; })); assertFalse(consumed.get());
    }
    @Test void contextSwitchBeforeFirstGetRejectsOriginalCapture() throws Exception {
        var captured = capture(); assertThrows(TenantAccessException.class, () -> service.materialize(subject, "2", captured)); verify(packages, never()).read(ArgumentMatchers.<ApplicationSessionIdentity.Verified>any(), any());
    }

    private static final class Fixtures {
 final PermissionSchema json=new PermissionSchema();final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();final BuiltinEvaluationPlanningContextCompiler compiler=new BuiltinEvaluationPlanningContextCompiler(catalogue,json);
 Fixtures()throws Exception{}
 static String id(int i){return "00000000-0000-4000-8000-"+String.format("%012d",i);}static String hash(String s){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);}
 PermissionProtocol.Basis basis(int i){return new PermissionProtocol.Basis(id(i),hash("v"+i));}
 FormalEvaluationContextProtocol.Node binding(){return new FormalEvaluationContextProtocol.Node("N01",basis(1),basis(2),basis(3),new FormalEvaluationContextProtocol.Schema(BuiltinEvaluationPlanningContextCompiler.INPUT,catalogue.schemaDigest("evaluation-planning-context-compiler")),new FormalEvaluationContextProtocol.Schema(BuiltinEvaluationPlanningContextCompiler.OUTPUT,catalogue.schemaDigest("simulation-execution")));}
 ObjectNode doc(){var d=json.object().put("schema_version","1.0.0").put("document_type","PRODUCT_TECHNICAL_REQUIREMENTS").put("language","zh-CN");d.putArray("sections").addObject().put("section_id","1").put("title","原题 😀").put("text","原件严格数值 1.00 mm\né，不作改写");return d;}
 ObjectNode prompt(){var p=json.object().put("schema_version","1.0.0");p.putArray("system_messages").add("原文系统规则\n  空白不改").add("不推断未知事实");return p;}
 ObjectNode tool()throws Exception{var p=json.object().put("schema_version","1.0.0").put("kind","EVALUATION_CONTEXT_ASSEMBLER").put("implementation_id",BuiltinEvaluationPlanningContextCompiler.IMPLEMENTATION).put("implementation_version","1.0.0").put("implementation_class_sha256",compiler.implementationClassSha256()).put("context_mode","N01_PLANNING").put("tool_mode","NONE").put("upstream_mode","ORIGINAL_PLAN_PREPARATION");for(var e:Map.of("input_schema",BuiltinEvaluationPlanningContextCompiler.INPUT,"output_schema",BuiltinEvaluationPlanningContextCompiler.OUTPUT,"prompt_package_schema",BuiltinEvaluationPlanningContextCompiler.PROMPT).entrySet())p.putObject(e.getKey()).put("schema_ref",e.getValue()).put("schema_sha256",catalogue.schemaDigest(e.getKey().equals("output_schema")?"simulation-execution":"evaluation-planning-context-compiler"));return p;}
 ObjectNode planning()throws Exception {
  var p=json.object();p.set("template",json.tree(basis(30)));p.put("profile_revision",id(31)).put("profile_sha256",hash("profile"));p.putArray("profile_attributes");
  var template=p.putObject("template_content").put("schema_version","1.0.0").put("document_type","PRODUCT_TECHNICAL_REQUIREMENTS").put("application_path","FIRST_REGISTRATION").put("ivd",false);template.putNull("directory_profile");
  var node=template.putArray("nodes").addObject().put("node_id",id(60)).putNull("parent_id").put("official_number","1").put("title","Original template title").put("ordinal",1).put("required",true).put("content_mode","NARRATIVE").put("nature","OFFICIAL");
  var condition=json.object().put("mode","ALWAYS").putNull("rule_revision").putNull("engine_revision");condition.putArray("input_keys");node.set("condition",condition);node.putArray("basis_refs");node.putArray("content_requirements");
  var rule=node.putObject("evidence_rule").put("mode","NARRATIVE").putNull("calculation_rule_revision").put("require_original_locator",true).put("require_human_verification",true);rule.putArray("authority_source_types");rule.putArray("verification_rule_revisions");rule.putArray("input_field_ids");
  for(String k:List.of("field_definitions","repeat_definitions","attachment_requirements","cross_document_rules"))template.putArray(k);
  var conditions=template.putObject("condition_schema").put("unknown_result","UNKNOWN");conditions.putArray("attribute_keys");conditions.set("applicability",condition);template.putArray("basis_manifest").add(json.tree(basis(70)));
  var format=template.putObject("format_profile").put("attachment_order","DECLARED_ORDINAL");format.set("format_revision",json.tree(basis(71)));format.set("numbering_rule_revision",json.tree(basis(72)));format.putArray("output_types").add("DOCX");format.putArray("font_artifacts").add(json.tree(basis(73)));format.putArray("node_displays");
  var plan=new ArtifactProtocol.PlanCandidate(id(30),id(31),List.of(id(60)),List.of(new ArtifactProtocol.PlanSection(id(61),List.of(id(60)),"Original template title",null,ArtifactProtocol.Method.HUMAN_PROVIDED,List.of(),List.of())),List.of(),List.of(id(60)));
  p.set("plan_candidate",json.tree(plan));p.putArray("conditions").addObject().put("scope","NODE").put("target_id",id(60)).putNull("rule_revision").putNull("engine_revision").put("status","UNKNOWN").putArray("input_keys");((ObjectNode)p.path("conditions").get(0)).putArray("unknown_input_keys");
  p.putArray("blockers").addObject().put("code","OWNER_UNASSIGNED").putArray("target_ids").add(id(61));return p;
 }

 byte[] bytes(JsonNode n)throws Exception{return json.canonical(n).getBytes(StandardCharsets.UTF_8);}
    }
}
