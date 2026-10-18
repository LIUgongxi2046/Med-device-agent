package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.*;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface GruMapper {
 List<GruProtocol.Head> lockHead(Map<String,Object> values);
 List<SelectionOperationRow> recoveryOperation(Map<String,Object> values);
 List<GruProtocol.Counts> messageCounts(Map<String,Object> values);
 List<String> version(Map<String,Object> values);
 List<String> parents(Map<String,Object> values);
 List<String> listed(Map<String,Object> values);
 List<GruProtocol.Head> head(Map<String,Object> values);
 List<GruProtocol.Context> context(Map<String,Object> values);
 List<GruProtocol.Message> messages(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
 int insertConversation(Map<String,Object> values);
 int insertContext(Map<String,Object> values);
 int insertMessage(Map<String,Object> values);
 int insertMessageRecord(Map<String,Object> values);
 int insertVersion(Map<String,Object> values);
 int insertLineage(Map<String,Object> values);
 int advanceConversation(Map<String,Object> values);
 int saveOperation(Map<String,Object> values);
}
