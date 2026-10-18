package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.AnnotationReplyProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnotationReplyMapper {
    List<AnnotationReplyProtocol.Parent> parent(Map<String,Object> values);
    String originalDocumentRevision(Map<String,Object> values);
    String authorizationClock(Map<String,Object> values);
    Long replyCount(Map<String,Object> values);
    List<AnnotationReplyProtocol.Row> replies(Map<String,Object> values);
    List<AnnotationReplyProtocol.Row> reply(Map<String,Object> values);
    int insertReply(Map<String,Object> values);
    int advanceParent(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
