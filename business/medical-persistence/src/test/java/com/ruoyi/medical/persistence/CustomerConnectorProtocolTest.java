package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Deferred offline contract tests. Not executed during source-only development.
 * These test-only values are not customer specifications, accounts or acceptance evidence. */
class CustomerConnectorProtocolTest {
    private static final String TENANT="00000000-0000-4000-8000-000000000001",PROJECT="00000000-0000-4000-8000-000000000002",
        USER="00000000-0000-4000-8000-000000000003",CONFIG="00000000-0000-4000-8000-000000000004",RUN="00000000-0000-4000-8000-000000000005";
    private ObjectNode draft(){
        var b=JSON.createObjectNode().put("schema_version","1.0.0").put("connector_id",CONFIG).put("project_id",PROJECT)
            .put("expected_revision","0").put("expires_at","2026-12-01T00:00:00Z").put("classification","INTERNAL");
        for(String field:List.of("interface_spec","account_mapping","ownership_scope","credential_reference_id","credential_revision"))b.putNull(field);
        return b;
    }
    private ObjectNode configured(){
        var b=draft();b.putObject("interface_spec").put("version","offline-contract-test").putObject("document").put("fixture",true);
        b.putArray("account_mapping").addObject().put("external_account_id","offline-account").put("user_id",USER);
        var scope=b.putObject("ownership_scope").put("project_id",PROJECT);scope.putObject("external_scope").put("fixture",true);scope.putObject("document_ownership_rules").put("fixture",true);
        b.put("credential_reference_id",USER).put("credential_revision","offline");return b;
    }
    private CustomerConnectorAdapter.Exchange exchange(){return new CustomerConnectorAdapter.Exchange("GET","https://example.invalid/offline-only",JSON.createArrayNode(),new byte[0],200,JSON.createArrayNode(),new byte[]{1},"2026-10-21T00:00:00Z","2026-10-21T00:00:01Z",true);}
    private CustomerConnectorAdapter.Request request(){return new CustomerConnectorAdapter.Request(TENANT,PROJECT,RUN,CONFIG,"a".repeat(64),configured(),JSON.nullNode(),1);}
    private CustomerConnectorAdapter.Item item(String owner,boolean revoked,byte[] content){return new CustomerConnectorAdapter.Item("offline-document","v1","offline-account",owner,"text/plain","https://example.invalid/document","2026-10-21T00:00:01Z",content,revoked,JSON.createObjectNode());}
    @Test void incompleteConfigurationIsRepresentableButNeverReady(){var b=draft();assertDoesNotThrow(()->definition(b));assertEquals(List.of("interface_spec","account_mapping","ownership_scope","credential_reference_id"),missing(b));}
    @Test void suppliedInputsHaveNoImplicitApproval(){var b=configured();assertDoesNotThrow(()->definition(b));assertTrue(missing(b).isEmpty());assertFalse(b.has("approved"));}
    @Test void extraConfigurationFieldIsRejected(){var b=draft().put("connected",true);assertThrows(TenantAccessException.class,()->definition(b));}
    @Test void duplicateExternalAccountIsRejected(){var b=configured();b.withArray("account_mapping").add(b.withArray("account_mapping").get(0).deepCopy());assertThrows(TenantAccessException.class,()->definition(b));}
    @Test void crossProjectOwnershipIsRejected(){var b=configured();((ObjectNode)b.required("ownership_scope")).put("project_id",TENANT);assertThrows(TenantAccessException.class,()->definition(b));}
    @Test void unmappedExternalAccountIsRejected(){assertThrows(TenantAccessException.class,()->account(configured(),"different-account"));}
    @Test void emptyRevocationOriginalIsAccepted(){assertDoesNotThrow(()->page(request(),new CustomerConnectorAdapter.Page(List.of(item(PROJECT,true,new byte[0])),JSON.nullNode(),false,List.of(exchange()))));}
    @Test void revocationCannotSmuggleAnOriginal(){assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(item(PROJECT,true,new byte[]{1})),JSON.nullNode(),false,List.of(exchange()))));}
    @Test void crossProjectCaptureCannotEnterPipeline(){assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(item(TENANT,false,new byte[]{1})),JSON.nullNode(),false,List.of(exchange()))));}
    @Test void repeatedCursorCannotClaimMorePages(){assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(),JSON.nullNode(),true,List.of(exchange()))));}
    @Test void duplicateExternalIdsCannotHideAConflict(){var i=item(PROJECT,false,new byte[]{1});assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(i,i),JSON.nullNode(),false,List.of(exchange()))));}
    @Test void originalBytesAreDefensivelyCopied(){byte[] input={1};var i=item(PROJECT,false,input);input[0]=2;assertArrayEquals(new byte[]{1},i.original());i.original()[0]=3;assertArrayEquals(new byte[]{1},i.original());}
    @Test void secretHeadersAreNotPersistedInReceipts(){var headers=JSON.createArrayNode();headers.addObject().put("name","Authorization").put("value","test-only");var e=new CustomerConnectorAdapter.Exchange("GET","https://example.invalid",headers,new byte[0],200,JSON.createArrayNode(),new byte[0],"2026-10-21T00:00:00Z","2026-10-21T00:00:01Z",true);assertThrows(TenantAccessException.class,()->exchanges(List.of(e)));}
    @Test void scalarCursorCanonicalizationPreservesJsonTypeAndExistingObjectDigests()throws Exception{
        assertEquals("null",new String(canonicalBytes(JSON.nullNode()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("\"cursor\"",new String(canonicalBytes(com.fasterxml.jackson.databind.node.TextNode.valueOf("cursor")),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(hash("cursor"),hash(com.fasterxml.jackson.databind.node.TextNode.valueOf("cursor")));
        assertEquals("1",new String(canonicalBytes(JSON.getNodeFactory().numberNode(1)),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("true",new String(canonicalBytes(JSON.getNodeFactory().booleanNode(true)),java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(TenantAccessException.class,()->canonicalBytes(Double.NaN));
        assertThrows(TenantAccessException.class,()->canonicalBytes(Double.POSITIVE_INFINITY));
        assertNotEquals(hash("null"),hash(JSON.nullNode()));
        assertArrayEquals(ContentReadAuthorization.canonicalBytes(configured()),canonicalBytes(configured()));
    }
    @Test void scalarCursorRetainsBoundAndRepeatChecks()throws Exception{
        assertDoesNotThrow(()->page(request(),new CustomerConnectorAdapter.Page(List.of(),com.fasterxml.jackson.databind.node.TextNode.valueOf("next"),true,List.of(exchange()))));
        assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(),JSON.getNodeFactory().numberNode(Double.NaN),false,List.of(exchange()))));
        assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(),com.fasterxml.jackson.databind.node.MissingNode.getInstance(),false,List.of(exchange()))));
        assertThrows(TenantAccessException.class,()->page(request(),new CustomerConnectorAdapter.Page(List.of(),com.fasterxml.jackson.databind.node.TextNode.valueOf("x".repeat(8192)),false,List.of(exchange()))));
    }
    @Test void requestPinsAreDefensivelyCopied(){var b=configured();var r=new CustomerConnectorAdapter.Request(TENANT,PROJECT,RUN,CONFIG,"a".repeat(64),b,JSON.nullNode(),1);b.put("project_id",TENANT);assertEquals(PROJECT,r.definition().required("project_id").asText());}
}
