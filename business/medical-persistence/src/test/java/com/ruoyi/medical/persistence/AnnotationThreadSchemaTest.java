package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationThreadSchemaTest {
    private final PermissionSchema json=new PermissionSchema();
    private final AnnotationThreadSchema schema=new AnnotationThreadSchema(json);
    AnnotationThreadSchemaTest() throws Exception { }
    private ObjectNode selection(){var selected=json.object().put("document_revision",AnnotationThreadServiceTest.REVISION);var segments=selected.putArray("segments");
        segments.add(json.object().put("kind","TEXT").put("node_id",AnnotationThreadServiceTest.TEXT).put("node_sha256","a".repeat(64)).put("start_cp",0).put("end_cp",1).put("quote_sha256","b".repeat(64)));return selected;}
    private ObjectNode input(){var value=json.object().put("document_revision",AnnotationThreadServiceTest.REVISION).put("expected_revision","1").put("content"," 原文😀\n\t ");value.set("selection",selection());return value;}
    private byte[] bytes(Object value)throws Exception{return json.canonicalObject(value).getBytes(StandardCharsets.UTF_8);}
    @Test void usesClosedSharedSelectionAndPreservesExactUserText()throws Exception{
        var value=input();assertEquals(value.required("content").asText(),schema.create(bytes(value)).content());
        for(String name:List.of("actor_id","tenant_id","status","annotation_id","selection_artifact_id","created_at"))assertThrows(SchemaRejection.class,()->schema.create(bytes(input().put(name,"injected"))));
        var malformed=input();((ObjectNode)malformed.required("selection")).put("text","invented");assertThrows(SchemaRejection.class,()->schema.create(bytes(malformed)));
        var empty=input();((ObjectNode)empty.required("selection")).putArray("segments");assertThrows(SchemaRejection.class,()->schema.create(bytes(empty)));
    }
    @Test void rejectsDuplicateTrailingMalformedUnicodeAndUnincrementableRevision()throws Exception{
        String value=json.canonical(input());assertThrows(Exception.class,()->schema.create((value+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->schema.create(value.replace("\"content\":","\"content\":\"first\",\"content\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.nio.charset.CharacterCodingException.class,()->schema.create(new byte[]{(byte)0xc3,(byte)0x28}));
        for(String revision:List.of("0","01","-1","18446744073709551615","18446744073709551616"))assertThrows(SchemaRejection.class,()->schema.create(bytes(input().put("expected_revision",revision))));
        for(String text:List.of(""," \n\t","\u0000"))assertThrows(Exception.class,()->schema.create(bytes(input().put("content",text))));
        String escaped=json.canonical(input().put("content","PLACEHOLDER")).replace("PLACEHOLDER","\\ud800");
        assertThrows(SchemaRejection.class,()->schema.create(escaped.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void explicitRequestAndTextBoundsNeverTruncate()throws Exception{
        assertEquals("😀".repeat(16000),schema.create(bytes(input().put("content","😀".repeat(16000)))).content());
        assertThrows(SchemaRejection.class,()->schema.create(bytes(input().put("content","x".repeat(16001)))));
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->schema.create(new byte[1_048_577])).code());
    }
    @Test void oneHundredMaximumDetailItemsStillFitAfterMutableFieldsGrow()throws Exception{
        String maximum="18446744073709551615";var selected=selection();
        var item=json.object().put("annotation_id",AnnotationThreadServiceTest.id(40)).put("document_revision",AnnotationThreadServiceTest.REVISION)
                .put("revision",maximum).put("selection_artifact_id",AnnotationThreadServiceTest.id(41)).put("selection_sha256",schema.hash(selected))
                .put("content","").put("actor_id",AnnotationThreadServiceTest.ACTOR).put("created_at","9999-12-31T23:59:59.999999Z")
                .put("status","REANCHOR_REQUIRED").put("location_status","ORIGINAL_VERSION").put("reply_count",maximum);item.set("selection",selected);
        // Combining escaped controls (6 bytes) with ordinary text reaches the exact 65,536-byte bound inside 16,000 code points.
        int baseline=bytes(item).length,remaining=AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES-baseline;
        String text="\t".repeat(remaining/2)+"x".repeat(remaining%2);
        // Tabs encode as two bytes, so use legal multibyte code points for a shorter content within the code-point bound.
        text="😀".repeat(remaining/4)+"x".repeat(remaining%4);item.put("content",text);
        if(text.codePointCount(0,text.length())>16000){
            int shift=text.codePointCount(0,text.length())-16000;
            // Grow Selection metadata instead, with additional valid TEXT segments, retaining semantic verification at service admission.
            var segments=(com.fasterxml.jackson.databind.node.ArrayNode)selected.required("segments");
            while(remaining/4>15900){var segment=segments.get(0).deepCopy();((ObjectNode)segment).put("node_id",AnnotationThreadServiceTest.id(100+segments.size()));segments.add(segment);remaining=AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES-bytes(item.put("content","")).length;}
            item.put("selection_sha256",schema.hash(selected));item.put("content","😀".repeat(remaining/4)+"x".repeat(remaining%4));
        }
        assertEquals(AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES,bytes(item).length);schema.annotation(item);
        var data=json.object().put("schema_version","1.0.0").put("document_id",AnnotationThreadServiceTest.DOCUMENT).put("document_revision",AnnotationThreadServiceTest.REVISION)
                .put("revision",maximum).put("issue_watermark",maximum).put("evidence_watermark",maximum).putNull("status_filter").put("total","100").put("complete",true);
        var items=data.putArray("items");for(int i=0;i<100;i++)items.add(item.deepCopy().put("annotation_id",AnnotationThreadServiceTest.id(1000+i)));
        data.put("listing_sha256",schema.hash(data));var envelope=schema.envelope("Listing",data,AnnotationThreadServiceTest.id(2000),maximum);
        assertTrue(bytes(envelope).length<AnnotationThreadSchema.MAXIMUM_LIST_BYTES);
    }
}
