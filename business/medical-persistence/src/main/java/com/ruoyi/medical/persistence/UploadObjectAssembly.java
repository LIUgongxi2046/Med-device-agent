package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

final class UploadObjectAssembly implements AutoCloseable {
    private final BigInteger total;
    private final String expectedSha256;
    private final S3ObjectReader.Admission admission;
    private final Path content;
    private final FileChannel channel;
    private final MessageDigest digest;
    private BigInteger received = BigInteger.ZERO;
    private long nextPart = 1;
    private boolean finished;
    private boolean failed;
    private boolean closed;

    UploadObjectAssembly(Path privateDirectory, long maximumBytes, BigInteger total, String expectedSha256,
            S3ObjectReader.Admission admission) throws IOException, NoSuchAlgorithmException {
        this.admission = Objects.requireNonNull(admission); admission.requireCurrent();
        WireValues.revision(Objects.requireNonNull(total).toString()); WireValues.hash(expectedSha256);
        if (maximumBytes <= 0 || total.signum() <= 0) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (total.compareTo(BigInteger.valueOf(maximumBytes)) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        UploadTransferStore.range(total, 1);
        var directory = Objects.requireNonNull(privateDirectory).toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        this.total = total; this.expectedSha256 = expectedSha256;
        digest = MessageDigest.getInstance("SHA-256");
        content = Files.createTempFile(directory, "upload-assembly-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        boolean opened = false;
        try {
            channel = FileChannel.open(content, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            opened = true;
        } finally { if (!opened) Files.deleteIfExists(content); }
    }

    synchronized void append(long partNo, ObjectRef reference, S3ObjectReader reader) throws Exception {
        requireOpen(); if (finished) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        admission.requireCurrent(); Objects.requireNonNull(reference); Objects.requireNonNull(reader);
        if (partNo != nextPart) mismatch();
        var range = UploadTransferStore.range(total, partNo);
        if (!range.start().equals(received) || !range.size().toString().equals(reference.sizeBytes())
                || !"application/octet-stream".equals(reference.mediaType())) mismatch();
        boolean accepted = false;
        try (var verified = reader.readVerified(reference, admission)) {
            // 使用实际固定版本的完整核验结果，按原上传范围连续写入。
            for (long start = 0, size = range.size().longValueExact(); start < size;) {
                int length = (int) Math.min(S3ObjectReader.MAX_SEGMENT_BYTES, size - start);
                byte[] bytes = verified.chunk(start, length); admission.requireCurrent();
                digest.update(bytes); var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                start += length;
            }
            if (channel.size() != range.end().longValueExact()) mismatch();
            admission.requireCurrent(); received = range.end(); nextPart++; accepted = true;
        } finally { if (!accepted) failed = true; }
    }

    synchronized Path finish() throws IOException {
        requireOpen(); admission.requireCurrent();
        if (!received.equals(total)) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if (!finished) {
            channel.force(true);
            if (!expectedSha256.equals(HexFormat.of().formatHex(digest.digest()))) { failed = true; mismatch(); }
            finished = true;
        }
        if (!Files.isRegularFile(content, LinkOption.NOFOLLOW_LINKS) || Files.size(content) != total.longValueExact()
                || !Files.getPosixFilePermissions(content, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))) mismatch();
        try (var input = Files.newInputStream(content, LinkOption.NOFOLLOW_LINKS)) {
            if (!expectedSha256.equals(DigestUtils.sha256Hex(input))) mismatch();
        }
        admission.requireCurrent(); return content;
    }

    private void requireOpen() {
        if (closed || failed) throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    @Override public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            try { channel.close(); } finally { Files.deleteIfExists(content); }
        }
    }
}
