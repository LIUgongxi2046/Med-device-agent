package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-deletion")
public record SourceDeletionPolicy(String revision, List<String> tenantIds, Duration polling, Duration lease,
        int maximumEntries, long maximumObjectBytes, List<ObjectStore> originals, ObjectStore parsed,
        ObjectStore diagnostics, List<VectorStore> vectors, List<BackupStore> backups, String cacheNamespace) {
    public SourceDeletionPolicy {
        WireValues.uuid(revision);
        tenantIds = List.copyOf(tenantIds); originals = List.copyOf(originals);
        vectors = List.copyOf(vectors); backups = List.copyOf(backups);
        tenantIds.forEach(WireValues::uuid);
        Objects.requireNonNull(polling); Objects.requireNonNull(lease);
        Objects.requireNonNull(parsed); Objects.requireNonNull(diagnostics); Objects.requireNonNull(cacheNamespace);
        var backupIds=new java.util.HashSet<String>();
        for(var backup:backups)if(!backupIds.add(backup.store().id()))
            throw new IllegalArgumentException("备份存储标识必须唯一");
        var originalIds=new java.util.HashSet<String>();
        for(var original:originals)if(!originalIds.add(original.id()))
            throw new IllegalArgumentException("原件存储标识必须唯一");
        if (tenantIds.isEmpty() || tenantIds.size()>100 || tenantIds.stream().distinct().count()!=tenantIds.size() || originals.isEmpty()
                || vectors.isEmpty() || backups.isEmpty() || maximumEntries<1 || maximumEntries>100000
                || maximumObjectBytes<1 || maximumObjectBytes>1_000_000_000L || polling.toMillis()<100
                || lease.compareTo(Duration.ofSeconds(90))<0 || lease.compareTo(Duration.ofMinutes(10))>0
                || !cacheNamespace.matches("[a-z0-9:-]{1,128}"))
            throw new IllegalArgumentException("删除 Worker 必须具有完整的批准配置");
    }

    public record ObjectStore(String id, URI endpoint, String bucket, String region, String prefix,
            S3ObjectReader.Network network, Path credentialsFile) {
        public ObjectStore {
            Objects.requireNonNull(endpoint); Objects.requireNonNull(network); privateFile(credentialsFile);
            if (id==null || !id.matches("[a-z0-9-]{1,64}") || bucket==null || bucket.isBlank()
                    || region==null || region.isBlank() || prefix==null || prefix.isBlank() || !prefix.endsWith("/")
                    || endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getQuery()!=null
                    || endpoint.getFragment()!=null || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                    || network==S3ObjectReader.Network.LOCAL_LOOPBACK && !("http".equals(endpoint.getScheme())
                        && "127.0.0.1".equals(endpoint.getHost()) && endpoint.getPort()>0)
                    || network==S3ObjectReader.Network.PRIVATE_TLS && !"https".equals(endpoint.getScheme()))
                throw new IllegalArgumentException("删除存储地址与批准范围无效");
        }
    }

    public record VectorStore(String host, int port, boolean tls, String collection, Path apiKeyFile) {
        public VectorStore {
            privateFile(apiKeyFile);
            if (host==null || host.isBlank() || port<1 || port>65535 || !tls && !"127.0.0.1".equals(host)
                    || collection==null || !collection.matches("[a-zA-Z0-9_-]{1,128}"))
                throw new IllegalArgumentException("向量删除配置无效");
        }
    }

    public record BackupStore(ObjectStore store, String catalogueKey, Path signingKeyFile) {
        public BackupStore {
            Objects.requireNonNull(store); Objects.requireNonNull(catalogueKey); privateFile(signingKeyFile);
            if (catalogueKey.isBlank() || !catalogueKey.endsWith("/") || catalogueKey.startsWith(store.prefix()))
                throw new IllegalArgumentException("备份目录证明必须保存在备份数据范围以外");
        }
    }

    static void privateFile(Path path) {
        Objects.requireNonNull(path);
        if (!path.isAbsolute() || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("秘密引用必须使用现有私有文件");
        org.springframework.util.function.ThrowingSupplier.of(() -> {
            if (!Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS)
                    .equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))
                throw new IllegalArgumentException("秘密文件权限必须为0600");
            return null;
        }).get();
    }
}
