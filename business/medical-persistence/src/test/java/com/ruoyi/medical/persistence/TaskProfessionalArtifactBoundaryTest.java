package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskProfessionalArtifactBoundaryTest extends NodeInputObjectFixture {
    TaskProfessionalArtifactBoundaryTest()throws Exception{}
    @Test void allV2IntentOwnersBlockBeforeAdmissionEvenWhenRelabeled(){
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
        when(artifact.artifactKind()).thenReturn("ORDINARY");when(binding.schemaRef()).thenReturn("urn:ordinary");
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskProfessionalArtifactBoundary.requireGenericAllowed(artifact,binding,List.of(id(44)))).code());
        assertThrows(TenantAccessException.class,()->TaskProfessionalArtifactBoundary.requireGenericAllowed(artifact,binding,null));
    }
    @Test void historicalV1KindsAloneAreNotReinterpreted(){
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
        for(String kind:List.of("STEP_ENVELOPE","STEP_RESULT","EVIDENCE_BUNDLE","SESSION_EXPORT")){
            when(artifact.artifactKind()).thenReturn(kind);when(binding.schemaRef()).thenReturn("urn:medical:runtime-step:1.0.0#/$defs/StepEnvelope");
            assertDoesNotThrow(()->TaskProfessionalArtifactBoundary.requireGenericAllowed(artifact,binding,List.of()));
        }
        for(String ref:List.of(TaskProfessionalRuntimeService.ENVELOPE_REF,TaskInvocationContextSchema.EXACT_INPUT_REF)){
            when(binding.schemaRef()).thenReturn(ref);
            assertThrows(TenantAccessException.class,()->TaskProfessionalArtifactBoundary.requireGenericAllowed(artifact,binding,List.of()));
        }
    }
    @Test void actualGenericRegistryChecksV2OwnershipBeforeArtifactGrant()throws Exception{
        var mapper=mock(ArtifactRegistryMapper.class);var authority=mock(ResourceReadAuthority.class);
        var artifact=new ArtifactRegistryProtocol.ArtifactRow(tenant,project,id(10),"ORDINARY","{}","key","version",hash('a'),java.math.BigInteger.ONE,"application/json",id(11),hash('a'),"1.0.0",hash('a'),"INTERNAL");
        var binding=new ArtifactRegistryProtocol.BindingRow(tenant,project,id(10),"SOURCE",id(12),BuiltinEvaluationDocumentContextCompiler.OUTPUT,hash('a'),id(11),hash('a'));
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact));when(mapper.binding(id(10))).thenReturn(List.of(binding));when(mapper.runtimeProfessionalOwners(id(10))).thenReturn(List.of(id(44)));
        var service=new ArtifactRegistry(mapper,authority,new DependencyManifestSchema(),mock(DependencyLineage.class));
        var request=new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,project,"SOURCE",id(12)),"source.read","TASK_EXECUTION",BuiltinEvaluationDocumentContextCompiler.OUTPUT,hash('a'),hash('a'),new DependencyLineage.Limits(512,2048));
        assertThrows(TenantAccessException.class,()->service.requireRegistration(admission,id(10),request));
        verify(mapper).runtimeProfessionalOwners(id(10));verify(authority,times(1)).require(any(),any(),any(),any());verify(mapper,never()).manifest(any());
    }
    @Test void actualOwnershipSqlUsesImmutableVersionNotKindOr099AndInjectsTenant()throws Exception{
        var config=new Configuration();String path="medical-mappers/ArtifactRegistryMapper.xml";
        try(var in=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))){new XMLMapperBuilder(in,config,path,config.getSqlFragments()).parse();}
        String name="com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.runtimeProfessionalOwners";var statement=config.getMappedStatement(name);
        var bound=statement.getBoundSql(Map.of("id",id(10)));assertEquals(List.of("id"),bound.getParameterMappings().stream().map(p->p.getProperty()).toList());
        assertFalse(statement.isUseCache());assertTrue(statement.isFlushCacheRequired());
        tx(()->{TenantScope.requireSqlAccess(name);String sql=new ProtectedTenantInterceptor().parserSingle(bound.getSql(),null);assertTrue(sql.contains("i.tenant_id = '"+tenant+"'"),sql);assertTrue(sql.contains("schema_version = '2.0.0'"),sql);assertTrue(sql.contains("planned_artifact_id = ?"),sql);assertTrue(sql.contains("LIMIT 2"),sql);assertFalse(sql.contains("write_kind"));assertFalse(sql.contains("runtime_professional_object_binding"));return null;});
    }
}
