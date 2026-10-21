package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

class FormalEvaluationContextServiceTest {
    final FormalEvaluationContextReadAuthority reads=mock(FormalEvaluationContextReadAuthority.class);
    final FormalEvaluationContextStoreTest f = new FormalEvaluationContextStoreTest();
    final EnterpriseAccess access = mock(EnterpriseAccess.class);
    final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationContextStore store = mock(FormalEvaluationContextStore.class);
    final AgentAuthoringCursor cursors = new AgentAuthoringCursor();
    final FormalEvaluationContextService service;
    String session="session-a";
    FormalEvaluationContextServiceTest() throws Exception {
        when(subject.sessionId()).thenAnswer(i->session);
        when(access.execute(eq(subject),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(f.a));
        service=new FormalEvaluationContextService(access,store,new FormalEvaluationContextHttpSchema(),cursors,f.json,reads);
    }
    byte[] query(String project,int limit,String cursor)throws Exception {
        var q=f.json.object().put("schema_version","1.0.0").put("project_id",project).put("limit",limit);
        if(cursor==null)q.putNull("cursor");else q.put("cursor",cursor);
        return f.bytes(q);
    }
    JsonNode view(String id)throws Exception {
        var original=f.author();var view=(com.fasterxml.jackson.databind.node.ObjectNode)f.tx(()->f.store.readView(f.a,original.binding().version_id(),reads));
        ((com.fasterxml.jackson.databind.node.ObjectNode)view.required("binding")).put("version_id",id);
        ((com.fasterxml.jackson.databind.node.ObjectNode)view.required("fixed")).put("version_id",id);
        return view;
    }
    @Test void boundedDirectoryContainsOnlyVerifiedMetadataAndUsesSeekCursor()throws Exception {
        when(store.seek(f.a,id(1),"00000000-0000-0000-0000-000000000000",1)).thenReturn(List.of(id(401),id(402)));
        var firstView=view(id(401));when(store.readView(f.a,id(401),reads)).thenReturn(firstView);
        var page=service.query(subject,"1",query(id(1),1,null));
        var summary=page.required("items").get(0);assertEquals(9,summary.size());assertFalse(summary.has("fixed"));assertFalse(summary.has("node_bindings"));assertFalse(summary.has("author_reason"));
        assertTrue(page.required("next_cursor").isTextual());
        when(store.seek(f.a,id(1),id(401),1)).thenReturn(List.of(id(402)));
        var secondView=view(id(402));when(store.readView(f.a,id(402),reads)).thenReturn(secondView);
        var last=service.query(subject,"1",query(id(1),1,page.required("next_cursor").asText()));
        assertTrue(last.required("next_cursor").isNull());
        verify(store,never()).author(any(),anyString(),any());verify(store,never()).decide(any(),anyString(),any());
    }
    String cursor()throws Exception {
        when(store.seek(f.a,id(1),"00000000-0000-0000-0000-000000000000",1)).thenReturn(List.of(id(401),id(402)));
        var firstView=view(id(401));when(store.readView(f.a,id(401),reads)).thenReturn(firstView);
        return service.query(subject,"1",query(id(1),1,null)).required("next_cursor").asText();
    }
    @Test void cursorRejectsProjectAndLimitChanges()throws Exception {
        String cursor=cursor();
        assertEquals(ErrorCode.CURSOR_INVALID,assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(9),1,cursor))).code());
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),2,cursor)));
    }
    @Test void cursorRejectsSessionActorAndEpochChanges()throws Exception {
        String cursor=cursor();session="session-b";
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),1,cursor)));
        session="session-a";f.actor=id(501);
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),1,cursor)));
        f.actor=id(101);when(f.a.context()).thenReturn(new BusinessSessionProtocol.Context(f.tenant,"1","1","2"));
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),1,cursor)));
    }
    @Test void cursorNamespaceCannotBeReusedByOtherDirectory()throws Exception {
        var owner=new AgentAuthoringCursor.Owner(f.tenant,f.actor,session,"1","1","1",1,"080:AGENT:"+id(1));
        String token=cursors.issue(owner,id(401));
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),1,token)));
    }
    @Test void mismatchedRowProjectAndCorruptJournalAreNotSilentlySkipped()throws Exception {
        when(store.seek(f.a,id(2),"00000000-0000-0000-0000-000000000000",1)).thenReturn(List.of(id(401)));
        var firstView=view(id(401));when(store.readView(f.a,id(401),reads)).thenReturn(firstView);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(2),1,null))).code());
        when(store.seek(f.a,id(1),"00000000-0000-0000-0000-000000000000",1)).thenReturn(List.of(id(401)));
        when(store.readView(f.a,id(401),reads)).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        assertThrows(TenantAccessException.class,()->service.query(subject,"1",query(id(1),1,null)));
    }
    @Test void inaccessibleMetadataUsesBoundedEmptyPageWithoutInventingItems()throws Exception {
        when(store.seek(f.a,id(1),"00000000-0000-0000-0000-000000000000",1)).thenReturn(List.of(id(401),id(402)));
        when(store.readView(f.a,id(401),reads)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        var page=service.query(subject,"1",query(id(1),1,null));assertTrue(page.required("items").isEmpty());assertTrue(page.required("next_cursor").isTextual());
    }
    @Test void staleContextRejectsBeforeStoreLookup()throws Exception {
        assertThrows(TenantAccessException.class,()->service.query(subject,"2",query(id(1),1,null)));verifyNoInteractions(store);
    }
}
