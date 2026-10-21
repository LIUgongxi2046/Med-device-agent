package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred contract tests; no provider, runtime session, or write authority is fabricated. */
final class TaskCandidateAssemblyContractsTest {
    @Test void approvedRoleDeclarationSelectsExactAssemblyContract()throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();var request=JsonNodeFactory.instance.objectNode();
        request.putObject("output_schema_ref").put("schema_ref",catalogue.schemaRef("artifacts-v2","AssemblyManifest")).put("schema_sha256",catalogue.schemaDigest("artifacts-v2"));
        assertTrue(TaskCandidateAssemblyContracts.fromRole(request).v2());
        request.withObject("/output_schema_ref").put("schema_sha256",catalogue.schemaDigest("artifacts"));
        assertThrows(Exception.class,()->TaskCandidateAssemblyContracts.fromRole(request));
    }
    @Test void missingHistoricalDeclarationRemainsV1DespitePayloadVersion()throws Exception {
        var input=JsonNodeFactory.instance.objectNode().put("schema_version","2.0.0");
        assertFalse(TaskCandidateAssemblyContracts.fromInput(input).v2());
        input.put("body_schema_ref",new TaskWriterEvidenceContract(true).bodyRef());
        assertThrows(Exception.class,()->TaskCandidateAssemblyContracts.fromInput(input));
    }
    @Test void bodyAndManifestDeclarationsCannotMixGenerations()throws Exception {
        var input=JsonNodeFactory.instance.objectNode();var v2=new TaskWriterEvidenceContract(true);
        TaskCandidateAssemblyContracts.declare(input,v2);assertEquals(v2,TaskCandidateAssemblyContracts.fromInput(input));
        input.put("body_schema_sha256",new TaskWriterEvidenceContract(false).bodySha());
        assertThrows(Exception.class,()->TaskCandidateAssemblyContracts.fromInput(input));
    }
    @Test void nonAssemblyRoleOutputCannotAuthorizeAssembly()throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();var request=JsonNodeFactory.instance.objectNode();
        request.putObject("output_schema_ref").put("schema_ref",catalogue.schemaRef("artifacts-v2","SectionDraft")).put("schema_sha256",catalogue.schemaDigest("artifacts-v2"));
        assertThrows(Exception.class,()->TaskCandidateAssemblyContracts.fromRole(request));
    }
}
