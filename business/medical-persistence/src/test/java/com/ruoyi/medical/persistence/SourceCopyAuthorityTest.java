package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: pure contract/decision cases. Provider and database integration remain NOT_RUN. */
class SourceCopyAuthorityTest {
    @Test void samePersonCannotIndependentlyApprove(){
        assertThrows(TenantAccessException.class,()->SourceCopyAuthority.requireIndependent("declarer","declarer"));
        assertDoesNotThrow(()->SourceCopyAuthority.requireIndependent("declarer","reviewer"));
    }
    @Test void scopeApprovalCannotHaveUnboundedOrExpiredLifetime(){
        var now=Instant.parse("2026-10-21T00:00:00Z");
        assertThrows(SchemaRejection.class,()->SourceCopyAuthority.requireWindow(now,now));
        assertThrows(SchemaRejection.class,()->SourceCopyAuthority.requireWindow(now,now.plusSeconds(86401)));
        assertDoesNotThrow(()->SourceCopyAuthority.requireWindow(now,now.plusSeconds(86400)));
    }
    @Test void callerCannotSupplyApprovalInventoryOrCompleteFlag()throws Exception{
        var json=JsonMapper.builder().build();
        var schemaDocument=json.readTree(getClass().getClassLoader().getResourceAsStream("source-copy-authority-v2.schema.json"));
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaDocument.at("/$defs/Decide"),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var request=json.createObjectNode().put("schema_version","2.0.0").put("kind","APPROVE")
            .put("declaration_id","11111111-1111-4111-8111-111111111111").put("declaration_sha256","a".repeat(64)).put("reason","Independent review");
        assertTrue(schema.validate(request).isEmpty());
        request.put("complete",true);assertFalse(schema.validate(request).isEmpty());
        request.remove("complete");request.putObject("inventory").put("complete",true);
        assertFalse(schema.validate(request).isEmpty());
    }
    @Test void unknownScopeCannotBeHiddenByOmittingDeclarationFields()throws Exception{
        var json=JsonMapper.builder().build();
        var document=json.readTree(getClass().getClassLoader().getResourceAsStream("source-copy-authority-v2.schema.json"));
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document.at("/$defs/Declare"));
        var request=json.createObjectNode().put("schema_version","2.0.0").put("kind","DECLARE")
            .put("configuration_sha256","a".repeat(64)).put("reason","Scope owner declaration")
            .put("valid_until","2026-10-21T12:00:00Z").put("all_copy_locations_declared",true);
        assertFalse(schema.validate(request).isEmpty());request.putArray("external_scopes");
        assertTrue(schema.validate(request).isEmpty());request.put("all_copy_locations_declared",false);
        assertFalse(schema.validate(request).isEmpty());
    }
    @Test void v1CannotSelectV2Declaration()throws Exception{
        var json=JsonMapper.builder().build();
        var document=json.readTree(getClass().getClassLoader().getResourceAsStream("source-copy-authority-v2.schema.json"));
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document.at("/$defs/Decide"));
        var request=json.createObjectNode().put("schema_version","1.0.0").put("kind","APPROVE")
            .put("declaration_id","11111111-1111-4111-8111-111111111111").put("declaration_sha256","a".repeat(64)).put("reason","Review");
        assertFalse(schema.validate(request).isEmpty());
    }
    @Test void missingFinalGlobalInventoryCannotBePromoted()throws Exception{
        var json=JsonMapper.builder().build();
        assertThrows(TenantAccessException.class,()->SourceDeletionEvidence.requireFinalInventory(json.createObjectNode(),null,"tenant","source"));
    }
}
