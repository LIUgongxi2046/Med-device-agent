package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalInputFixture.*;
final class ExpertQualificationMapperTest {
 static final String PREFIX="com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper.";
 Configuration configuration()throws Exception{var c=new Configuration();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/medical-mappers/ExpertQualificationMapper.xml"))){new XMLMapperBuilder(in,c,"ExpertQualificationMapper.xml",c.getSqlFragments()).parse();}return c;}
 @Test void everyActualJavaMethodHasItsUniqueMappedStatement()throws Exception{var c=configuration();var names=Arrays.stream(ExpertQualificationMapper.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();assertEquals(34,names.size());for(String id:names)assertTrue(c.hasStatement(PREFIX+id),id);assertEquals(25,c.getResultMap(PREFIX+"Journal").getConstructorResultMappings().size());}
 @Test void onlyHeadCasIsMutableAndEveryReadDisablesBothCaches()throws Exception{var c=configuration();for(var method:ExpertQualificationMapper.class.getDeclaredMethods()){var m=c.getMappedStatement(PREFIX+method.getName());if(m.getSqlCommandType()==SqlCommandType.SELECT){assertFalse(m.isUseCache());assertTrue(m.isFlushCacheRequired());}else if(m.getSqlCommandType()==SqlCommandType.UPDATE)assertEquals("advanceHead",method.getName());else assertEquals(SqlCommandType.INSERT,m.getSqlCommandType());}}
 @Test void everyActualMapRequiresCurrentActorAndCannotSubstituteHistoricalCreator()throws Exception{var c=configuration();try(var f=new ExpertFixture()){f.transaction(()->{var v=new HashMap<String,Object>();v.put("userId",f.actor);for(var method:ExpertQualificationMapper.class.getDeclaredMethods()){String id=PREFIX+method.getName();TenantScope.requireSqlAccess(id);TenantScope.requireSubjectParameter(id,v);for(var wrong:List.of(Map.of(),Map.of("creatorId",f.actor),Map.of("userId",id(999))))assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(id,wrong)).code());String sql=new ProtectedTenantInterceptor().parserSingle(c.getMappedStatement(id).getBoundSql(v).getSql(),null);if(!method.getName().equals("databaseTime"))assertTrue(sql.contains(TENANT),method.getName());}return null;});}}
 @Test void immutableAuthorDecisionBindingAndWitnessRejectUpdateDeleteAndTenantSpoof()throws Exception{try(var f=new ExpertFixture()){f.transaction(()->{for(String table:List.of("expert_qualification_record","expert_qualification","expert_score_binding","expert_score_authority_witness")){var guard=new ProtectedTenantInterceptor();for(String sql:List.of("UPDATE "+table+" SET classification='PUBLIC'","DELETE FROM "+table,"INSERT INTO "+table+"(tenant_id) VALUES('"+id(999)+"')"))assertThrows(TenantAccessException.class,()->guard.parserSingle(sql,null));}return null;});}}
 @Test void headCasIncludesRevisionPriorDecisionStatusAndRealLock()throws Exception{var c=configuration();String sql=c.getMappedStatement(PREFIX+"advanceHead").getBoundSql(Map.of()).getSql();for(String expected:List.of("qualification_id=?","status=?","revision=?","decision_id <=> ?"))assertTrue(sql.contains(expected),expected);assertTrue(c.getMappedStatement(PREFIX+"lockHead").getBoundSql(Map.of()).getSql().contains("FOR UPDATE"));assertTrue(c.getMappedStatement(PREFIX+"lockActor").getBoundSql(Map.of()).getSql().contains("FOR UPDATE"));}
}
