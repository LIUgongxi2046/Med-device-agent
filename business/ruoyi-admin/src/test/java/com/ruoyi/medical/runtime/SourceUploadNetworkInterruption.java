package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.digest.DigestUtils;
import static org.junit.jupiter.api.Assertions.*;

final class SourceUploadNetworkInterruption implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    final Path storage;
    private final Path root, directory;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Process process;
    private URI control;

    SourceUploadNetworkInterruption(Path root) throws Exception {
        this.root = root;
        directory = Files.createDirectory(root.resolve(".implementation-work/source-upload-network-" + UUID.randomUUID()),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        storage = Files.createDirectory(directory.resolve("storage"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    void start() throws Exception {
        Path archive = root.resolve(".implementation-work/toolchains/toxiproxy_2.12.0_darwin_amd64.tar.gz");
        assertEquals("52ac1f99d7204b4b523f910b791bc86318b50ef94822c79a6157606704003ced", DigestUtils.sha256Hex(Files.readAllBytes(archive)));
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        process = new ProcessBuilder(root.resolve(".implementation-work/toolchains/toxiproxy-2.12.0/toxiproxy-server").toString(),
            "-host", "127.0.0.1", "-port", Integer.toString(port)).redirectErrorStream(true)
            .redirectOutput(directory.resolve("proxy.log").toFile()).start();
        control = URI.create("http://127.0.0.1:" + port);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            assertTrue(process.isAlive(), "网络检查进程提前终止");
            return new ProcessBuilder("/usr/sbin/lsof", "-nP", "-a", "-p", Long.toString(process.pid()), "-iTCP:" + port,
                "-sTCP:LISTEN").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor() == 0;
        });
        var version = http.send(HttpRequest.newBuilder(control.resolve("/version")).timeout(Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, version.statusCode()); assertTrue(version.body().contains("2.12.0"));
        Path original = root.resolve(".implementation-work/runtime/object-storage");
        byte[] deploymentBytes = Files.readAllBytes(original.resolve("deployment.json"));
        var deployment = (ObjectNode) JSON.readTree(deploymentBytes);
        var endpoint = URI.create(deployment.required("endpoint").textValue());
        assertEquals("127.0.0.1", endpoint.getHost()); assertEquals("http", endpoint.getScheme());
        var proxy = call("POST", "/proxies", JSON.createObjectNode().put("name", "source-original-s3")
            .put("listen", "127.0.0.1:0").put("upstream", endpoint.getHost() + ":" + endpoint.getPort()).put("enabled", true), 201);
        deployment.put("endpoint", "http://" + proxy.required("listen").textValue());
        deployment.put("original_endpoint", endpoint.toString()); deployment.put("original_deployment_sha256", DigestUtils.sha256Hex(deploymentBytes));
        Files.write(storage.resolve("deployment.json"), JSON.writeValueAsBytes(deployment));
        var secret = JSON.readTree(Files.readAllBytes(original.resolve("service-secret.json")));
        assertEquals(deployment.required("service_id"), secret.required("service_id"));
        Path target = Files.createFile(storage.resolve("service-secret.json"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(target, JSON.writeValueAsBytes(secret));
    }

    void interruptResponses() throws Exception {
        // 原S3仍接收真实请求并保存对象，仅本次连接的响应受到控制。
        var toxic = JSON.createObjectNode().put("name", "source-response-interruption").put("type", "timeout")
            .put("stream", "downstream").put("toxicity", 1.0);
        toxic.set("attributes", JSON.createObjectNode().put("timeout", 0));
        call("POST", "/proxies/source-original-s3/toxics", toxic, 200);
    }

    void restoreResponses() throws Exception {
        var response = http.send(HttpRequest.newBuilder(control.resolve("/proxies/source-original-s3/toxics/source-response-interruption"))
            .timeout(Duration.ofSeconds(5)).DELETE().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(204, response.statusCode());
    }

    private JsonNode call(String method, String path, JsonNode body, int status) throws Exception {
        var response = http.send(HttpRequest.newBuilder(control.resolve(path)).timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(status, response.statusCode()); return JSON.readTree(response.body());
    }

    @Override public void close() throws Exception {
        try {
            if (process != null) { process.destroy(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertFalse(process.isAlive()); }
        } finally { Files.deleteIfExists(storage.resolve("service-secret.json")); }
    }
}
