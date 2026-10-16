package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;

public record SourceDeletionTarget(String kind, String sourceVersion, String parseVersion, String indexVersion,
        String objectKey, String objectVersion, String contentSha256) {
    static SourceDeletionTarget read(SourceGovernanceProtocol.DeletionItemRow row, SourceGovernanceSchema schema) throws Exception {
        JsonNode value=schema.read(row.targetRef());
        String source=text(value,"source_version"); WireValues.uuid(source);
        Set<String> fields=new HashSet<>(); value.fieldNames().forEachRemaining(fields::add);
        String parse=null,index=null,key=null,version=null,hash=null,identity;
        Set<String> expected;
        switch(row.storeKind()) {
            case "ORIGINAL" -> {
                expected=Set.of("source_version","object_key","object_version","content_sha256","size_bytes","media_type");
                key=text(value,"object_key"); version=text(value,"object_version"); hash=text(value,"content_sha256");
                WireValues.hash(hash); if(WireValues.revision(text(value,"size_bytes")).signum()<=0) mismatch();
                text(value,"media_type"); identity="ORIGINAL:"+key+":"+version+":"+hash;
            }
            case "PARSED" -> {
                expected=Set.of("source_version","parse_version","artifact_key","content_sha256");
                parse=text(value,"parse_version"); WireValues.uuid(parse); key=text(value,"artifact_key");
                hash=text(value,"content_sha256"); WireValues.hash(hash); identity="PARSED:"+parse+":"+key+":"+hash;
            }
            case "FULLTEXT","VECTOR" -> {
                expected=Set.of("source_version","parse_version","index_version","manifest_sha256");
                parse=text(value,"parse_version"); index=text(value,"index_version"); hash=text(value,"manifest_sha256");
                WireValues.uuid(parse); WireValues.uuid(index); WireValues.hash(hash); identity=row.storeKind()+":"+index+":"+hash;
            }
            case "CACHE","DIAGNOSTIC" -> {
                expected=Set.of("source_version"); identity=row.storeKind()+":"+source;
            }
            default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
        if(!expected.equals(fields) || !source.equals(row.sourceVersion())
                || !DigestUtils.sha256Hex(identity).equals(row.targetSha256())) mismatch();
        return new SourceDeletionTarget(row.storeKind(),source,parse,index,key,version,hash);
    }

    static String text(JsonNode value,String field) {
        var node=value.required(field);
        if(!node.isTextual() || node.textValue().isBlank()) mismatch();
        return node.textValue();
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
