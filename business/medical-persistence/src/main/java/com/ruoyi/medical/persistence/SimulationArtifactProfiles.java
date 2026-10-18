package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.util.Optional;

/** Fixed simulation-only interpretation profiles, never permission grants or client-configured schemas. */
final class SimulationArtifactProfiles {
    private SimulationArtifactProfiles() { }
    static Optional<EvaluationDependencyPolicy.ArtifactProfile> known(String ref,String resourceKind)throws IOException {
        var input=SimulationInputArtifactProfile.profile();
        if(input.schemaRef().equals(ref))
            return Optional.of(resourceKind==null?input:SimulationInputArtifactProfile.profile(resourceKind));
        var preparation=SimulationEvaluationPreparationProfile.forSchema(ref);
        if(preparation.isPresent()&&resourceKind!=null&&!preparation.get().resourceKind().equals(resourceKind))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        if(preparation.isPresent())return preparation;
        final com.ruoyi.medical.contracts.SchemaCatalogue catalogue;
        try {catalogue=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();}
        catch(java.security.NoSuchAlgorithmException failure){throw new IOException("SCHEMA_DIGEST_UNAVAILABLE",failure);}
        for(String document:java.util.List.of("model-stream","local-simulation-provenance")) {
            String schemaRef=catalogue.schemaRef(document,"model-stream".equals(document)?"ModelResult":null);
            if(schemaRef.equals(ref)) {
                if(resourceKind!=null&&!"EVALUATION".equals(resourceKind))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                String digest=catalogue.schemaDigest(document);
                return Optional.of(new EvaluationDependencyPolicy.ArtifactProfile("EVALUATION","evaluation.read",schemaRef,digest,digest));
            }
        }
        return Optional.empty();
    }
}
