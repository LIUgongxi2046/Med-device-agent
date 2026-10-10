package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WireValues {
    private static final BigInteger UNSIGNED_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger SAFE_INTEGER = BigInteger.ONE.shiftLeft(53).subtract(BigInteger.ONE);
    private static final Pattern REVISION = Pattern.compile("(?:0|[1-9][0-9]*)");
    private static final Pattern DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern UTC = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?(?:Z|\\+00:00)");

    private WireValues() { }

    public static UUID uuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("UUID 必须使用小写标准形式");
        return parsed;
    }

    public static BigInteger revision(String value) {
        if (value == null || value.length() > 20 || !REVISION.matcher(value).matches()) throw new IllegalArgumentException("revision 必须使用非负十进制字符串");
        BigInteger parsed = new BigInteger(value);
        if (parsed.compareTo(UNSIGNED_MAX) > 0) throw new IllegalArgumentException("revision 超出 BIGINT UNSIGNED 范围");
        return parsed;
    }

    public static BigDecimal decimal(String value, int precision, int scale) {
        if (precision <= 0 || scale < 0 || scale > precision) throw new IllegalArgumentException("Decimal 字段精度配置无效");
        if (value == null || !DECIMAL.matcher(value).matches()) throw new IllegalArgumentException("Decimal 必须使用普通十进制字符串");
        BigDecimal parsed = new BigDecimal(value);
        int integerDigits = parsed.signum() == 0 ? 0 : Math.max(0, parsed.precision() - parsed.scale());
        if (integerDigits > precision - scale || parsed.scale() > scale) throw new IllegalArgumentException("Decimal 超出字段精度");
        return parsed;
    }

    public static Instant utcTime(String value) {
        if (value == null || !UTC.matcher(value).matches()) throw new IllegalArgumentException("时间必须使用 UTC ISO 8601 和微秒精度");
        return Instant.parse(value);
    }

    public static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static void hash(String value) {
        if (value == null || !HASH.matcher(value).matches()) throw new IllegalArgumentException("SHA-256 必须使用小写十六进制字符串");
    }

    public static void boundedString(String value, int maxCodePoints) {
        if (maxCodePoints < 0 || value == null || value.codePointCount(0, value.length()) > maxCodePoints)
            throw new IllegalArgumentException("字符串必须符合 Schema 的类型和字符数量要求");
    }

    public static String codePointQuote(String text, int start, int end, String expectedSha256) throws NoSuchAlgorithmException {
        int size = text.codePointCount(0, text.length());
        if (start < 0 || end <= start || end > size) throw new IllegalArgumentException("选区超出原文范围");
        String quote = text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
        if (!sha256(quote.getBytes(StandardCharsets.UTF_8)).equals(expectedSha256)) throw new IllegalArgumentException("选区摘要不一致");
        return quote;
    }

    static void checkCanonicalNumbers(JsonNode node) throws IOException {
        if (node.isIntegralNumber() && node.bigIntegerValue().abs().compareTo(SAFE_INTEGER) > 0) throw new IllegalArgumentException("大整数必须使用字符串");
        if (node.isNumber() && !Double.isFinite(node.doubleValue())) throw new IllegalArgumentException("JSON 数字必须为有限数值");
        if (node.isTextual()) checkUnicode(node.textValue());
        if (node.isObject()) {
            var names = node.fieldNames();
            while (names.hasNext()) checkUnicode(names.next());
        }
        if (node.isContainerNode()) for (JsonNode child : node) checkCanonicalNumbers(child);
    }

    private static void checkUnicode(String text) throws IOException {
        StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
    }
}
