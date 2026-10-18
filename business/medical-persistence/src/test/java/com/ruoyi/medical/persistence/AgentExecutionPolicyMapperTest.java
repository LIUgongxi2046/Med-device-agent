package com.ruoyi.medical.persistence;
import java.nio.file.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class AgentExecutionPolicyMapperTest {
 static final String NS="com.ruoyi.medical.persistence.mapper.AgentExecutionPolicyMapper.";
 static final List<String> READS=List.of("policy","state","components","decisions","parents","componentParents","source","role","model","list");
 static final List<String> INSERTS=List.of("insertRegistry","insertPolicy","insertState","insertComponent","insertLineage","insertDecision");
 Configuration configuration()throws Exception{var c=new Configuration();try(var in=Files.newInputStream(Path.of(System.getProperty("policy.directory","/tmp/stage22-task-runtime/a/policy"),"AgentExecutionPolicyMapper.xml"))){new XMLMapperBuilder(in,c,"AgentExecutionPolicyMapper.xml",c.getSqlFragments()).parse();}return c;}
 String sql(Configuration c,String id){return c.getMappedStatement(NS+id).getBoundSql(new HashMap<>()).getSql().replaceAll("\\s+"," ").trim();}
 @Test void allEighteenStatementsAreActualMyBatisAndJsqlParserSql()throws Exception{var c=configuration();var names=new ArrayList<>(READS);names.addAll(INSERTS);names.addAll(List.of("advanceState","advanceGovernance"));assertEquals(18,names.size());for(var name:names)assertNotNull(CCJSqlParserUtil.parse(sql(c,name)),name);assertEquals(new HashSet<>(names),new HashSet<>(Arrays.stream(com.ruoyi.medical.persistence.mapper.AgentExecutionPolicyMapper.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList()));}
 @Test void everyReadIsUncachedAndCoreStateUsesActualRowLocks()throws Exception{var c=configuration();for(String n:READS){assertFalse(c.getMappedStatement(NS+n).isUseCache(),n);assertTrue(c.getMappedStatement(NS+n).isFlushCacheRequired(),n);}assertTrue(sql(c,"policy").endsWith("FOR UPDATE"));assertTrue(sql(c,"state").endsWith("FOR UPDATE"));}
 @Test void immutableTablesAreInsertOnlyAndEveryRecordConstructorIsComplete()throws Exception{var c=configuration();for(String n:INSERTS)assertEquals(org.apache.ibatis.mapping.SqlCommandType.INSERT,c.getMappedStatement(NS+n).getSqlCommandType());for(var item:Map.of("policy",14,"state",8,"components",15,"decisions",17,"source",8).entrySet())assertEquals(item.getValue(),c.getMappedStatement(NS+item.getKey()).getResultMaps().get(0).getConstructorResultMappings().size(),item.getKey());}
 @Test void casIncludesActorScopedDecisionAndAllImmutableIdentityFields()throws Exception{var c=configuration();String s=sql(c,"advanceState");for(String part:List.of("version_id = ?","policy_id = ?","project_id <=> ?","content_sha256 = ?","status = ?","revision = ?","d.created_by = ?"))assertTrue(CCJSqlParserUtil.parse(s).toString().contains(part),part);assertTrue(s.contains("d.project_key=agent_execution_policy_state.project_key"));assertTrue(s.contains("d.version_id=agent_execution_policy_state.version_id"));assertTrue(sql(c,"advanceGovernance").contains("auth_epoch=? AND governance_epoch=?"));}
 @Test void lineageRetainsRelationAndComponentReadsJoinFullParentHash()throws Exception{var c=configuration();assertTrue(sql(c,"parents").contains("CONCAT(e.relation_kind,':',e.upstream_version_id)"));assertTrue(sql(c,"components").contains("p.content_sha256=c.policy_sha256"));assertTrue(sql(c,"components").contains("r.owner_project_id <=> c.project_id"));assertTrue(sql(c,"state").contains("p.classification=s.classification"));}
 @Test void allInsertColumnsHaveBoundValuesAndNoModelOrExternalExecutionSql()throws Exception{var c=configuration();for(String n:INSERTS){var statement=CCJSqlParserUtil.parse(sql(c,n));assertInstanceOf(net.sf.jsqlparser.statement.insert.Insert.class,statement);var insert=(net.sf.jsqlparser.statement.insert.Insert)statement;assertFalse(insert.getColumns().isEmpty());}assertTrue(sql(c,"insertComponent").contains("policy_sha256"));assertTrue(sql(c,"list").contains("ORDER BY p.version_id LIMIT ?"));assertTrue(sql(c,"model").contains("c.execution_mode"));}
}
