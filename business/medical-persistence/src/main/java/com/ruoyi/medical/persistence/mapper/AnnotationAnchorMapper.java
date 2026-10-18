package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AnnotationAnchorMapper {
 List<AnnotationAnchorProtocol.Locator> locator(Map<String,Object> v);
 List<AnnotationAnchorProtocol.Locator> documentAnnotations(Map<String,Object> v);
 Long documentCount(Map<String,Object> v);
 List<AnnotationAnchorProtocol.Pointer> pointer(Map<String,Object> v);
 List<AnnotationAnchorProtocol.Anchor> anchor(Map<String,Object> v);
 List<AnnotationAnchorProtocol.Anchor> history(Map<String,Object> v);
 Long historyCount(Map<String,Object> v);
 int insertAnchor(Map<String,Object> v);
 int insertPointer(Map<String,Object> v);
 int advancePointer(Map<String,Object> v);
 int advanceAnnotation(Map<String,Object> v);
 int insertTransition(Map<String,Object> v);
 List<String> transitions(Map<String,Object> v);
 List<String> transition(Map<String,Object> v);
 List<UploadOperationRow> operation(Map<String,Object> v);
 int startOperation(Map<String,Object> v);
 int updateCommand(Map<String,Object> v);
 int completeOperation(Map<String,Object> v);
 int invalidateReview(Map<String,Object> v);
 List<AnnotationThreadProtocol.Invalidation> invalidations(Map<String,Object> v);
 List<String> audit(Map<String,Object> v);
 List<String> event(Map<String,Object> v);
}
