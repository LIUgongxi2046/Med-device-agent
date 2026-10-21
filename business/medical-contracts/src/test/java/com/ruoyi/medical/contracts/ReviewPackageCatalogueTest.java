package com.ruoyi.medical.contracts;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** New publications are additive; original N04/N05 bytes retain their identities. */
class ReviewPackageCatalogueTest {
 @Test void allThreeReviewLanePackagesArePublishedWithExactOriginalDigests()throws Exception{
  Path root=Path.of(System.getProperty("medical.projectRoot"));var project=SchemaCatalogue.fromProject(root);var packaged=SchemaCatalogue.fromClasspath();
  for(String name:List.of("task-section-writer-package","task-source-verifier-package","document-review-package")){
   String hash=WireValues.sha256(Files.readAllBytes(root.resolve("business/medical-persistence/src/main/resources/"+name+".schema.json")));
   for(String kind:List.of("PromptPackage","ProfilePackage","RolePackage")){
    String ref="urn:medical:"+name+":1.0.0#/$defs/"+kind;
    assertEquals(project.requirePublished(ref,hash),packaged.requirePublished(ref,hash));
   }
  }
 }
}
