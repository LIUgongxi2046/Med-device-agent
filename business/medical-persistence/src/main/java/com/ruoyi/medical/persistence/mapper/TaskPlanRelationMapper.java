package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.TaskPlanProjection;
import com.ruoyi.medical.persistence.TemplateProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskPlanRelationMapper {
    List<TaskPlanProjection.Basis> basis(Map<String,Object> values);
    int insertSection(Map<String,Object> values);
    int insertSectionNode(Map<String,Object> values);
    int insertSectionRule(Map<String,Object> values);
    int insertSectionDependency(Map<String,Object> values);
    int insertQuestionIdentity(Map<String,Object> values);
    int insertQuestion(Map<String,Object> values);
    int insertQuestionNode(Map<String,Object> values);
    int insertQuestionRule(Map<String,Object> values);
    int insertQuestionDependency(Map<String,Object> values);
    List<TaskPlanProjection.QuestionIdentity> identities(Map<String,Object> values);
    List<TemplateProtocol.Projection> sections(Map<String,Object> values);
    List<TemplateProtocol.Projection> sectionNodes(Map<String,Object> values);
    List<TemplateProtocol.Projection> sectionRules(Map<String,Object> values);
    List<TemplateProtocol.Projection> sectionDependencies(Map<String,Object> values);
    List<TemplateProtocol.Projection> questions(Map<String,Object> values);
    List<TemplateProtocol.Projection> questionNodes(Map<String,Object> values);
    List<TemplateProtocol.Projection> questionRules(Map<String,Object> values);
    List<TemplateProtocol.Projection> questionDependencies(Map<String,Object> values);
}
