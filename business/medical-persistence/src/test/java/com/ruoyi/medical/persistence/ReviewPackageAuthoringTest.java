package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewPackageAuthoringTest {
 @Test void serverSelectedProfilesBindDistinctNormalAuthoringRoutes()throws Exception{
  var base=new TaskProfessionalPackageAuthoringSchema(new PermissionSchema());
  var schemas=java.util.List.of(base.sectionWriter(),base.sourceVerifier(),base.documentReview());
  var prefixes=java.util.List.of("task-section-writer","task-source-verifier","document-review");
  for(int i=0;i<schemas.size();i++){
   var schema=schemas.get(i);String prefix=prefixes.get(i);
   assertEquals("urn:medical:"+prefix+"-package:1.0.0#/$defs/PromptPackage",schema.ref("PROMPT"));
   assertEquals("/api/projects/project/"+prefix+"-packages",schema.profile().route("project"));
   var proof=new JsonMapper().createObjectNode();schema.bindProfile(proof,"project");schema.requireProfile(proof,"project");
   var other=schemas.get((i+1)%schemas.size());assertThrows(TenantAccessException.class,()->other.requireProfile(proof,"project"));
  }
 }
}
