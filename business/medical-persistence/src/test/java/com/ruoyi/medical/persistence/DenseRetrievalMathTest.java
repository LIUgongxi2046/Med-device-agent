package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: authored during implementation; intentionally not executed at user's request. */
class DenseRetrievalMathTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void actualFiniteNonzeroVectorsOnly() throws Exception {
        assertEquals(List.of(0.25,-0.5),DenseEmbeddingGateway.vector(json.readTree("[0.25,-0.5]"),2));
        assertThrows(TenantAccessException.class,()->DenseEmbeddingGateway.vector(json.readTree("[0,0]"),2));
        assertThrows(TenantAccessException.class,()->DenseEmbeddingGateway.vector(json.readTree("[1]"),2));
        assertThrows(TenantAccessException.class,()->DenseEmbeddingGateway.vector(json.readTree("[\"1\",0]"),2));
        assertThrows(TenantAccessException.class,()->DenseEmbeddingGateway.vector(json.readTree("[1e309,1]"),2));
    }
    @Test void cosineUsesRealDirectionNotArbitraryIdentifiers() {
        assertEquals(1.0,DenseRetrievalService.cosine(List.of(1.0,2.0),List.of(2.0,4.0)),1e-12);
        assertEquals(0.0,DenseRetrievalService.cosine(List.of(1.0,0.0),List.of(0.0,1.0)),1e-12);
        assertEquals(-1.0,DenseRetrievalService.cosine(List.of(1.0,0.0),List.of(-1.0,0.0)),1e-12);
    }
    @Test void reciprocalRanksAreFixedAndMonotonic() {
        assertEquals(new BigDecimal("0.0163934426229508"),DenseRetrievalService.rrf(0));
        assertTrue(DenseRetrievalService.rrf(0).compareTo(DenseRetrievalService.rrf(1))>0);
    }
}
