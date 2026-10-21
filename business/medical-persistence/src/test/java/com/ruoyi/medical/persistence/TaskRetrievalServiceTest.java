package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RetrievalMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real retrieval service/schema and SQL generation; only persistence and current authority are doubles. */
class TaskRetrievalServiceTest {
    @Test void sessionFreeRetrievalPersistsAndReadsExactReceiptOwnedQuery() throws Exception {
        String tenant=id(1),project=id(2),actor=id(3),question=id(4),snapshot=id(5);
        var rows=mock(RetrievalMapper.class);var permissions=mock(PermissionMapper.class);
        var json=new PermissionSchema();var schema=new RetrievalSchema(json);
        var service=new RetrievalService(mock(EnterpriseAccess.class),rows,permissions,mock(PermissionAuthority.class),mock(SourceReadAuthority.class),
                schema,new OperationSchema(json),json,new RetrievalPolicy("TEST","LOCAL",100),new IdentityDirectoryPolicy("INTERNAL"));
        var a=mock(EnterpriseAccess.Admission.class);when(a.actorId()).thenReturn(actor);
        when(a.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",actor,null,null));
        when(a.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"0","1","1"));
        var fixed=new TaskResearchToolAccess.Fixed(tenant,project,actor,snapshot,question,"Exact confirmed question?",List.of(),"a".repeat(64));
        var query=new AtomicReference<RetrievalProtocol.QueryRow>();var operation=new AtomicReference<SelectionOperationRow>();
        LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);when(permissions.databaseTime()).thenReturn(now);
        when(rows.operation(anyMap())).thenAnswer(call->operation.get()==null?List.of():List.of(operation.get()));
        when(rows.candidates(anyMap())).thenAnswer(call->{Map<String,Object> values=call.getArgument(0);assertEquals(List.of(),values.get("fixedSources"));assertEquals(fixed.questionText(),values.get("queryText"));return List.of();});
        when(rows.insertQuery(anyMap())).thenAnswer(call->{Map<String,Object> v=call.getArgument(0);
            query.set(new RetrievalProtocol.QueryRow(tenant,project,(String)v.get("retrievalId"),actor,(String)v.get("queryText"),(String)v.get("querySha256"),"VERIFICATION","FULLTEXT",(String)v.get("selectionSha256"),BigInteger.ONE,BigInteger.ONE,BigInteger.ZERO,"EMPTY",actor,now,"INTERNAL"));return 1;});
        when(rows.query(anyMap())).thenAnswer(call->List.of(query.get()));
        when(rows.saveOperation(anyMap())).thenAnswer(call->{Map<String,Object> v=call.getArgument(0);operation.set(new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"COMPLETED",201,(String)v.get("response"),(String)v.get("retrievalId"),"1.0.0",schema.manifestSha256()));return 1;});
        when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(1);
        when(rows.hits(anyMap())).thenReturn(List.of());
        var input=new RetrievalProtocol.SearchInput("1.0.0",project,fixed.questionText(),"VERIFICATION");
        var response=service.create(a,"0",fixed.retrievalKey(),input,fixed.identity(),fixed);
        assertEquals(query.get().retrievalId(),response.required("data").required("resource_id").textValue());
        assertEquals(response,service.create(a,"0",fixed.retrievalKey(),input,fixed.identity(),fixed));verify(rows,times(1)).insertQuery(anyMap());
        var page=service.read(a,"0",query.get().retrievalId(),new RetrievalProtocol.PageQuery(1,20),fixed);
        assertEquals("EMPTY",page.required("data").required("status").textValue());assertTrue(page.required("data").required("items").isEmpty());
        verify(a,never()).subject();
        var wrong=new TaskResearchToolAccess.Fixed(tenant,project,actor,snapshot,question,"Different question",List.of(),"b".repeat(64));
        assertThrows(TenantAccessException.class,()->service.read(a,"0",query.get().retrievalId(),new RetrievalProtocol.PageQuery(1,20),wrong));
    }
    @Test void fixedSourceSqlConstrainsExactIndexAndSelectionBeforeLimit() throws Exception {
        var config=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement=config.getMappedStatement(RetrievalMapper.class.getName()+".candidates");
        var pin=new TaskNodeInputProtocol.SourceSelection(id(1),id(2),id(3),id(4),id(5),id(6),"DOCUMENT_BODY");
        String sql=statement.getBoundSql(Map.of("fixedSources",List.of(pin))).getSql();
        assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        assertTrue(sql.contains("ri.index_version=?"));assertTrue(sql.contains("sv.rights_revision=?"));
        assertTrue(sql.indexOf("index_version=?")<sql.indexOf("GROUP BY"));
        assertTrue(sql.indexOf("sv.rights_revision=?")<sql.indexOf("LIMIT 501"));
        String empty=statement.getBoundSql(Map.of("fixedSources",List.of())).getSql();assertTrue(empty.contains("1=0"));
    }
    static String id(int n){return String.format("10000000-0000-4000-8000-%012d",n);}
}
