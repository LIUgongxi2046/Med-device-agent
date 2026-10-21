package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
/** Deferred only: synthetic fixtures confer no source rights or provider acceptance. */
class MultimodalQaSchemaTest {
 private static final String TENANT="11111111-1111-4111-8111-111111111111",PROJECT="22222222-2222-4222-8222-222222222222",SOURCE="33333333-3333-4333-8333-333333333333",VERSION="44444444-4444-4444-8444-444444444444",PARSE="55555555-5555-4555-8555-555555555555",SECTION="66666666-6666-4666-8666-666666666666";
 private final MultimodalQaSchema schema=new MultimodalQaSchema();
 private ObjectNode selection(boolean image)throws Exception{String literal="Dose 2 mg";var locator=image?new DatasetProtocol.ImageLocator("1.0.0","IMAGE",0,"a".repeat(64),"ORIGINAL_TOP_LEFT_NORMALIZED",640,480,0,0,1,1):new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,literal.length(),1,1);
  var evidence=new DatasetProtocol.EvidenceRef(image?"2.0.0":"1.0.0",TENANT,PROJECT,SOURCE,VERSION,PARSE,"chunk-1",image?DatasetProtocol.LocatorKind.IMAGE:DatasetProtocol.LocatorKind.TXT,locator,WireValues.sha256(literal.getBytes(java.nio.charset.StandardCharsets.UTF_8)),PARSE);
  return (ObjectNode)schema.tree(new MultimodalQaSchema.Selection("1.0.0","MULTIMODAL_QA","PARSED_EVIDENCE_QA","ABSTAIN_ON_MISSING_OR_CONFLICTING_EVIDENCE",new PermissionProtocol.Basis(SECTION,"b".repeat(64)),List.of(new MultimodalQaSchema.Input("source",new PermissionProtocol.Basis(VERSION,"c".repeat(64)),PARSE,evidence,literal,image?"IMAGE_OCR_TEXT":"PARSED_TEXT")),List.of(new MultimodalQaSchema.Question("dose",SECTION,"What dose is stated?",List.of("source")))));
 }
 @Test void exactTextEvidenceRoundTrips()throws Exception{var n=selection(false);assertEquals(schema.read(n),schema.definition(schema.definition(n)));}
 @Test void imageIsExplicitlyOcrNotVision()throws Exception{var n=selection(true);assertEquals("IMAGE_OCR_TEXT",schema.read(n).inputs().get(0).representation());var fixed=schema.tree(Map.of("selection",n));assertFalse(MultimodalQaPipeline.plan(fixed).required("native_visual_understanding").asBoolean());}
 @Test void nativeVisionCannotUseTextCapability()throws Exception{var n=selection(true);n.put("capability","VISUAL_INTERPRETATION");assertThrows(Exception.class,()->schema.read(n));}
 @Test void alteredQuoteCannotBeClaimedAsLiteral()throws Exception{var n=selection(false);((ObjectNode)n.required("inputs").get(0)).put("literal","Dose 20 mg");assertThrows(Exception.class,()->schema.read(n));}
 @Test void unknownQuestionInputFails()throws Exception{var n=selection(false);((ObjectNode)n.required("questions").get(0)).putArray("input_keys").add("unregistered");assertThrows(Exception.class,()->schema.read(n));}
 @Test void imageCannotMasqueradeAsGenericText()throws Exception{var n=selection(true);((ObjectNode)n.required("inputs").get(0)).put("representation","PARSED_TEXT");assertThrows(Exception.class,()->schema.read(n));}
 @Test void unknownFieldsAreRejected()throws Exception{var n=selection(false);n.put("model_receipt","invented");assertThrows(Exception.class,()->schema.read(n));}
}
