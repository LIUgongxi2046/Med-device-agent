package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceUploadProtocol;
import com.ruoyi.medical.persistence.UploadOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceUploadMapper {
    List<UploadOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int saveUpload(Map<String,Object> values);
    int saveDeclaration(Map<String,Object> values);
    int receive(Map<String,Object> values);
    List<UploadOperationRow> completionOperation(Map<String,Object> values);
    List<UploadOperationRow> completionState(Map<String,Object> values);
    List<UploadOperationRow> completionOperationById(Map<String,Object> values);
    List<String> completionResult(Map<String,Object> values);
    List<String> completionFailure(Map<String,Object> values);
    int startCompletionOperation(Map<String,Object> values);
    List<String> completionCommand(Map<String,Object> values);
    int saveCompletionDelivery(Map<String,Object> values);
    List<SourceUploadProtocol.CompletionDelivery> completionDelivery(Map<String,Object> values);
    int claimCompletionDelivery(Map<String,Object> values);
    int renewCompletionDelivery(Map<String,Object> values);
    List<String> completionAudit(Map<String,Object> values);
    List<UploadOperationRow> partOperation(Map<String,Object> values);
    int startPartOperation(Map<String,Object> values);
    int completePartOperation(Map<String,Object> values);
    List<String> partAudit(Map<String,Object> values);
    List<String> partEvent(Map<String,Object> values);
    List<String> documentPartAudit(Map<String,Object> values);
    List<String> documentPartEvent(Map<String,Object> values);
    List<SourceUploadProtocol.Declaration> declaration(Map<String,Object> values);
    List<SourceUploadProtocol.Owner> sourceOwner(Map<String,Object> values);
    List<String> conversation(Map<String,Object> values);
    List<String> sourceSession(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
