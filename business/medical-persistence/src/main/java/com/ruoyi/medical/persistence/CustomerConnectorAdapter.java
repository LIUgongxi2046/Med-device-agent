package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** An adapter is registered only for its exact specification digest. Synthetic demonstration
 * adapters must be explicitly isolated and never stand in for customer-approved compatibility.
 * No fallback protocol exists. Recovery must never silently repeat an uncertain exchange.
 * Implementations recheck Current before transport, during bounded reads and after transport.
 * Credentials are existing approved handles, never supplied secrets or minted credentials. */
public interface CustomerConnectorAdapter {
    String specificationSha256();
    /** Exact deployed provider and endpoint identities; never inferred from a hostname. */
    record CredentialTarget(String provider,String endpoint) { }
    CredentialTarget credentialTarget(Request request);
    record Request(String tenantId, String projectId, String operationId,
            String configurationRevision, String definitionSha256, JsonNode definition,
            JsonNode cursor, int maximumItems) {
        public Request { definition=definition.deepCopy(); cursor=cursor.deepCopy(); }
        @Override public JsonNode definition(){return definition.deepCopy();}
        @Override public JsonNode cursor(){return cursor.deepCopy();}
    }
    record Exchange(String requestMethod, String requestUri, JsonNode requestHeaders,
            byte[] requestBody, int responseStatus, JsonNode responseHeaders,
            byte[] responseBody, String startedAt, String completedAt, boolean complete) {
        public Exchange { requestBody=requestBody.clone(); responseBody=responseBody.clone();
            requestHeaders=requestHeaders.deepCopy(); responseHeaders=responseHeaders.deepCopy(); }
        @Override public byte[] requestBody(){return requestBody.clone();}
        @Override public byte[] responseBody(){return responseBody.clone();}
        @Override public JsonNode requestHeaders(){return requestHeaders.deepCopy();}
        @Override public JsonNode responseHeaders(){return responseHeaders.deepCopy();}
    }
    record Verification(boolean verified, String externalAccountId,
            JsonNode actualScope, List<Exchange> exchanges) {
        public Verification { exchanges=List.copyOf(exchanges); actualScope=actualScope.deepCopy(); }
        @Override public JsonNode actualScope(){return actualScope.deepCopy();}
    }
    record Item(String externalId, String externalVersion, String externalAccountId,
            String ownerProjectId, String mediaType, String originalUrl, String acquiredAt,
            byte[] original, boolean revoked, JsonNode metadata) {
        public Item { original=original.clone(); metadata=metadata.deepCopy(); }
        @Override public byte[] original(){return original.clone();}
        @Override public JsonNode metadata(){return metadata.deepCopy();}
    }
    record Page(List<Item> items, JsonNode nextCursor, boolean hasMore, List<Exchange> exchanges) {
        public Page { items=List.copyOf(items); exchanges=List.copyOf(exchanges); nextCursor=nextCursor.deepCopy(); }
        @Override public JsonNode nextCursor(){return nextCursor.deepCopy();}
    }
    @FunctionalInterface interface Current { void require() throws Exception; }
    Verification verify(Request request, boolean recoveryOnly, Current current) throws Exception;
    Page readPage(Request request, boolean recoveryOnly, Current current) throws Exception;
}
