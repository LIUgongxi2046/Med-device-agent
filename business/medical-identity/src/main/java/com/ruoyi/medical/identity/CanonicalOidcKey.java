package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;

final class CanonicalOidcKey {
    private CanonicalOidcKey() { }

    static String hash(String... values) throws IOException {
        byte[] encoded = JsonMapper.builder().build().writeValueAsBytes(List.of(values));
        return DigestUtils.sha256Hex(new JsonCanonicalizer(encoded).getEncodedUTF8());
    }
}
