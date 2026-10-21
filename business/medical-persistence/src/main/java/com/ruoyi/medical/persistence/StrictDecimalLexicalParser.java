package com.ruoyi.medical.persistence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic lexical stage only, never a source-read/approval receipt. The production host
 * must supply a verified original-derived chunk and persist its own genuine read provenance.
 * No candidate value, expected decimal or unit-conversion parameter is accepted.
 */
public final class StrictDecimalLexicalParser {
    public static final String CONTRACT = "PLAIN_DECIMAL_SCALE_V1";
    private static final Pattern DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    public record Parsed(String contract, String chunkSha256, int startCodepoint, int endCodepoint,
            String rawText, String rawTextSha256, String decimalValue, int scale) { }
    private final int maximumChunkCodepoints;
    public StrictDecimalLexicalParser(int maximumChunkCodepoints) {
        if (maximumChunkCodepoints < 1) throw new IllegalArgumentException("DECIMAL_CAPACITY_REQUIRED");
        this.maximumChunkCodepoints = maximumChunkCodepoints;
    }
    public Parsed parse(String chunk, String expectedChunkSha256, int startCodepoint, int endCodepoint) {
        Objects.requireNonNull(chunk);
        // Bound UTF-16 traversal before allocating code-point or UTF-8 buffers.
        if ((long) chunk.length() > (long) maximumChunkCodepoints * 2) reject("DECIMAL_CONTEXT_LIMIT");
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= chunk.length() || !Character.isLowSurrogate(chunk.charAt(i))) reject("INVALID_UNICODE_CHUNK");
            } else if (Character.isLowSurrogate(c)) reject("INVALID_UNICODE_CHUNK");
        }
        int count = chunk.codePointCount(0, chunk.length());
        if (count > maximumChunkCodepoints) reject("DECIMAL_CONTEXT_LIMIT");
        if (startCodepoint < 0 || startCodepoint >= endCodepoint || endCodepoint > count) reject("DECIMAL_SPAN_INVALID");
        String chunkHash = sha256(chunk);
        if (!chunkHash.equals(expectedChunkSha256)) reject("DECIMAL_CHUNK_DIGEST_MISMATCH");
        String raw = chunk.substring(chunk.offsetByCodePoints(0, startCodepoint), chunk.offsetByCodePoints(0, endCodepoint));
        // DECIMAL(38,12), LLD-backend §data types: reject overflow, never round or truncate.
        if (raw.length() > 40 || !DECIMAL.matcher(raw).matches()) reject("UNSUPPORTED_DECIMAL_LEXICAL");
        BigDecimal decimal = new BigDecimal(raw);
        if (decimal.scale() > 12 || decimal.precision() > 38 || decimal.precision() - decimal.scale() > 26)
            reject("DECIMAL_STORAGE_RANGE_EXCEEDED");
        return new Parsed(CONTRACT, chunkHash, startCodepoint, endCodepoint, raw, sha256(raw), raw, decimal.scale());
    }
    private static String sha256(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void reject(String reason) { throw new IllegalArgumentException(reason); }
}
