package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.HashSet;

/** Recovery rejects partial or corrupted evidence instead of upgrading it to success. */
final class SourceDeletionEvidence {
    static void requirePrimary(JsonNode proof,SourceDeletionTarget target,String targetHash,String revision) {
        if(!"2.0.0".equals(proof.path("schema_version").asText())
                ||!targetHash.equals(proof.path("target_sha256").asText())
                ||!revision.equals(proof.path("config_revision").asText())
                ||!target.kind().equals(proof.path("stores").path("kind").asText()))fail();
        WireValues.uuid(proof.path("worker_id").asText());
        if(WireValues.revision(proof.path("generation").asText()).signum()<=0)fail();
        var deleted=WireValues.utcTime(proof.path("deleted_at").asText());
        if(WireValues.utcTime(proof.path("query_confirmed_at").asText()).isBefore(deleted))fail();
        var stores=proof.path("stores").path("stores");
        if(!stores.isArray()||stores.isEmpty())fail();
        for(var store:stores) {
            if(store.path("store_id").asText().isBlank()||!"0".equals(store.path("remaining").asText()))fail();
            if(!store.has("object_key"))continue;
            var receipts=store.path("copy_receipts");
            if(!receipts.isArray()||!Integer.toString(receipts.size()).equals(store.path("deleted_versions").asText()))fail();
            if(!(receipts.isEmpty()?"ALREADY_ABSENT":"DELETE_ACKNOWLEDGED_AND_ABSENT")
                    .equals(store.path("observation").asText()))fail();
            var seen=new HashSet<String>();
            for(var receipt:receipts) {
                String key=receipt.path("object_key").asText(),version=receipt.path("object_version").asText();
                if(key.isBlank()||version.isBlank()||!seen.add(key+"\n"+version)
                        ||!receipt.path("delete_acknowledged").asBoolean(false)
                        ||!receipt.path("absence_confirmed").asBoolean(false)
                        ||receipt.path("provider_request_id").asText().isBlank())fail();
            }
        }
    }
    static void requireComplete(JsonNode evidence,JsonNode receipt,SourceDeletionTarget target,String targetHash,
            String tenant,PermissionSchema json) throws Exception {
        if(!org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(evidence))
                .equals(receipt.path("evidence_sha256").asText()))fail();
        var primary=evidence.path("primary");
        WireValues.uuid(primary.path("copy_authority").path("authority_id").asText());
        WireValues.hash(primary.path("copy_authority").path("content_sha256").asText());
        WireValues.hash(primary.path("copy_authority").path("configuration_sha256").asText());
        String revision=primary.path("config_revision").asText();WireValues.uuid(revision);
        requirePrimary(primary,target,targetHash,revision);
        var backup=evidence.path("backup");
        if(!"2.0.0".equals(backup.path("schema_version").asText())
                ||!tenant.equals(backup.path("tenant_id").asText())
                ||!target.sourceVersion().equals(backup.path("source_version").asText())
                ||!revision.equals(backup.path("config_revision").asText()))fail();
        var stores=backup.path("stores");if(!stores.isArray()||stores.isEmpty())fail();
        var ids=new HashSet<String>();
        for(var store:stores) {
            String id=store.path("store_id").asText();
            if(id.isBlank()||!ids.add(id)||!"0".equals(store.path("remaining").asText()))fail();
            WireValues.uuid(store.path("inventory_revision").asText());WireValues.hash(store.path("catalogue_sha256").asText());
            var inventory=store.path("live_inventory");SourceCopyInventory.requireAbsent(inventory);
            if(!id.equals(inventory.path("store_id").asText())||!tenant.equals(inventory.path("tenant_id").asText())
                    ||!target.sourceVersion().equals(inventory.path("source_version").asText()))fail();
            var copies=store.path("copy_observations");if(!copies.isArray())fail();
            for(var copy:copies) {
                if(copy.path("object_key").asText().isBlank()||copy.path("object_version").asText().isBlank()
                        ||!copy.path("absence_confirmed").asBoolean(false))fail();
                WireValues.hash(copy.path("sha256").asText());
                var observed=WireValues.utcTime(copy.path("observed_at").asText());
                if(observed.isBefore(WireValues.utcTime(copy.path("expires_at").asText())))fail();
                String outcome=copy.path("observation").asText();
                if(!"ALREADY_ABSENT".equals(outcome)&&!("DELETE_ACKNOWLEDGED_AND_ABSENT".equals(outcome)
                        &&!copy.path("provider_request_id").asText().isBlank()))fail();
            }
        }
    }
    static void requireCoverage(JsonNode evidence,SourceDeletionPolicy policy) {
        if(!policy.revision().equals(evidence.path("primary").path("config_revision").asText()))fail();
        var expected=new HashSet<String>();for(var store:policy.backups())expected.add(store.store().id());
        var actual=new HashSet<String>();for(var store:evidence.path("backup").path("stores")) {
            if(!actual.add(store.path("store_id").asText()))fail();
            var configured=policy.backups().stream().filter(b->b.store().id().equals(store.path("store_id").asText())).toList();
            if(configured.size()!=1||!configured.get(0).store().prefix()
                    .equals(store.path("live_inventory").path("scope_prefix").asText()))fail();
        }
        if(!expected.equals(actual)||expected.isEmpty())fail();
    }
    static void requireFinalInventory(JsonNode evidence,SourceDeletionPolicy policy,String tenant,String source) {
        var complete=evidence.path("complete_source_inventory");
        if(!tenant.equals(complete.path("tenant_id").asText())||!source.equals(complete.path("source_version").asText()))fail();
        var inventories=complete.path("objects");if(!inventories.isArray())fail();
        var scopes=new java.util.ArrayList<SourceDeletionPolicy.ObjectStore>(policy.originals());scopes.add(policy.parsed());scopes.add(policy.diagnostics());
        if(inventories.size()!=scopes.size())fail();
        for(int i=0;i<scopes.size();i++) {
            var inventory=inventories.get(i);var expected=scopes.get(i);SourceCopyInventory.requireAbsent(inventory);
            if(!expected.id().equals(inventory.path("store_id").asText())||!expected.prefix().equals(inventory.path("scope_prefix").asText())
                    ||!tenant.equals(inventory.path("tenant_id").asText())||!source.equals(inventory.path("source_version").asText()))fail();
        }
        var vectors=complete.path("vectors");if(!vectors.isArray()||vectors.size()!=policy.vectors().size())fail();
        for(int i=0;i<vectors.size();i++) {
            var actual=vectors.get(i);var expected=policy.vectors().get(i);
            if(!expected.host().equals(actual.path("host").asText())||expected.port()!=actual.path("port").asInt()
                    ||!expected.collection().equals(actual.path("collection").asText())||!"0".equals(actual.path("remaining").asText()))fail();
        }
        if(!(policy.cacheNamespace()+":"+tenant+":"+source+":*").equals(complete.path("cache").path("scope_pattern").asText())
                ||!"0".equals(complete.path("cache").path("remaining").asText()))fail();
    }
    private static void fail(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
