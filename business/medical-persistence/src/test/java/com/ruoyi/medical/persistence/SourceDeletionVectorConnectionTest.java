package com.ruoyi.medical.persistence;

import io.grpc.Status;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceDeletionVectorConnectionTest {
    @Test
    void connectsToTheActualLocalQdrantUsingTheWorkerClient() throws Exception {
        Path key = Path.of(System.getProperty("medical.projectRoot"))
            .resolve(".implementation-work/runtime/vector-storage/api-key.txt");
        SourceDeletionPolicy.privateFile(key);
        try (var client = new QdrantClient(QdrantGrpcClient.newBuilder("127.0.0.1",16334,false)
                .withApiKey(Files.readString(key)).build())) {
            assertEquals("1.19.2",client.healthCheckAsync(Duration.ofSeconds(10)).get(10,TimeUnit.SECONDS).getVersion());
            assertNotNull(client.listCollectionsAsync(Duration.ofSeconds(10)).get(10,TimeUnit.SECONDS));
        }
    }

    @Test
    void actualQdrantRejectsCollectionAccessWithoutAnIdentity() throws Exception {
        try (var client = new QdrantClient(QdrantGrpcClient.newBuilder("127.0.0.1",16334,false).build())) {
            var error = assertThrows(ExecutionException.class,
                () -> client.listCollectionsAsync(Duration.ofSeconds(10)).get(10,TimeUnit.SECONDS));
            assertEquals(Status.Code.UNAUTHENTICATED,Status.fromThrowable(error.getCause()).getCode());
        }
    }
}
