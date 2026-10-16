package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.ExportMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ExportSchemaTest {
    @Test
    void requestAndResponsesRemainClosed() throws Exception {
        var values = new PermissionSchema(); var schema = new ExportSchema();
        String id = "11111111-1111-4111-8111-111111111111", hash = "a".repeat(64);
        var request = values.object(); request.put("schema_version", "1.0.0"); request.put("document_revision", id);
        request.put("purpose", "INTERNAL_EXCHANGE"); request.putArray("formats").add("DOCX").add("MARKDOWN");
        request.put("template_version", id); request.put("format_profile_revision", id);
        request.putNull("channel_profile_revision"); request.put("review_manifest_sha256", hash);
        assertEquals(2, schema.create(request.toString().getBytes(StandardCharsets.UTF_8)).formats().size());
        request.put("unknown", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.create(request.toString().getBytes(StandardCharsets.UTF_8)));

        var item = values.object(); item.put("export_item_id", id); item.put("format", "DOCX"); item.put("attempt_no", "1");
        item.put("status", "QUEUED"); item.putNull("artifact_id"); item.putNull("validation_artifact_id");
        item.putNull("validation");
        item.put("downloadable", false); item.put("revision", "1"); item.put("created_at", "2026-10-11T12:00:00Z");
        item.put("updated_at", "2026-10-11T12:00:00Z");
        var view = values.object(); view.put("schema_version", "1.0.0"); view.put("export_id", id); view.put("document_id", id);
        view.put("document_revision", id); view.put("purpose", "INTERNAL_EXCHANGE"); view.put("status", "QUEUED");
        view.put("template_version", id); view.put("review_manifest_sha256", hash); view.put("format_profile_revision", id);
        view.putNull("channel_profile_revision"); view.put("manifest_sha256", hash); view.put("revision", "1");
        view.putArray("items").add(item); view.put("created_at", "2026-10-11T12:00:00Z"); view.put("updated_at", "2026-10-11T12:00:00Z");
        assertEquals(id, schema.envelope("View", view, id, "1").required("data").required("export_id").textValue());
        view.put("unknown", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.envelope("View", view, id, "1"));

        var process = values.object(); process.put("schema_version", "1.0.0"); process.put("expected_revision", "1");
        assertEquals("1", schema.process(process.toString().getBytes(StandardCharsets.UTF_8)).expected_revision());

        var file = values.object(); file.put("schema_version", "1.0.0"); file.put("format", "PDF");
        file.put("media_type", "application/pdf"); file.put("sha256", hash); file.put("size_bytes", "128");
        assertDoesNotThrow(() -> schema.verify("File", file));
        file.put("unknown", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.verify("File", file));

        view.remove("unknown");
        var history = values.object(); history.put("schema_version", "1.0.0"); history.put("document_id", id);
        history.put("document_revision", id); history.put("page", "1"); history.put("size", "20"); history.put("total", "1");
        history.put("has_more", false); history.putArray("items").add(view);
        assertDoesNotThrow(() -> schema.verify("History", history));
    }

    @Test
    void mapperStatementsAndConstructorsRemainLoadable() throws Exception {
        var configuration = new MedicalPersistenceConfiguration()
                .medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var parameters = Map.<String,Object>of("userId", "11111111-1111-4111-8111-111111111111");
        for (var method : ExportMapper.class.getDeclaredMethods()) {
            String name = ExportMapper.class.getName() + "." + method.getName();
            String sql = configuration.getMappedStatement(name).getBoundSql(parameters).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        }
        assertConstructor(configuration, "document", ExportProtocol.DocumentRow.class);
        assertConstructor(configuration, "signature", ExportProtocol.SignatureRow.class);
        assertConstructor(configuration, "version", ExportProtocol.VersionRow.class);
        assertConstructor(configuration, "manifest", ExportProtocol.ManifestRow.class);
        assertConstructor(configuration, "item", ExportProtocol.ItemRow.class);
    }

    @Test
    void everyMapperStatementRequiresCurrentSubject() {
        for (var method : ExportMapper.class.getDeclaredMethods())
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(
                    ExportMapper.class.getName() + "." + method.getName(), Map.of()));
    }

    private static void assertConstructor(org.apache.ibatis.session.Configuration configuration, String map,
            Class<?> type) throws Exception {
        var mapping = configuration.getResultMap(ExportMapper.class.getName() + "." + map);
        Class<?>[] parameters = mapping.getConstructorResultMappings().stream()
                .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(type.getDeclaredConstructor(parameters));
    }
}
