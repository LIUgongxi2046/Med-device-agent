package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationCatalogueCompatibilityTest {
    @Test void newEvaluationArtifactsDoNotInvalidateExistingPermissionManifests() throws Exception {
        // This is the pre-extension permission identity, not a regenerated expected value.
        assertEquals("6345592f8753d1f0a392d773790c2f63c975145d84228226c10720a69ac707c6", new PermissionSchema().manifestSha256());
        assertEquals(EvaluationSchema.EXPECTED_ITEMS_REF, SchemaCatalogue.fromClasspath().schemaRef("evaluation-results", "ExpectedItems"));
        assertEquals(EvaluationSchema.SAMPLE_MANIFEST_REF, SchemaCatalogue.fromClasspath().schemaRef("evaluation-results", "SampleManifest"));
    }
}
