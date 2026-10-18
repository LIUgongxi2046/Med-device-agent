package com.ruoyi.medical.persistence;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual MyBatis binding and JSQLParser AST; no database execution claim. */ class FormalAgentDraftDirectoryMapperTest {
    @Test void actualMapperBindsBoundedCurrentOwnerQueryAndParsesMysqlAst()throws Exception{
        var c=new Configuration();
        for(String name:List.of("TenantSelectionMapper","ArtifactRegistryMapper","AgentPublicationMapper","FormalAgentCandidateMapper")){
            String resource="medical-mappers/"+name+".xml";
            try(var stream=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))){
                new XMLMapperBuilder(stream,c,resource,c.getSqlFragments()).parse();
            }
        }
        var v=new HashMap<String,Object>();
        v.put("projectId",null);
        v.put("afterId","00000000-0000-0000-0000-000000000000");
        v.put("capabilityKind","TOOL");
        v.put("scanLimit",21);
        var statement=c.getMappedStatement("com.ruoyi.medical.persistence.mapper.FormalAgentCandidateMapper.runtimeList");
        var bound=statement.getBoundSql(v);
        String sql=bound.getSql().replaceAll("\\s+"," ");
        assertNotNull(CCJSqlParserUtil.parse(sql));
        assertFalse(statement.isUseCache());
        assertTrue(statement.isFlushCacheRequired());
        assertEquals(List.of("projectId","capabilityKind","afterId","scanLimit"),bound.getParameterMappings().stream().map(p->p.getProperty()).toList());
        for(String part:List.of("h.tenant_id=c.tenant_id","h.config_id=c.config_id","h.current_version=c.capability_version","r.tenant_id=c.tenant_id","r.resource_id=c.capability_version","r.content_sha256=c.content_sha256","c.project_id <=> ?","ORDER BY c.capability_version LIMIT ?"))assertTrue(sql.contains(part),part);
    }
}
