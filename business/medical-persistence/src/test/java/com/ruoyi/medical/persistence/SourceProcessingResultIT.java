package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceProcessingResultIT {
    private final Path commandPath=requiredPath("medical.processing.command");
    private final Path resultPath=requiredPath("medical.processing.result");

    @Test
    void javaConsumesActualPythonBundleAndFixedLocatorSchemas() throws Exception {
        var results=new SourceProcessingResult();
        var command=new JsonMapper().readTree(Files.readAllBytes(commandPath));
        var verified=results.local(command,resultPath,268435456);
        assertTrue(verified.bundle().required("nodes").size()>175);
        assertEquals(verified.bundle().required("nodes").size(),verified.bundle().required("chunks").size());
        assertEquals(command.required("original_sha256"),verified.bundle().required("scan").required("original_sha256"));
        assertEquals(0,verified.bundle().required("scan").required("returncode").intValue());
        assertEquals(4,verified.bundle().required("scan").required("artifacts").size());
        assertEquals(verified.receipt().required("status"),verified.bundle().required("status"));
    }

    @Test
    void actualOriginalCannotBeAcceptedAsItsParsedArtifact() throws Exception {
        var results=new SourceProcessingResult();
        var command=new JsonMapper().readTree(Files.readAllBytes(commandPath));
        var receipt=new JsonMapper().readTree(Files.readAllBytes(resultPath));
        byte[] original=Files.readAllBytes(Path.of(command.required("source_path").textValue()));
        assertThrows(JsonParseException.class,() -> results.verify(command,receipt,original));
    }

    private static Path requiredPath(String property) {
        String value=java.util.Objects.requireNonNull(System.getProperty(property),"实际解析验证输入缺少："+property);
        Path path=Path.of(value);
        if(!path.isAbsolute() || !Files.isRegularFile(path)) throw new IllegalArgumentException("实际解析验证输入路径无效："+property);
        return path;
    }
}
