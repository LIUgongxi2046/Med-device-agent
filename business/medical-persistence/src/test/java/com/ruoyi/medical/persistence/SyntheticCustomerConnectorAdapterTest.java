package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SyntheticCustomerConnectorAdapterTest {
    @TempDir Path directory;
    private ObjectNode definition(){
        var b=JSON.createObjectNode().put("schema_version","1.0.0").put("connector_id","00000000-0000-4000-8000-000000000003")
            .put("project_id",SyntheticCustomerHttpFixture.PROJECT).put("expected_revision","0").put("expires_at","2027-01-01T00:00:00Z").put("classification","INTERNAL")
            .put("credential_reference_id","00000000-0000-4000-8000-000000000004").put("credential_revision","SYNTHETIC_TEST_REFERENCE");
        b.set("interface_spec",SyntheticCustomerConnectorAdapter.specification());b.putArray("account_mapping").addObject()
            .put("external_account_id",SyntheticCustomerHttpFixture.ACCOUNT).put("user_id","00000000-0000-4000-8000-000000000005");
        var ownership=b.putObject("ownership_scope").put("project_id",SyntheticCustomerHttpFixture.PROJECT);
        ownership.putObject("external_scope").put("fixture","SYNTHETIC");ownership.putObject("document_ownership_rules").put("rule","SYNTHETIC_EXPLICIT_REVOCATION_ONLY");return b;
    }
    private CustomerConnectorAdapter.Request request(String operation,JsonNode cursor)throws Exception{
        var b=definition();return new CustomerConnectorAdapter.Request("00000000-0000-4000-8000-000000000001",SyntheticCustomerHttpFixture.PROJECT,operation,
            "00000000-0000-4000-8000-000000000006",hash(b),b,cursor,2);
    }
    private SyntheticCustomerConnectorAdapter adapter(SyntheticCustomerHttpFixture f)throws Exception{return new SyntheticCustomerConnectorAdapter(f.endpoint(),directory.resolve("journal"),f.client());}
    @Test void realTlsVerificationAndPaginationPreserveSyntheticOriginals()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var v=a.verify(request("verify",JSON.nullNode()),false,()->{});assertTrue(v.verified());assertEquals(1,v.exchanges().size());
            var first=a.readPage(request("page1",JSON.nullNode()),false,()->{});assertEquals(2,first.items().size());assertTrue(first.hasMore());
            assertEquals("SYNTHETIC",first.items().get(0).metadata().path("mode").asText());
            var second=a.readPage(request("page2",first.nextCursor()),false,()->{});assertEquals(1,second.items().size());assertFalse(second.hasMore());assertEquals(3,f.calls.get());
        }
    }
    @Test void committedJournalRecoversAcrossAdapterRestartWithoutNetwork()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var r=request("recover",JSON.nullNode());var original=adapter(f).readPage(r,false,()->{});int before=f.calls.get();
            var recovered=adapter(f).readPage(r,true,()->{});assertEquals(hash(original),hash(recovered));assertEquals(before,f.calls.get());
        }
    }
    @Test void missingOrUncertainJournalNeverResendsInRecovery()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var r=request("missing",JSON.nullNode());assertThrows(java.io.IOException.class,()->a.readPage(r,true,()->{}));assertEquals(0,f.calls.get());
            a.readPage(r,false,()->{});try(var files=Files.list(directory.resolve("journal"))){for(Path p:files.filter(p->p.toString().endsWith(".json")).toList())Files.delete(p);}
            assertThrows(java.io.IOException.class,()->a.readPage(r,false,()->{}));assertEquals(1,f.calls.get());
        }
    }
    @Test void operationIdentityCannotBeReusedWithDifferentCursor()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var first=a.readPage(request("identity",JSON.nullNode()),false,()->{});
            assertThrows(java.io.IOException.class,()->a.readPage(request("identity",first.nextCursor()),false,()->{}));assertEquals(1,f.calls.get());
        }
    }
    @Test void foreignOwnershipAndUnmappedAccountsFailClosed()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);f.scenario="foreign-project";assertThrows(TenantAccessException.class,()->a.readPage(request("foreign-project",JSON.nullNode()),false,()->{}));
            f.scenario="foreign-account";assertThrows(TenantAccessException.class,()->a.readPage(request("foreign-account",JSON.nullNode()),false,()->{}));
        }
    }
    @Test void explicitRevocationIsNotDeletionAndContainsNoOriginal()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            f.scenario="revocation";var page=adapter(f).readPage(request("revoke",JSON.nullNode()),false,()->{});
            assertTrue(page.items().get(0).revoked());assertEquals(0,page.items().get(0).original().length);
        }
    }
    @Test void changedBytesRetainExternalVersionForServiceConflictDetection()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var original=a.readPage(request("original",JSON.nullNode()),false,()->{}).items().get(0);
            f.scenario="conflict";var changed=a.readPage(request("conflict",JSON.nullNode()),false,()->{}).items().get(0);
            assertEquals(original.externalVersion(),changed.externalVersion());assertEquals(original.externalId(),changed.externalId());assertFalse(Arrays.equals(original.original(),changed.original()));
        }
    }
    @Test void currentAuthorityDenialPreventsTransport()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            assertThrows(SecurityException.class,()->adapter(f).verify(request("denied",JSON.nullNode()),false,()->{throw new SecurityException("revoked");}));assertEquals(0,f.calls.get());
        }
    }
    @Test void simultaneousProducersCannotSendTheSameOperationTwice()throws Exception{
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var r=request("concurrent",JSON.nullNode());
            var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
            var checks=new java.util.concurrent.atomic.AtomicInteger();
            var first=pool.submit(()->a.readPage(r,false,()->{if(checks.incrementAndGet()==2){held.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_TIMEOUT");}}));
            try{assertTrue(held.await(5,java.util.concurrent.TimeUnit.SECONDS));assertThrows(java.nio.channels.OverlappingFileLockException.class,()->a.readPage(r,false,()->{}));}
            finally{release.countDown();}
            assertEquals(2,first.get(10,java.util.concurrent.TimeUnit.SECONDS).items().size());assertEquals(1,f.calls.get());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void invalidSnapshotIsCapturedAsFailureAndNeverRetried()throws Exception{
        try(var f=new SyntheticCustomerHttpFixture(directory)){
            var a=adapter(f);var r=request("invalid",JSON.createObjectNode().put("snapshot","WRONG").put("offset",0));
            assertThrows(java.io.IOException.class,()->a.readPage(r,false,()->{}));
            assertThrows(java.io.IOException.class,()->a.readPage(r,true,()->{}));assertEquals(1,f.calls.get());
        }
    }
    @Test void publicDestinationsPlaintextAndRedirectClientsCannotBeConfigured(){
        for(String endpoint:List.of("http://127.0.0.1:8443","https://example.com:443","https://localhost:8443","https://127.0.0.1:8443/?secret=x"))
            assertThrows(IllegalArgumentException.class,()->new SyntheticCustomerConnectorAdapter(URI.create(endpoint),directory,HttpClient.newHttpClient()));
        assertThrows(IllegalArgumentException.class,()->new SyntheticCustomerConnectorAdapter(URI.create("https://127.0.0.1:8443"),directory,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()));
    }
}
