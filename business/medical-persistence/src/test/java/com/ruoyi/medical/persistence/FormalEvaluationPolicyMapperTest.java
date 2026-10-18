package com.ruoyi.medical.persistence;
import com.ruoyi.medical.persistence.mapper.FormalEvaluationPolicyMapper;
import java.nio.file.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FormalEvaluationPolicyMapperTest {
 static final String PREFIX="com.ruoyi.medical.persistence.mapper.FormalEvaluationPolicyMapper.";
 Configuration config()throws Exception{var c=new Configuration();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/medical-mappers/FormalEvaluationPolicyMapper.xml"))){new XMLMapperBuilder(in,c,"084",c.getSqlFragments()).parse();}return c;}
 @Test void exactImmutableStatementsAndConstructorMapping()throws Exception{var c=config();assertEquals(Set.of("snapshot","insertSnapshot","dependencyClassification"),Arrays.stream(FormalEvaluationPolicyMapper.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));assertEquals(SqlCommandType.INSERT,c.getMappedStatement(PREFIX+"insertSnapshot").getSqlCommandType());assertEquals(7,c.getResultMap(PREFIX+"Snapshot").getConstructorResultMappings().size());}
 @Test void readDisablesBothCachesAndLocksOriginal()throws Exception{var r=config().getMappedStatement(PREFIX+"snapshot");assertFalse(r.isUseCache());assertTrue(r.isFlushCacheRequired());assertTrue(r.getBoundSql(Map.of()).getSql().contains("FOR SHARE"));}
 @Test void guardedSqlRejectsMissingCurrentSubjectAndCrossTenant()throws Exception{var f=new PolicyStorageFixture();f.scoped(()->{for(String id:List.of("snapshot","insertSnapshot","dependencyClassification")){TenantScope.requireSqlAccess(PREFIX+id);TenantScope.requireSubjectParameter(PREFIX+id,Map.of("userId",f.actor));for(var wrong:List.of(Map.of(),Map.of("userId",PolicyStorageFixture.id(999)),Map.of("creatorId",f.actor)))assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+id,wrong));}return null;});}
 @Test void immutableSnapshotUpdateDeleteAndTenantSpoofRejected()throws Exception{var f=new PolicyStorageFixture();f.scoped(()->{var guard=new ProtectedTenantInterceptor();for(String sql:List.of("UPDATE formal_evaluation_policy_input SET snapshot_json='{}'","DELETE FROM formal_evaluation_policy_input","INSERT INTO formal_evaluation_policy_input(tenant_id,record_id) VALUES('"+PolicyStorageFixture.id(999)+"','"+PolicyStorageFixture.id(5)+"')"))assertThrows(TenantAccessException.class,()->guard.parserSingle(sql,null));return null;});}
 @Test void actualGuardInjectsCurrentTenantInBothStatements()throws Exception{var f=new PolicyStorageFixture();var c=config();f.scoped(()->{for(String id:List.of("snapshot","insertSnapshot","dependencyClassification")){var values=new HashMap<String,Object>();values.put("userId",f.actor);String sql=new ProtectedTenantInterceptor().parserSingle(c.getMappedStatement(PREFIX+id).getBoundSql(values).getSql(),null);assertTrue(sql.contains(PolicyStorageFixture.id(1)));}return null;});}
 @Test void dependencyClassificationUsesExactRegistryIdentityAndNoCache()throws Exception{var r=config().getMappedStatement(PREFIX+"dependencyClassification");assertFalse(r.isUseCache());assertTrue(r.isFlushCacheRequired());String sql=r.getBoundSql(Map.of("versionId",PolicyStorageFixture.id(5))).getSql();for(String fragment:List.of("sv.original_sha256=v.content_sha256","a.content_sha256=v.content_sha256","r.content_sha256=v.content_sha256","v.version_id=?","s.tenant_id=sv.tenant_id"))assertTrue(sql.contains(fragment),fragment);assertEquals(5,config().getResultMap(PREFIX+"Classification").getConstructorResultMappings().size());}
}
