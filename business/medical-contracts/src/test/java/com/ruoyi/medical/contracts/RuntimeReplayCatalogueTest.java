package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeReplayCatalogueTest {
    final Path root=Path.of(System.getProperty("medical.projectRoot", System.getProperty("simulation.bridge.project", "../..")));
    @Test void registersCompleteIndependentReplayAndRetainsFrozenBase() throws Exception {
        var catalogue=SchemaCatalogue.fromProject(root);
        byte[] replay=Files.readAllBytes(root.resolve("agent-design/replay-manifest.schema.json"));
        assertEquals("replay-manifest",catalogue.requirePublished("urn:medical:replay-manifest:1.0.0",WireValues.sha256(replay)).name());
        assertEquals("dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915",WireValues.sha256(Files.readAllBytes(root.resolve("contracts/catalogue.json"))));
    }
    @Test void tenIdentityFieldsAloneCannotBecomeAReplay() throws Exception {
        var catalogue=SchemaCatalogue.fromProject(root);var value=JsonMapper.builder().build().createObjectNode();
        value.put("schema_version","1.0.0");
        for(String key:new String[]{"tenant_id","project_id","initiator_id","task_id","run_id","agent_release_id","task_revision","plan_revision","snapshot_id","budget_revision"})value.put(key,"00000000-0000-4000-8000-000000000001");
        assertThrows(SchemaRejection.class,()->catalogue.bind("replay-manifest",value,null));
    }
    @Test void trustedExtensionBytesCannotBeReplacedByCaller() throws Exception {
        byte[] base=Files.readAllBytes(root.resolve("contracts/catalogue.json"));
        byte[] evaluation=Files.readAllBytes(root.resolve("contracts/extensions/evaluation-results.catalogue.json"));
        byte[] simulation=Files.readAllBytes(root.resolve("contracts/extensions/simulation-execution.catalogue.json"));
        byte[] runtime=Files.readAllBytes(root.resolve("contracts/extensions/task-runtime.catalogue.json"));
        runtime[runtime.length-1]=' ';
        assertThrows(IllegalArgumentException.class,()->SchemaCatalogue.publishedManifest(base,evaluation,simulation,runtime));
    }
    @Test void unrelatedPublishedSchemaCannotMasqueradeAsReplay() throws Exception {
        var catalogue=SchemaCatalogue.fromProject(root);
        assertThrows(SchemaRejection.class,()->catalogue.requirePublished("urn:medical:replay-manifest:1.0.0",catalogue.schemaDigest("tasks")));
    }
}
