package com.ruoyi.medical.persistence;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.session.Configuration;
/** Real MyBatis generated SQL; does not pretend to execute MySQL FK corruption. */
class DocumentRevisionDirectoryMapperTest{
 @Test void incompleteForeignRowsRemainVisibleToIntegritySentinel()throws Exception{
  var configuration=new Configuration();String resource="medical-mappers/DocumentRevisionDirectoryMapper.xml";
  try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))){new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
  var parameters=new HashMap<String,Object>();parameters.put("userId","actor");parameters.put("projectId","project");parameters.put("documentId","document");parameters.put("limit",3);parameters.put("afterCreatedAt",null);parameters.put("afterDocumentRevision",null);
  String sql=configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.DocumentRevisionDirectoryMapper.scan").getBoundSql(parameters).getSql().replaceAll("\\s+"," ");
  assertTrue(sql.contains("LEFT JOIN document_revision r"));assertTrue(sql.contains("LEFT JOIN version_registry v"));assertTrue(sql.contains("THEN v.content_sha256 ELSE NULL END AS content_sha256"));assertFalse(sql.contains("JOIN document_head"));
  assertTrue(sql.contains("ORDER BY m.created_at DESC,m.document_revision DESC LIMIT ?"));
 }
}
