package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ContentReadProtocol;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContentReadMapper {
    LocalDateTime databaseTime(Map<String, Object> values);
    List<ContentReadProtocol.HeaderRow> header(Map<String, Object> values);
    List<ContentReadProtocol.RangeRow> range(Map<String, Object> values);
    List<ContentReadProtocol.AuditRow> audit(Map<String, Object> values);
    int saveHeader(Map<String, Object> values);
    int saveRange(Map<String, Object> values);
    int saveAudit(Map<String, Object> values);
}
