package com.ruoyi.medical.persistence.mapper;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/** Metadata only; no body-byte verification or authorization is implied by any row.
 * Every map carries userId from the current admission. Service enforces positive bounded limits.
 * scan: projectId,documentId,afterCreatedAt,afterDocumentRevision,limit.
 * Cursor components are both null for the first page, both non-null thereafter.
 * head: projectId,documentId. References: projectId,documentId,documentRevision,limit.
 */
@Mapper
public interface DocumentRevisionDirectoryMapper {
 record Row(String documentRevision,String bodySha256,String createdBy,LocalDateTime createdAt,
            String basedOnDocumentRevision,String contentSha256) {}
 record Head(String tenantId,String projectId,String documentId,String currentDocumentRevision,BigInteger revision) {}
 List<Head> head(Map<String,Object> values);
 List<Row> scan(Map<String,Object> values);
 List<String> checks(Map<String,Object> values);
 List<String> reviews(Map<String,Object> values);
 List<String> signatures(Map<String,Object> values);
}
