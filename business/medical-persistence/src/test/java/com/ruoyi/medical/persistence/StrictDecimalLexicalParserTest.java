package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrictDecimalLexicalParserTest {
    private final StrictDecimalLexicalParser parser=new StrictDecimalLexicalParser(1000);
    private static String hash(String raw){return assertDoesNotThrow(()->WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8)));}
    private StrictDecimalLexicalParser.Parsed parse(String raw){return parser.parse(raw,hash(raw),0,raw.codePointCount(0,raw.length()));}
    @Test void exactCodepointsRawLexemeAndTrailingZeroScaleRemain(){String raw="😀 dose -0.1200 mg";var result=parser.parse(raw,hash(raw),7,14);assertEquals("-0.1200",result.rawText());assertEquals("-0.1200",result.decimalValue());assertEquals(4,result.scale());assertEquals(hash("-0.1200"),result.rawTextSha256());assertEquals(hash(raw),result.chunkSha256());assertEquals("PLAIN_DECIMAL_SCALE_V1",result.contract());}
    @Test void zeroIsNotMissingAndSignedZeroIsNotNormalized(){assertEquals("0",parse("0").rawText());assertEquals("-0.00",parse("-0.00").decimalValue());assertEquals(2,parse("-0.00").scale());}
    @Test void noLocaleExponentWhitespaceUnitRoundingOrUnicodeGuess(){for(String raw:List.of("1e3","+1","01","1.",".1","1,000","1,2"," 1","1 ","1mg","NaN","Infinity","１","−1","1\n"))assertThrows(IllegalArgumentException.class,()->parse(raw),raw);}
    @Test void databaseDecimalRangeRejectsInsteadOfRounding(){parse("9".repeat(26)+"."+"9".repeat(12));assertThrows(IllegalArgumentException.class,()->parse("1".repeat(27)));assertThrows(IllegalArgumentException.class,()->parse("0."+"1".repeat(13)));}
    @Test void digestAndIntervalsAreNotCallerAttestation(){assertThrows(IllegalArgumentException.class,()->parser.parse("42","b".repeat(64),0,2));assertThrows(IllegalArgumentException.class,()->parser.parse("42",hash("42"),0,3));assertThrows(IllegalArgumentException.class,()->parser.parse("42",hash("42"),1,1));assertThrows(IllegalArgumentException.class,()->parser.parse("42",hash("42"),-1,1));}
    @Test void boundedTraversalAndInvalidSurrogatesReject(){assertThrows(IllegalArgumentException.class,()->new StrictDecimalLexicalParser(1).parse("12",hash("12"),0,2));assertThrows(IllegalArgumentException.class,()->parser.parse("\ud8001",hash("\ud8001"),1,2));assertThrows(IllegalArgumentException.class,()->parser.parse("\udc001",hash("\udc001"),1,2));}
}
