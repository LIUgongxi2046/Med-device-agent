package com.ruoyi.medical.persistence;
import java.nio.file.*;import java.util.*;import java.math.BigInteger;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.session.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;import net.sf.jsqlparser.statement.select.PlainSelect;
public final class SourceOriginalParseMapperProbe {
 public static void main(String[] args)throws Exception {
  var c=new Configuration();try(var prerequisite=Files.newInputStream(Path.of("business/medical-persistence/src/main/resources/medical-mappers/TenantSelectionMapper.xml"))){new XMLMapperBuilder(prerequisite,c,"TenantSelectionMapper.xml",c.getSqlFragments()).parse();}try(var in=Files.newInputStream(Path.of(args[0]))){new XMLMapperBuilder(in,c,"SourceGovernanceMapper.xml",c.getSqlFragments()).parse();}
  var out=new StringBuilder();String tenant="00000000-0000-4000-8000-000000000001",actor="00000000-0000-4000-8000-000000000002";
  TransactionSynchronizationManager.setActualTransactionActive(true);
  try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
   try {for(String id:List.of("artifactSubmission","pendingArtifactSubmission"))for(boolean enterprise:List.of(false,true)){
    var v=new HashMap<String,Object>();v.put("userId",actor);v.put("submissionId","submission");v.put("artifactId","artifact");v.put("targetProjectId",enterprise?null:"project");String mapped="com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper."+id;
    TenantScope.requireSqlAccess(mapped);TenantScope.requireSubjectParameter(mapped,v);
    String sql=new ProtectedTenantInterceptor().parserSingle(c.getMappedStatement(mapped).getBoundSql(v).getSql(),null);
    var statement=(PlainSelect)CCJSqlParserUtil.parse(sql);String where=statement.getWhere().toString();
    if(where.contains("original_event.")||where.contains("p."))throw new AssertionError("LEFT JOIN became WHERE requirement: "+where);
    if(statement.getJoins().stream().filter(j->j.isLeft()).count()!=2)throw new AssertionError("Two LEFT JOINs required");
    if(!sql.contains("original_event.tenant_id = '"+tenant+"'")||!sql.contains("p.tenant_id = '"+tenant+"'"))throw new AssertionError("Missing tenant ON filtering");
    out.append(id+" enterprise="+enterprise+" PASS\n"+sql+"\n\n");
   }}finally{scope.finishTransaction();}
  }finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
  Files.writeString(Path.of(args[1]),out);System.out.println("4/4 actual MyBatis + ProtectedTenantInterceptor branches preserve LEFT JOIN null rows and tenant/actor gates");
 }
}
