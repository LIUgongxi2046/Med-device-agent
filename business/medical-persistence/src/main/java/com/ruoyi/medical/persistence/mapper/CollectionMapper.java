package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.CollectionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollectionMapper {
    List<CollectionProtocol.Head> head(Map<String,Object> values);
    List<CollectionProtocol.Version> version(Map<String,Object> values);
    List<CollectionProtocol.Version> versions(Map<String,Object> values);
    List<CollectionProtocol.Version> selectionVersions(Map<String,Object> values);
    List<CollectionProtocol.Parse> parse(Map<String,Object> values);
    List<CollectionProtocol.StoredMember> members(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
    int insertRegisteredVersion(Map<String,Object> values);
    int insertHead(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertMember(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
}
