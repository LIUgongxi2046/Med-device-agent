package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AnnotationAnchorReconciliationMapper {
 List<UploadOperationRow> operation(Map<String,Object> v);
 int startOperation(Map<String,Object> v);
 int completeOperation(Map<String,Object> v);
 int invalidateReview(Map<String,Object> v);
 List<AnnotationThreadProtocol.Invalidation> invalidations(Map<String,Object> v);
 List<String> audit(Map<String,Object> v);
 List<String> event(Map<String,Object> v);
}
