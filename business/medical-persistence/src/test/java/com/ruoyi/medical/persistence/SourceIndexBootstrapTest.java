package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static org.junit.jupiter.api.Assertions.*;

class SourceIndexBootstrapTest {
    @Test
    void actualIndexDefinitionsAndArtifactHaveACompletePublishedIdentity() throws Exception {
        var manifest=new SourceIndexManifest();
        assertTrue(manifest.sha256().matches("[a-f0-9]{64}"));
        assertTrue(manifest.artifactDigest().matches("[a-f0-9]{64}"));
        assertEquals(manifest.artifactDigest(),manifest.artifactDigest());
        var enabled=SourceIndexConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("medical.source-index",enabled.prefix()); assertArrayEquals(new String[]{"enabled"},enabled.name());
        assertEquals("true",enabled.havingValue()); assertFalse(enabled.matchIfMissing());
    }
}
