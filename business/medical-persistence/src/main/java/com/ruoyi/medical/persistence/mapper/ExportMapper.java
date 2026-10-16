package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ExportProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExportMapper {
    List<ExportProtocol.DocumentRow> document(Map<String,Object> values);
    List<String> reviewSnapshot(Map<String,Object> values);
    List<ExportProtocol.SignatureRow> signature(Map<String,Object> values);
    List<ExportProtocol.VersionRow> version(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    List<ExportProtocol.ManifestRow> manifest(Map<String,Object> values);
    List<ExportProtocol.ManifestRow> history(Map<String,Object> values);
    List<Long> historyCount(Map<String,Object> values);
    List<ExportProtocol.ItemRow> items(Map<String,Object> values);
    List<ExportProtocol.ItemRow> itemForUpdate(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveManifest(Map<String,Object> values);
    int saveItem(Map<String,Object> values);
    int updateManifestForRetry(Map<String,Object> values);
    int claimItem(Map<String,Object> values);
    int markItemVerifying(Map<String,Object> values);
    int completeItem(Map<String,Object> values);
    int failItem(Map<String,Object> values);
    int updateManifestStatus(Map<String,Object> values);
    int saveArtifactVersion(Map<String,Object> values);
    int saveArtifact(Map<String,Object> values);
    int saveBinding(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
}
