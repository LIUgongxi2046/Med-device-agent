package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskScopeSelectionSchemaTest {
    private final PermissionSchema json = new PermissionSchema();
    private final TaskScopeSelectionSchema schema = new TaskScopeSelectionSchema(json, new TaskInputSchema(json));
    TaskScopeSelectionSchemaTest() throws Exception { }
    private static String id(int n) { return String.format("00000000-0000-4000-8000-%012d", n); }

    @Test void queryRequiresOneReleaseAndRejectsClientPurpose() {
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of()));
        var query = schema.query(Map.of("agent_release_id", new String[]{id(1)}));
        assertEquals(id(1), query.agentReleaseId()); assertEquals(20, query.limit());
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("agent_release_id", new String[]{id(1), id(2)})));
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("agent_release_id", new String[]{"bad"})));
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("agent_release_id", new String[]{id(1)}, "purpose", new String[]{"DOCUMENT_BODY"})));
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("agent_release_id", new String[]{id(1)}, "after", new String[]{id(2)})));
    }

    @Test void pageRequiresReleaseEchoWithoutAcceptingPurpose() {
        var page = json.object(); page.put("schema_version", "1.0.0"); page.put("tenant_id", id(1)); page.put("project_id", id(2));
        page.put("context_revision", "1"); page.put("auth_epoch", "1"); page.put("governance_epoch", "1");
        page.put("scope_kind", "SOURCE"); page.put("limit", 20); page.putArray("items"); page.putNull("next_after");
        page.put("filter_sha256", "a".repeat(64)); page.put("dataset_watermark", "b".repeat(64));
        assertThrows(SchemaRejection.class, () -> schema.envelope(page, id(3)));
        page.put("agent_release_id", id(4)); assertNotNull(schema.envelope(page, id(3)));
        page.put("purpose", "DOCUMENT_BODY"); assertThrows(SchemaRejection.class, () -> schema.envelope(page, id(3)));
    }
}
