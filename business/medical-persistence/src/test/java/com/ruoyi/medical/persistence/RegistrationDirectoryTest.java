package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationDirectoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode directory() throws Exception {
        try (var source = RegistrationDirectoryTest.class.getResourceAsStream("/registration-directory-2021-121.json")) {
            assertNotNull(source, "首次注册模板必须提供六章及全部38个二级节点");
            return JSON.readTree(source);
        }
    }

    private void requireComplete(JsonNode nodes) throws Exception {
        var type = Class.forName("com.ruoyi.medical.persistence.RegistrationDirectory");
        var current = type.getMethod("fromClasspath").invoke(null);
        type.getMethod("requireComplete", JsonNode.class).invoke(current, nodes);
    }

    @Test void entireDirectoryMatchesActualFixedOfficialSourceAndOriginalOrder() throws Exception {
        var actual = directory();
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var input = root.resolve("ui-delivery/research/official/registration-requirements-2021-121.txt");
        byte[] bytes = Files.readAllBytes(input);
        assertEquals("65877d3a85d80bd867ffbf719908457d39d956a15e7a77a382934809fb569b56", DigestUtils.sha256Hex(bytes));
        assertEquals(DigestUtils.sha256Hex(bytes), actual.required("source_sha256").textValue());
        assertEquals("official-requirements-2021-121-a5-text", actual.required("source_id").textValue());
        // 固定来源前四行是标题，随后44行是完整官方目录；摘要变化必须重新核查来源。
        List<String> expected = Files.readAllLines(input).subList(4, 48);
        var observed = new ArrayList<String>(); int chapters = 0, children = 0;
        for (var node : actual.required("nodes")) {
            String number = node.required("official_number").textValue();
            boolean chapter = node.required("parent_number").isNull();
            if (chapter) chapters++; else children++;
            observed.add(number + (chapter ? "." : "") + node.required("title").textValue());
        }
        assertEquals(6, chapters); assertEquals(38, children); assertEquals(expected, observed);
        assertDoesNotThrow(() -> requireComplete(actual.required("nodes")));
    }

    @Test void missingConditionalNodeStillPreventsCompleteDirectory() throws Exception {
        var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) directory().required("nodes").deepCopy();
        nodes.remove(7);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> requireComplete(nodes));
        assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
    }

    @Test void originalQualitySystemTitlesCannotBeExchanged() throws Exception {
        var nodes = directory().required("nodes").deepCopy();
        var overview = (com.fasterxml.jackson.databind.node.ObjectNode) nodes.get(34);
        var contents = (com.fasterxml.jackson.databind.node.ObjectNode) nodes.get(35);
        assertEquals("6.1", overview.required("official_number").textValue());
        assertEquals("6.2", contents.required("official_number").textValue());
        String title = overview.required("title").textValue();
        overview.put("title", contents.required("title").textValue()); contents.put("title", title);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> requireComplete(nodes));
        assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
    }

    @Test void originalNodeCannotBeMovedToAnotherChapter() throws Exception {
        var nodes = directory().required("nodes").deepCopy();
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) nodes.get(1);
        assertEquals("1.1", item.required("official_number").textValue()); item.put("parent_number", "2");
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> requireComplete(nodes));
        assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
    }
}
