package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DocumentReadProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentReadMapper {
    List<DocumentReadProtocol.ArtifactOwner> artifactOwner(Map<String,Object> values);
    List<DocumentReadProtocol.Header> header(Map<String,Object> values);
    List<DocumentReadProtocol.Projection> projections(Map<String,Object> values);
    List<DocumentReadProtocol.Watermarks> watermarks(Map<String,Object> values);
    List<String> checks(Map<String,Object> values);
    List<String> reviews(Map<String,Object> values);
    List<String> signatures(Map<String,Object> values);
    List<String> invalidationReasons(Map<String,Object> values);
    List<DocumentReadProtocol.StreamRow> streams(Map<String,Object> values);
    Long sourceCount(Map<String,Object> values);
    List<DocumentReadProtocol.Header> metadataHeader(Map<String,Object> values);
    List<DocumentReadProtocol.Projection> metadataProjections(Map<String,Object> values);
    List<DocumentReadProtocol.Watermarks> metadataWatermarks(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TaskProtocol.Snapshot> metadataSnapshot(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.DocumentUploadBasisProtocol.MemberRow> metadataMembers(Map<String,Object> values);
    List<java.math.BigInteger> metadataSnapshotBytes(Map<String,Object> values);
    Long metadataSourceCount(Map<String,Object> values);
}
