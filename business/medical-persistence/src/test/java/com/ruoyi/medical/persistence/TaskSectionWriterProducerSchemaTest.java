package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Structural regression only. Tests are not evidence of live human approval or runtime writes. */
class TaskSectionWriterProducerSchemaTest {
 private final TaskNodeProducerSchema writer=new TaskNodeProducerSchema(TaskNodeProducerProfile.WRITER);
 private final TaskNodeProducerSchema research=new TaskNodeProducerSchema();
 TaskSectionWriterProducerSchemaTest()throws Exception{}
 private ObjectNode author()throws Exception{var n=new TaskNodeProducerSchemaTest().author();n.set("section_ids",n.remove("question_ids"));n.putArray("artifact_kinds").add("TASK_SECTION_WRITER_INPUT");return n;}
 @Test void writerIsClosedAndCannotAlterLegacySchema()throws Exception{assertNotEquals(writer.manifest(),research.manifest());assertNotNull(writer.validate("InputAuthor",author()));assertThrows(RuntimeException.class,()->research.validate("InputAuthor",author()));assertThrows(RuntimeException.class,()->writer.validate("InputAuthor",new TaskNodeProducerSchemaTest().author()));try(var in=getClass().getResourceAsStream("/task-node-producer.schema.json")){assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),research.manifest());}}
 @Test void questionsCannotBeAliasesForSections()throws Exception{var n=author();n.set("question_ids",n.remove("section_ids"));assertThrows(RuntimeException.class,()->writer.validate("InputAuthor",n));var duplicate=author();duplicate.withArray("section_ids").add(duplicate.required("section_ids").get(0));assertThrows(RuntimeException.class,()->writer.validate("InputAuthor",duplicate));}
 @Test void writerKindsAreExplicitAndResearchKindsCannotLeak()throws Exception{var n=author();var kinds=n.putArray("artifact_kinds");TaskNodeProducerProfile.WRITER.kinds().stream().sorted().forEach(kinds::add);assertNotNull(writer.validate("InputAuthor",n));kinds.add("STEP_RESULT");assertThrows(RuntimeException.class,()->writer.validate("InputAuthor",n));}
 @Test void predecessorReadKindsNeverBecomeWriterOutput(){for(String kind:java.util.List.of("FIELD_CHECK_RESULT","EVIDENCE_BUNDLE","DOCUMENT_BODY"))assertFalse(TaskSectionWriterProducerAuthority.writerProducedKind(kind));assertTrue(TaskSectionWriterProducerAuthority.writerProducedKind("SECTION_CANDIDATE"));}
 @Test void profilesHaveDistinctOperationAndPersistenceNamespaces(){assertNotEquals(TaskNodeProducerProfile.RESEARCH.operation(),TaskNodeProducerProfile.WRITER.operation());assertNotEquals(TaskNodeProducerProfile.RESEARCH.table(),TaskNodeProducerProfile.WRITER.table());assertEquals("N02",TaskNodeProducerProfile.RESEARCH.node());assertEquals("N04",TaskNodeProducerProfile.WRITER.node());}
 @Test void proofsCannotBeConstructedFromPublicPins(){for(var type:java.util.List.of(TaskSectionWriterProducerAuthority.Prepared.class,TaskSectionWriterProducerAuthority.Verified.class))for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void independentApprovalStillRequired(){assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireDecision("DRAFT","APPROVE","author","author"));assertDoesNotThrow(()->TaskNodeProducerService.requireDecision("DRAFT","APPROVE","author","other"));}
 @Test void writerPreparationAdvertisesExplicitReadCompanions()throws Exception{var choices=TaskNodeProducerService.preparationChoices(new PermissionSchema(),TaskNodeProducerProfile.WRITER);assertTrue(choices.required("artifact_kinds").toString().contains("SECTION_CANDIDATE"));assertTrue(choices.required("artifact_kinds").toString().contains("EVIDENCE_BUNDLE"));}
}
