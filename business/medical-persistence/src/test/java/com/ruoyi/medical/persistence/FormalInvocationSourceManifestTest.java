package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;import java.time.*;
class FormalInvocationSourceManifestTest {
 static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
 @Test void actualAuthorityRecordsUseSnakeCase()throws Exception{var source=new SourceReadProtocol.SourceRow(id(1),id(2),id(3),id(4),"PROJECT","INTERNAL",id(5),"a".repeat(64),"a".repeat(64),id(4));var rights=new SourceReadProtocol.RightsRow(id(1),id(5),id(3),"DOCUMENT_BODY","[]","[]","[]",LocalDateTime.of(2026,1,1,0,0),LocalDateTime.of(2027,1,1,0,0),"APPROVED","{}","b".repeat(64),"1.0.0","c".repeat(64),"INTERNAL","b".repeat(64),id(4));var schema=new FormalInvocationSchema();var actual=schema.parse(schema.canonical(Map.of(id(3),Map.of("source",source,"rights",rights))));assertTrue(actual.required(id(3)).required("rights").has("rights_revision"));assertEquals(Map.of(id(3),id(5)),FormalInvocationSourceManifest.rights(actual));}
 @Test void sourceMismatchCannotBorrowAnotherRight()throws Exception{var schema=new FormalInvocationSchema();var bad=schema.parse("{\""+id(3)+"\":{\"source\":{\"source_version\":\""+id(3)+"\"},\"rights\":{\"rights_revision\":\""+id(5)+"\",\"source_version\":\""+id(9)+"\"}}}");assertThrows(TenantAccessException.class,()->FormalInvocationSourceManifest.rights(bad));}
}
