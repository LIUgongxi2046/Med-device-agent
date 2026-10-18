package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaCatalogue;

/** Fixed schema/owner semantics only. This descriptor does not create or replace any grant. */
public final class SimulationInputArtifactProfile {
    private SimulationInputArtifactProfile() { }
    public static EvaluationDependencyPolicy.ArtifactProfile profile() throws java.io.IOException {
        return profile("CONFIGURATION");
    }
    public static EvaluationDependencyPolicy.ArtifactProfile profile(String resourceKind) throws java.io.IOException {
        if(!java.util.Set.of("SOURCE","CONFIGURATION").contains(resourceKind))
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH);
        final SchemaCatalogue catalogue;
        try { catalogue=SchemaCatalogue.fromClasspath(); }
        catch(java.security.NoSuchAlgorithmException failure){throw new java.io.IOException("SCHEMA_DIGEST_UNAVAILABLE",failure);}
        return new EvaluationDependencyPolicy.ArtifactProfile(resourceKind,resourceKind.equals("SOURCE")?"source.read":"configuration.read",
            catalogue.schemaRef("simulation-execution","ExactInput"),catalogue.schemaDigest("simulation-execution"),
            catalogue.schemaDigest("simulation-execution"));
    }
}
