package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DocumentReadProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentReadMapper {
    List<DocumentReadProtocol.Header> header(Map<String,Object> values);
    List<DocumentReadProtocol.Projection> projections(Map<String,Object> values);
    List<DocumentReadProtocol.Watermarks> watermarks(Map<String,Object> values);
    List<String> checks(Map<String,Object> values);
    List<String> reviews(Map<String,Object> values);
    List<String> signatures(Map<String,Object> values);
    List<String> invalidationReasons(Map<String,Object> values);
    List<DocumentReadProtocol.StreamRow> streams(Map<String,Object> values);
    Long sourceCount(Map<String,Object> values);
}
