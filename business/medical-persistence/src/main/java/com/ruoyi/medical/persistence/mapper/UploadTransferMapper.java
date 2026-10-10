package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.UploadTransferProtocol;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadTransferMapper {
    List<UploadTransferProtocol.DocumentRow> document(Map<String, Object> values);
    LocalDateTime databaseTime(Map<String, Object> values);
    java.math.BigInteger storedSize(Map<String, Object> values);
    List<UploadTransferProtocol.SessionRow> session(Map<String, Object> values);
    List<UploadTransferProtocol.PartRow> part(Map<String, Object> values);
    List<UploadTransferProtocol.PartRow> parts(Map<String, Object> values);
    List<UploadTransferProtocol.BindingRow> binding(Map<String, Object> values);
    List<UploadTransferProtocol.GrantRow> grant(Map<String, Object> values);
    List<UploadTransferProtocol.SourceRow> source(Map<String, Object> values);
    int saveSession(Map<String, Object> values);
    int savePart(Map<String, Object> values);
    int saveBinding(Map<String, Object> values);
    int saveGrant(Map<String, Object> values);
    int beginPart(Map<String, Object> values);
    int storePart(Map<String, Object> values);
    int failPart(Map<String, Object> values);
    int receive(Map<String, Object> values);
    int beginCompletion(Map<String, Object> values);
    int complete(Map<String, Object> values);
    int cancel(Map<String, Object> values);
    int expire(Map<String, Object> values);
}
