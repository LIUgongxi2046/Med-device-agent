package com.ruoyi.medical.persistence;
import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FormalVerificationTimestampTest {
 final LocalDateTime now=LocalDateTime.of(2026,10,18,21,0,0,123456000);
 @Test void actualJobEventEncoderKeepsOriginalCreationTimeAndAllJobFields()throws Exception {
  var schema=new FormalInvocationSchema();var jobs=new FormalVerificationJobs(mock(javax.sql.DataSource.class),new PermissionSchema(),schema,new RunTokenAuthoritySchema());
  var job=new FormalVerificationJobs.Job(id(1),id(2),id(3),id(4),id(5),"original-key-0001",id(6),id(7),id(8),id(9),id(10),id(11),id(12),id(13),id(14),"{}","a".repeat(64),"b".repeat(64),"{}","c".repeat(64),schema.manifestSha256(),now,"INTERNAL");
  var event=jobs.evidence(job);assertEquals("MODEL_VERIFICATION_JOB_STARTED",event.path("kind").asText());assertEquals(now.toString(),event.path("job").path("created").asText());assertEquals(23,event.path("job").size());assertEquals(event,schema.parse(schema.canonical(event)));assertEquals(id(5),event.path("job").path("actor").asText());
 }
 @Test void actualSourceWitnessEncoderKeepsGrantAndRightsValidityDates()throws Exception {
  String h="a".repeat(64);var source=new SourceReadProtocol.SourceRow(id(1),id(2),id(3),id(4),"PROJECT","INTERNAL",id(5),h,h,id(4));
  var rights=new SourceReadProtocol.RightsRow(id(1),id(5),id(3),"MODEL_VERIFICATION","[\"READ\"]","[]","[]",now,now.plusDays(1),"APPROVED","{}",h,"1.0.0",h,"INTERNAL",h,id(4));
  var grant=new PermissionProtocol.GrantRow(id(6),id(7),id(4),"SOURCE",id(2),"source.read","{}","ACTIVE",now.minusDays(1),now.plusDays(2),id(8),"1.0.0",h,BigInteger.ONE);
  var actual=FormalVerificationJobs.sourceAuthorizations(Map.of(id(3),new SourceReadProtocol.VerifiedSource(source,rights,null,null,grant,null,null)));
  assertEquals(now.toString(),actual.path(id(3)).path("rights").path("valid_from").asText());assertEquals(now.plusDays(2).toString(),actual.path(id(3)).path("source_grant").path("valid_to").asText());assertEquals(new PermissionSchema().canonical(actual),new PermissionSchema().canonical(new FormalInvocationSchema().parse(new PermissionSchema().canonical(actual))));
 }
 static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
}
