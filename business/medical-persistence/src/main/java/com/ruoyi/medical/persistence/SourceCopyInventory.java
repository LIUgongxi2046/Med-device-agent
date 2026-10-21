package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Evidence produced from a bounded, exhaustive provider listing, not from a caller's complete flag.
 * Completeness is only for the configured store scope. Deployment-wide authority remains the signed
 * backup catalogue and approved deletion configuration; unknown ownership never means unrelated. */
final class SourceCopyInventory {
    record Copy(String key,String version,boolean marker,String tenant,String source,
                String contentHash,Instant expiresAt,Boolean legalHold) { }

    static ObjectNode produce(PermissionSchema json,String store,String prefix,String tenant,String source,
            Instant observedAt,List<Copy> copies,boolean exhausted) {
        WireValues.uuid(tenant); WireValues.uuid(source);
        var result=json.object().put("schema_version","2.0.0").put("store_id",store)
            .put("scope_prefix",prefix).put("tenant_id",tenant).put("source_version",source)
            .put("observed_at",observedAt.toString()).put("authority","CONFIGURED_STORE_SCOPE");
        var entries=result.putArray("copies"); var gaps=result.putArray("gaps");
        Set<String> seen=new HashSet<>();
        if(!exhausted)gaps.add("LISTING_NOT_EXHAUSTED");
        for(var copy:copies) {
            if(copy.key()==null||!copy.key().startsWith(prefix)||copy.version()==null
                    ||copy.version().isBlank()||!seen.add(copy.key()+"\n"+copy.version()))
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            // Delete markers carry no body; all content versions still appear independently.
            if(copy.marker())continue;
            if(copy.tenant()==null||copy.source()==null) { gaps.add("COPY_OWNERSHIP_UNKNOWN");continue; }
            try { WireValues.uuid(copy.tenant()); WireValues.uuid(copy.source()); }
            catch(RuntimeException invalid) { gaps.add("COPY_OWNERSHIP_UNKNOWN");continue; }
            if(!tenant.equals(copy.tenant())||!source.equals(copy.source()))continue;
            var entry=entries.addObject().put("object_key",copy.key()).put("object_version",copy.version())
                .put("observation","PRESENT");
            if(copy.contentHash()==null||!copy.contentHash().matches("[a-f0-9]{64}"))gaps.add("COPY_HASH_UNKNOWN");
            else entry.put("sha256",copy.contentHash());
            if(copy.expiresAt()==null)gaps.add("RETENTION_EXPIRY_UNKNOWN");
            else entry.put("expires_at",copy.expiresAt().toString());
            if(copy.legalHold()==null)gaps.add("LEGAL_HOLD_UNKNOWN");else entry.put("legal_hold",copy.legalHold());
        }
        result.put("complete",gaps.isEmpty());
        result.put("status",!gaps.isEmpty()?"UNKNOWN":entries.isEmpty()?"ABSENT":"PRESENT");
        return result;
    }

    static void requireAbsent(JsonNode inventory) {
        if(!"2.0.0".equals(inventory.path("schema_version").asText())
                ||!"CONFIGURED_STORE_SCOPE".equals(inventory.path("authority").asText())
                ||!inventory.path("complete").asBoolean(false)||!inventory.path("gaps").isArray()
                ||!inventory.path("gaps").isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(!inventory.path("copies").isArray()||!inventory.path("copies").isEmpty()
                ||!"ABSENT".equals(inventory.path("status").asText()))
            throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
    }
}
