package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskProfessionalPackageAuthoringSchemaTest.id;

/** Real semantic selection checks with explicit current capability/rights doubles. */
class TaskProfessionalPackageAuthoringV2AuthorityTest {
    final TaskProfessionalPackageAuthoringV2Fixture f=new TaskProfessionalPackageAuthoringV2Fixture(id(1),id(2),id(3));
    final EvaluationReferences profiles=mock(EvaluationReferences.class);final AgentPublicationDependencies dependencies=mock(AgentPublicationDependencies.class);
    final FormalCapabilityIdentityAuthority capabilities=mock(FormalCapabilityIdentityAuthority.class);final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final TaskProfessionalPackageAuthoringAuthority authority;
    TaskProfessionalPackageAuthoringV2AuthorityTest()throws Exception {
        authority=new TaskProfessionalPackageAuthoringAuthority(mock(DataSource.class),f.json,f.schema,resources,dependencies,capabilities,mock(FormalAgentCandidateAuthority.class),f.registry,mock(SourceReadMapper.class),f.lineage,new EvaluationDependencyPolicy("test","local",256,512,List.of()),profiles,new IdentityDirectoryPolicy("INTERNAL"),mock(VersionResourceMapper.class));ReflectionTestUtils.setField(authority,"db",f.db);
        when(f.db.query(startsWith("SELECT status FROM project"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of("ACTIVE"));
        when(dependencies.require(eq(f.a),eq(f.project),anyList(),eq("AGENT_PUBLICATION"))).thenReturn(new AgentPublicationDependencies.Checked(List.of(),f.json.object()));
        for(String kind:List.of("PROMPT","PROFILE","ROLE"))when(profiles.profile(f.schema.ref(kind),"AGENT_PUBLICATION","PROJECT")).thenReturn(new EvaluationDependencyPolicy.ArtifactProfile("PROJECT","project.read",f.schema.ref(kind),f.schema.packageHash(kind),f.schema.packageHash(kind)));
        when(f.lineage.inspect(anyString(),any())).thenAnswer(c->new DependencyLineage.VerifiedLineage(c.getArgument(0),new org.jgrapht.graph.DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false),Map.of()));
    }
    ObjectNode role()throws Exception {
        var prompt=f.publish("PROMPT",TaskProfessionalPackageAuthoringV2SchemaTest.request("PROMPT").required("package"));var profile=f.publish("PROFILE",TaskProfessionalPackageAuthoringV2SchemaTest.request("PROFILE").required("package"));
        var request=TaskProfessionalPackageAuthoringV2SchemaTest.request("ROLE");var basis=new PermissionProtocol.Basis(id(40),"b".repeat(64));request.withObject("package").set("prompt_basis",f.json.tree(basis));request.withObject("package").set("profile_artifact",profile.status().required("artifact"));
        var version=mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class);when(version.sourceArtifact()).thenReturn(new PermissionProtocol.Basis(prompt.original().text("artifact_id"),prompt.reference().sha256()));when(capabilities.require(f.a,basis,"PROMPT",f.project,"AGENT_PUBLICATION")).thenReturn(version);var metadata=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);when(metadata.configId()).thenReturn(id(41));when(capabilities.requireRegisteredIdentity(f.tenant,f.actor,basis,"PROMPT",f.project)).thenReturn(metadata);return request;
    }
    @Test void genuineV1RoleRequiresGenuineV2PromptAndProfileDependencies()throws Exception {
        var request=role();var checked=f.tx(()->authority.check(f.a,"1",f.project,request));assertTrue(checked.content().members().isEmpty());var reads=f.tx(()->authority.packageReads(f.a,f.project,request));assertEquals(2,reads.size());for(int n=0;n<2;n++)assertEquals(f.schema.ref(n==0?"PROMPT":"PROFILE"),f.registry.binding(reads.get(n).version_id()).get(0).schemaRef());verify(profiles).profile(TaskProfessionalPromptCompiler.ROLE_REF,"AGENT_PUBLICATION","PROJECT");
    }
    @Test void eitherV1DependencyInV2RoleIsRejectedBeforeIntent()throws Exception {
        for(String kind:List.of("PROMPT","PROFILE")){var instance=new TaskProfessionalPackageAuthoringV2AuthorityTest();var request=instance.role();var row=instance.f.rows("artifact_content_binding").stream().filter(r->instance.f.schema.ref(kind).equals(r.get("schema_ref"))).findFirst().orElseThrow();row.put("schema_ref",kind.equals("PROMPT")?TaskProfessionalPromptCompiler.PROMPT_REF:TaskProfessionalPromptCompiler.PROFILE_REF);row.put("schema_document_sha256",instance.f.schema.packageHash("ROLE"));assertThrows(TenantAccessException.class,()->instance.f.tx(()->instance.authority.check(instance.f.a,"1",instance.f.project,request)));assertEquals(2,instance.f.rows("task_package_authoring_intent").size());}
    }
    @Test void roleOutputProfileMustRetainV1HashNotV2PayloadHash()throws Exception {
        when(profiles.profile(f.schema.ref("ROLE"),"AGENT_PUBLICATION","PROJECT")).thenReturn(new EvaluationDependencyPolicy.ArtifactProfile("PROJECT","project.read",f.schema.ref("ROLE"),f.schema.packageHash("PROMPT"),f.schema.packageHash("PROMPT")));var request=TaskProfessionalPackageAuthoringV2SchemaTest.request("ROLE");assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(TenantAccessException.class,()->f.tx(()->authority.check(f.a,"1",f.project,request))).code());verifyNoInteractions(capabilities);assertTrue(f.rows("task_package_authoring_intent").isEmpty());
    }
    @Test void missingApprovedReadProfileFailsClosedWithoutInstallingAnything()throws Exception {
        when(profiles.profile(f.schema.ref("PROMPT"),"AGENT_PUBLICATION","PROJECT")).thenThrow(new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED));assertThrows(TenantAccessException.class,()->f.tx(()->authority.preparation(f.a,"1",f.project)));assertTrue(f.rows("artifact").isEmpty());assertTrue(f.rows("operation_record").isEmpty());
    }
}
