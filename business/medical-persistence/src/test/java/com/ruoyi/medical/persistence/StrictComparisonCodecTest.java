package com.ruoyi.medical.persistence;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrictComparisonCodecTest {
    private final StrictComparisonCodec codec=new StrictComparisonCodec(10000);
    private StrictComparisonProtocol.Span span(String input)throws Exception{return codec.bind(input.getBytes(StandardCharsets.UTF_8),StrictComparisonProtocol.Span.class);}
    @Test void missingPrimitiveUnknownFieldDuplicateAndCoercionReject(){for(String json:new String[]{"{\"end_codepoint\":2}","{\"start_codepoint\":0,\"end_codepoint\":2,\"approved\":true}","{\"start_codepoint\":0,\"start_codepoint\":1,\"end_codepoint\":2}","{\"start_codepoint\":\"0\",\"end_codepoint\":2}","{\"start_codepoint\":0.5,\"end_codepoint\":2}","{\"start_codepoint\":null,\"end_codepoint\":2}","{\"start_codepoint\":0,\"end_codepoint\":2}{}"})assertThrows(Exception.class,()->span(json),json);}
    @Test void validSpanPreservesZero(){assertEquals(new StrictComparisonProtocol.Span(0,2),assertDoesNotThrow(()->span("{\"start_codepoint\":0,\"end_codepoint\":2}")));}
    @Test void implementationIdentityIsActualClassBoundAndUuid()throws Exception {var first=StrictDecimalEvidenceService.implementation();var next=StrictDecimalEvidenceService.implementation();assertEquals(first,next);assertDoesNotThrow(()->com.ruoyi.medical.contracts.WireValues.uuid(first.revision()));assertNotEquals(first,StrictComparisonPolicyService.implementation());}
}
