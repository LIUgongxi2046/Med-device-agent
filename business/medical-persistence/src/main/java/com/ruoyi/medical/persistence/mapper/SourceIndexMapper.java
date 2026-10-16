package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceUploadProtocol;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceIndexMapper {
    List<String> command(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SourceIndexProtocol.Build> build(Map<String,Object> values);
    List<SourceUploadProtocol.CompletionDelivery> delivery(Map<String,Object> values);
    int initializeDelivery(Map<String,Object> values);
    int claim(Map<String,Object> values);
    int renew(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
    int saveIndex(Map<String,Object> values);
    int saveBuild(Map<String,Object> values);
    int saveItem(Map<String,Object> values);
    int readyIndex(Map<String,Object> values);
    int completeBuild(Map<String,Object> values);
    int completeDelivery(Map<String,Object> values);
    int failIndex(Map<String,Object> values);
    int failDelivery(Map<String,Object> values);
    int saveReceipt(Map<String,Object> values);
    List<String> receipt(Map<String,Object> values);
    BigInteger rawChunkCount(Map<String,Object> values);
    BigInteger itemCount(Map<String,Object> values);
    List<Map<String,Object>> chunks(Map<String,Object> values);
    List<Map<String,Object>> items(Map<String,Object> values);
    List<Map<String,Object>> index(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SourceIndexProtocol.View> views(Map<String,Object> values);
}
