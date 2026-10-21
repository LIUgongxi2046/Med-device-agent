package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
/** Complete publication copies must include every pinned extension; missing bytes fail closed. */
class PlanDraftPackageCatalogueTest {
 @TempDir Path temporary;
 @Test void topLevelOnlySchemaKeepsExactPublishedIdentity() throws Exception {
  Path root=Path.of(System.getProperty("medical.projectRoot"));
  byte[] bytes=Files.readAllBytes(root.resolve("business/medical-persistence/src/main/resources/document-export-command-v2.schema.json"));
  var document=new ObjectMapper().readTree(bytes);assertFalse(document.has("$defs"));
  var catalogue=SchemaCatalogue.fromClasspath();
  var published=catalogue.requirePublished(document.required("$id").textValue(),WireValues.sha256(bytes));
  assertEquals("document-export-command-v2",published.name());assertNull(published.definition());
 }
 @Test void completeCopyIncludesNewN01AndPreservesLegacyBytes()throws Exception{
  Path root=Path.of(System.getProperty("medical.projectRoot"));var json=new ObjectMapper();var paths=new LinkedHashSet<String>();
  var manifests=new ArrayList<String>();manifests.add("contracts/catalogue.json");
  try(var entries=Files.list(root.resolve("contracts/extensions"))){entries.filter(p->p.getFileName().toString().endsWith(".catalogue.json")).sorted().forEach(p->manifests.add(root.relativize(p).toString()));}
  for(String manifest:manifests){
   paths.add(manifest);for(var entry:json.readTree(Files.readAllBytes(root.resolve(manifest))).required("schemas"))paths.add(entry.required("path").textValue());
  }
  for(String path:paths){Path target=temporary.resolve(path);Files.createDirectories(target.getParent());Files.copy(root.resolve(path),target);}
  var copied=SchemaCatalogue.fromProject(temporary);var packaged=SchemaCatalogue.fromClasspath();String ref="urn:medical:task-plan-draft-package:1.0.0#/$defs/RolePackage";
  String digest=WireValues.sha256(Files.readAllBytes(root.resolve("business/medical-persistence/src/main/resources/task-plan-draft-package.schema.json")));
  assertEquals(packaged.requirePublished(ref,digest),copied.requirePublished(ref,digest));assertEquals("dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915",WireValues.sha256(Files.readAllBytes(temporary.resolve("contracts/catalogue.json"))));
  Files.delete(temporary.resolve(SchemaCatalogue.PLAN_DRAFT_PACKAGE_EXTENSION_PATH));assertThrows(java.io.IOException.class,()->SchemaCatalogue.fromProject(temporary));
 }
}
