package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;
final class AgentExecutionPolicyModelBoundaryTest {
 final PermissionSchema json=new PermissionSchema();final AgentExecutionPolicySchema schema=new AgentExecutionPolicySchema(json);
 final AgentPublicationDependencies dependencies=mock(AgentPublicationDependencies.class);final AgentExecutionPolicyMapper mapper=mock(AgentExecutionPolicyMapper.class);final FormalCapabilityIdentityAuthority capabilities=mock(FormalCapabilityIdentityAuthority.class);final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
 @SuppressWarnings("unchecked") final ObjectProvider<AgentExecutionPolicyModelPort> models=mock(ObjectProvider.class);
 @SuppressWarnings("unchecked") final ObjectProvider<SourceIndexPolicy> indexes=mock(ObjectProvider.class);
 final AgentExecutionPolicyDependencies service=new AgentExecutionPolicyDependencies(dependencies,mapper,mock(ArtifactRegistryMapper.class),mock(SourceGovernanceMapper.class),indexes,models,new EvaluationDependencyPolicy("test","test-region",512,2048,List.of()),schema,json,capabilities);
 AgentExecutionPolicyModelBoundaryTest()throws Exception{}
 @Test void anyModelApprovalWithoutRealPortFailsClosedBeforeDependencyUse()throws Exception{
  var d=mock(Definition.class);var node=mock(NodeBinding.class);when(d.bindings()).thenReturn(List.of(node));when(node.modelVersion()).thenReturn(AgentExecutionPolicySchemaTest.basis(8));
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.require(a,d,null,"AGENT_PUBLICATION",true)).code());verifyNoInteractions(dependencies,mapper,capabilities);
 }
 @Test void nonModelN00HasSuccessfulRealDependencyFlowWithoutModelPort()throws Exception{
  var d=schema.materialize(new AgentExecutionPolicySchemaTest().input());var role=d.bindings().get(0).roleVersion();var roleProof=mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class);
  when(dependencies.requireByUse(eq(a),eq(d.projectId()),anyList())).thenReturn(new AgentPublicationDependencies.Checked(List.of(role.version_id()),json.object()));
  when(mapper.role(anyMap())).thenReturn(List.of(new DomainRow(role.version_id(),role.content_sha256(),"CAPABILITY_VERSION",d.projectId(),AgentExecutionPolicySchemaTest.id(99),"ROLE","DRAFT",null)));
  when(capabilities.require(a,role,"ROLE",d.projectId(),"AGENT_PUBLICATION")).thenReturn(roleProof);when(roleProof.version()).thenReturn(role);when(roleProof.status()).thenReturn("DRAFT");when(roleProof.canonicalDefinition()).thenReturn(json.object());when(roleProof.authorizations()).thenReturn(json.object());
  assertEquals(List.of(role.version_id()),service.require(a,d,d.projectId(),"AGENT_PUBLICATION",true).versions());verify(roleProof).requireUnchanged();verifyNoInteractions(models);
 }
 @Test void formalRoleProofFailureCannotBeReplacedByDraftDatabaseLabel()throws Exception{
  var d=schema.materialize(new AgentExecutionPolicySchemaTest().input());var role=d.bindings().get(0).roleVersion();
  when(dependencies.requireByUse(eq(a),eq(d.projectId()),anyList())).thenReturn(new AgentPublicationDependencies.Checked(List.of(role.version_id()),json.object()));when(mapper.role(anyMap())).thenReturn(List.of(new DomainRow(role.version_id(),role.content_sha256(),"CAPABILITY_VERSION",d.projectId(),AgentExecutionPolicySchemaTest.id(99),"ROLE","DRAFT",null)));
  when(capabilities.require(a,role,"ROLE",d.projectId(),"AGENT_PUBLICATION")).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->service.require(a,d,d.projectId(),"AGENT_PUBLICATION",true));
 }
}
