package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FormalInvocationCoverageTest {
 final InterfaceProtocol.ObjectRef object=new InterfaceProtocol.ObjectRef("tenants/11111111-1111-4111-8111-111111111111/exact-input","fixed-original-version","a".repeat(64),"128","application/json");
 ObjectNode coverage()throws Exception {String digest=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(object),0,128);return JsonNodeFactory.instance.objectNode().put("selection_digest",digest).put("delivered_digest",digest).put("complete",true).putNull("next_cursor");}
 @Test void actualPublishedCoverageIsAcceptedByConsumer()throws Exception{var value=coverage();new FormalInvocationSchema().require("Coverage",value);assertDoesNotThrow(()->FormalInvocationInputs.requireCompleteCoverage(value,object));}
 @Test void unrelatedGenericCoverageFieldNamesAreRejected()throws Exception{var good=coverage();var value=JsonNodeFactory.instance.objectNode().put("selection_sha256",good.path("selection_digest").asText()).put("delivered_sha256",good.path("delivered_digest").asText()).put("complete",true).putNull("next_cursor");assertThrows(SchemaRejection.class,()->new FormalInvocationSchema().require("Coverage",value));assertThrows(TenantAccessException.class,()->FormalInvocationInputs.requireCompleteCoverage(value,object));}
 @Test void partialForgedExtraAndWrongPhysicalOriginalAreRejected()throws Exception{var original=coverage();for(var changed:java.util.List.of(original.deepCopy().put("complete",false),original.deepCopy().put("next_cursor","cursor"),original.deepCopy().put("selection_digest","b".repeat(64)),original.deepCopy().put("delivered_digest","b".repeat(64)),original.deepCopy().put("extra",true))){assertThrows(TenantAccessException.class,()->FormalInvocationInputs.requireCompleteCoverage(changed,object));}var other=new InterfaceProtocol.ObjectRef(object.objectKey(),"different-version",object.sha256(),object.sizeBytes(),object.mediaType());assertThrows(TenantAccessException.class,()->FormalInvocationInputs.requireCompleteCoverage(original,other));}
}
