package com.ruoyi.medical.persistence;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskProfessionalProfilePathTest {
 @Test void rejectsRawUnsafeComponentsBeforeAnyNormalization(){
  for(String value:List.of("profiles","/profiles/x","profiles//x","profiles/./x","profiles/../x","profiles/x/","profiles/a\\b","profiles/%2e","profiles/a\u0000","profiles/a\u007f","profiles/a\u0085"))assertThrows(TenantAccessException.class,()->TaskProfessionalProfilePath.require(value),value);
 }
 @Test void preservesEveryOtherUnicodeSpellingAndNonDotComponent(){
  for(String value:List.of("profiles/文档/é.json","profiles/文档/e\u0301.json","profiles/a..b/😺"))assertSame(value,TaskProfessionalProfilePath.require(value));
 }
 @Test void compilerConsumesTheSameExactComponentPolicy(){
  var fixture=new TaskProfessionalPromptCompilerTest();var profile=(com.fasterxml.jackson.databind.node.ObjectNode)fixture.profile();var file=(com.fasterxml.jackson.databind.node.ObjectNode)profile.required("files").get(0);
  file.put("relative","profiles/a..b/文档.json");assertDoesNotThrow(()->TaskProfessionalPromptCompiler.validateProfile(profile));
  for(String invalid:List.of("profiles/a//b","profiles/a/./b","profiles/%2e/b")){file.put("relative",invalid);assertThrows(TenantAccessException.class,()->TaskProfessionalPromptCompiler.validateProfile(profile));}
 }
}
