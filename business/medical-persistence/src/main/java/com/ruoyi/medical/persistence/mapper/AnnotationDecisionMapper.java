package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.AnnotationReplyProtocol;
import com.ruoyi.medical.persistence.AnnotationDecisionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnotationDecisionMapper {
    List<AnnotationReplyProtocol.Parent> parent(Map<String,Object> values);
    String authorizationClock(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.AnnotationThreadProtocol.Row> historicalAnnotation(Map<String,Object> values);
    List<AnnotationDecisionProtocol.History> history(Map<String,Object> values);
    Long replyCount(Map<String,Object> values);
    Long decisionCount(Map<String,Object> values);
    List<AnnotationDecisionProtocol.Row> decisions(Map<String,Object> values);
    List<AnnotationDecisionProtocol.Row> decision(Map<String,Object> values);
    int insertDecision(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    int advanceIssues(Map<String,Object> values);
    int invalidateReview(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.AnnotationThreadProtocol.Invalidation> invalidations(Map<String,Object> values);
    int advanceParent(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
