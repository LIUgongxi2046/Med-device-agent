package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class FormalAgentDraftDirectoryTest {
    final FormalRuntimeCapabilityAuthoringServiceTest f=new FormalRuntimeCapabilityAuthoringServiceTest();
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final ListableBeanFactory beans=mock(ListableBeanFactory.class);
    final FormalAgentDraftDirectory directory;
    FormalAgentDraftDirectoryTest()throws Exception{
        when(subject.sessionId()).thenReturn("test-session");
        when(access.execute(eq(subject),any())).thenAnswer(c->((Function<EnterpriseAccess.Admission,Object>)c.getArgument(1)).apply(f.a));
        when(beans.getBeanNamesForType(FormalAgentAuthoringController.class,false,false)).thenReturn(new String[]{
            "actual-controller"
        }
        );
        directory=new FormalAgentDraftDirectory(access,f.runtimeRows,mock(FormalAgentCandidateService.class),f.journal,f.identities,f.schema,new FormalAgentDraftDirectorySchema(),new AgentAuthoringCursor(),f.json,beans);
    }
    JsonNode query(String kind,String cursor)throws Exception {
        var q=f.json.object().put("schema_version","1.0.0").putNull("project_id").put("kind",kind).put("limit",1);
        if(cursor==null)q.putNull("cursor");
        else q.put("cursor",cursor);
        return directory.query(subject,"1",f.json.canonical(q).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void actualToolAuthorCanReadCorrectTypedViewAndOriginalReceipt()throws Exception{
        var receipt=f.capabilities.author(f.a,"tool-author-directory-001",f.capability());
        String id=receipt.required("resource_id").asText();
        var operations=new FormalAgentAuthoringOperations(access,mock(FormalAgentCandidateService.class),f.capabilities,new FormalAgentPublicationSchema(f.json),f.schema,f.identities,f.journal,f.runtimeRows,new AgentAuthoringCursor(),f.json,directory);
        var view=operations.record(subject,"1",id,true).required("data");
        assertEquals("AUTHOR",view.required("kind").asText());
        assertFalse(view.required("production_eligible").asBoolean());
        assertEquals(f.capability(),view.required("content").required("request"));
        assertEquals(receipt,operations.receipt(subject,"1",id,receipt.required("operation_id").asText(),true).required("data"));
    }
    @Test void actualRuntimeDirectoryUsesFixedLedgerAndBoundedMetadataOnly()throws Exception{
        var receipt=f.capabilities.author(f.a,"tool-author-directory-001",f.capability());
        var row=f.records.get(receipt.path("resource_id").asText());
        when(f.runtimeRows.runtimeList(anyMap())).thenReturn(List.of(row.resourceId()));
        var page=query("TOOL",null).required("data");
        assertEquals(1,page.required("items").size());
        var item=page.required("items").get(0);
        assertEquals(row.recordId(),item.required("record_id").asText());
        assertEquals(row.contentSha256(),item.required("content_sha256").asText());
        assertFalse(item.has("content"));
        assertFalse(item.has("manifest"));
        verify(f.runtimeRows).runtimeList(argThat(v->v.get("scanLimit").equals(2)&&v.get("userId").equals(f.actor)&&v.get("capabilityKind").equals("TOOL")));
    }
    @Test void missingLedgerCannotBeFilteredAsNoPermission()throws Exception{
        var receipt=f.capabilities.author(f.a,"tool-author-directory-001",f.capability());
        var row=f.records.get(receipt.path("resource_id").asText());
        when(f.runtimeRows.runtimeList(anyMap())).thenReturn(List.of(row.resourceId()));
        f.audits.clear();
        assertThrows(TenantAccessException.class,()->query("TOOL",null));
    }
    @Test void revokedAccessCanYieldEmptyPageWithOpaqueContinuation()throws Exception{
        var receipt=f.capabilities.author(f.a,"tool-author-directory-001",f.capability());
        var row=f.records.get(receipt.path("resource_id").asText());
        when(f.runtimeRows.runtimeList(anyMap())).thenReturn(List.of(row.resourceId(),"ffffffff-ffff-4fff-8fff-ffffffffffff"));
        f.revoked=true;
        var page=query("TOOL",null).required("data");
        assertTrue(page.required("items").isEmpty());
        assertTrue(page.required("next_cursor").isTextual());
        assertFalse(page.toString().contains(row.resourceId()));
    }
    @Test void cursorBindsKindAndProject()throws Exception{
        when(f.runtimeRows.runtimeList(anyMap())).thenReturn(List.of(f.id(40),f.id(41)));
        when(f.runtimeRows.runtimeAuthor(anyMap())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        String token=query("TOOL",null).path("data").path("next_cursor").asText();
        assertEquals(ErrorCode.CURSOR_INVALID,assertThrows(TenantAccessException.class,()->query("SKILL",token)).code());
    }
    @Test void availabilityUsesActualRegistrationAndNoGrantFields(){
        when(beans.getBeanNamesForType(FormalAgentAuthoringController.class,false,false)).thenReturn(new String[0]);
        var data=directory.availability(subject,"1").required("data");
        assertFalse(data.path("features").path("formal_agent_authoring").asBoolean());
        assertEquals(4,data.size());
    }
    @Test void contextChangeStopsBeforeScanning(){
        assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->directory.availability(subject,"2")).code());
        verifyNoInteractions(beans);
    }
    @Test void oversizedLimitAndUnknownFieldsRejected()throws Exception{
        var schema=new FormalAgentDraftDirectorySchema();
        var q=f.json.object().put("schema_version","1.0.0").putNull("project_id").put("kind","TOOL").put("limit",21).putNull("cursor");
        assertThrows(SchemaRejection.class,()->schema.validate("Query",q));
        q.put("limit",1);
        q.put("grant_all",true);
        assertThrows(SchemaRejection.class,()->schema.validate("Query",q));
    }
    @Test void disabledDirectoryDoesNotScanOrGrantAccess()throws Exception{
        when(beans.getBeanNamesForType(FormalAgentAuthoringController.class,false,false)).thenReturn(new String[0]);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->query("TOOL",null)).code());
        verify(f.runtimeRows,never()).runtimeList(anyMap());
    }
    @Test void controllerAuthenticatesBeforeReadingQueryBody()throws Exception{
        var identities=mock(ApplicationSessionIdentity.class);
        var controller=new FormalAgentDraftDirectoryController(identities,directory);
        var q=mock(jakarta.servlet.http.HttpServletRequest.class);
        var r=mock(jakarta.servlet.http.HttpServletResponse.class);
        var auth=mock(org.springframework.security.core.Authentication.class);
        when(q.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(List.of("1.0.0")));
        when(q.getHeaders("X-Context-Revision")).thenReturn(Collections.enumeration(List.of("1")));
        when(identities.requireCurrent(q,auth)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->controller.query(q,r,auth));
        verify(q,never()).getInputStream();
        verifyNoInteractions(beans);
    }
    @Test void descendingOrDuplicateQueryIdsAreCorruptionNotEmptyPage()throws Exception{
        var q=f.json.object().put("schema_version","1.0.0").putNull("project_id").put("kind","TOOL").put("limit",2).putNull("cursor");
        when(f.runtimeRows.runtimeList(anyMap())).thenReturn(List.of(f.id(40),f.id(40)));
        when(f.runtimeRows.runtimeAuthor(anyMap())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->directory.query(subject,"1",f.json.canonical(q).getBytes(java.nio.charset.StandardCharsets.UTF_8))).code());
    }
    @Test void navigationExpiryAndDisabledServiceHaveExplicitHttpStatuses()throws Exception {
        var controller=new FormalAgentDraftDirectoryController(mock(ApplicationSessionIdentity.class),directory);
        for(var item:Map.of(ErrorCode.CURSOR_EXPIRED,409,ErrorCode.CURSOR_INVALID,409,ErrorCode.DEPENDENCY_UNAVAILABLE,503).entrySet()){
            var response=mock(jakarta.servlet.http.HttpServletResponse.class);
            when(response.getOutputStream()).thenReturn(mock(jakarta.servlet.ServletOutputStream.class));
            controller.denied(new TenantAccessException(item.getKey()),response);
            verify(response).setStatus(item.getValue());
        }
    }
}
