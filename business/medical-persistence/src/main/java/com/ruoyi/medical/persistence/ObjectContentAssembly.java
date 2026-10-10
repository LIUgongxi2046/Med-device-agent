package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.Encoding;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Objects;

public final class ObjectContentAssembly {
    private final ObjectRef reference;
    private final int size;
    private final String objectDigest;
    private final String selection;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean unavailable;
    private boolean complete;

    public ObjectContentAssembly(ObjectRef reference, int maximumContentBytes) throws Exception {
        this.reference = Objects.requireNonNull(reference);
        if (maximumContentBytes <= 0) throw new IllegalArgumentException("完整内容容量必须完整配置");
        BigInteger length = WireValues.revision(reference.sizeBytes());
        if (length.signum() <= 0) throw new IllegalArgumentException("内容对象必须具有实际字节");
        if (length.compareTo(BigInteger.valueOf(maximumContentBytes)) > 0)
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        size = length.intValueExact();
        objectDigest = ObjectContentPaging.objectDigest(reference);
        selection = ObjectContentPaging.rangeDigest(objectDigest, 0, size);
    }

    public void accept(ObjectContentPaging.Page page) throws Exception {
        if (unavailable || complete) mismatch();
        // 校验失败后禁止继续消费已有部分字节，调用者必须重新建立完整读取。
        unavailable = true;
        Objects.requireNonNull(page); Objects.requireNonNull(page.coverage());
        if (page.encoding() != Encoding.BASE64 || !reference.mediaType().equals(page.mediaType())
                || !reference.sha256().equals(page.fullSha256()) || !selection.equals(page.coverage().selectionSha256())) mismatch();
        BigInteger start = WireValues.revision(page.startByte()), end = WireValues.revision(page.endByte());
        if (!start.equals(BigInteger.valueOf(bytes.size())) || end.compareTo(start) <= 0
                || end.compareTo(BigInteger.valueOf(size)) > 0) mismatch();
        int length = end.subtract(start).intValueExact();
        if (page.content() == null || page.content().length() != ((long) length + 2) / 3 * 4) mismatch();
        byte[] decoded = Base64.getDecoder().decode(page.content());
        if (decoded.length != length || !Base64.getEncoder().encodeToString(decoded).equals(page.content())
                || !WireValues.sha256(decoded).equals(page.chunkSha256())
                || !ObjectContentPaging.rangeDigest(objectDigest, start.longValueExact(), end.longValueExact())
                    .equals(page.coverage().deliveredSha256())) mismatch();
        boolean finalPage = end.intValueExact() == size;
        if (page.coverage().complete() != finalPage) mismatch();
        bytes.write(decoded);
        if (finalPage && !reference.sha256().equals(WireValues.sha256(bytes.toByteArray()))) mismatch();
        complete = finalPage;
        unavailable = false;
    }

    public byte[] content() {
        if (unavailable || !complete) mismatch();
        return bytes.toByteArray();
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
