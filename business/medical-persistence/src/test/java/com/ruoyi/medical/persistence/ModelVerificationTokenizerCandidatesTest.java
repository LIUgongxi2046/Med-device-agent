package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.time.Duration;import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.VerificationInputFixture.*;
class ModelVerificationTokenizerCandidatesTest {
 @TempDir Path temp;VerificationInputFixture f;FormalTokenizerRunner runner;FormalInvocationSourceOriginals reader;ObjectProvider<FormalTokenizerRunner> runners;ObjectProvider<FormalInvocationSourceOriginals> readers;ModelVerificationTokenizerCandidates service;Path executable;
 @BeforeEach @SuppressWarnings("unchecked") void before()throws Exception{
  f=new VerificationInputFixture();executable=temp.resolve("configured-tokenizer");Files.writeString(executable,"This executable is never executed by candidate discovery.\n");Files.setPosixFilePermissions(executable,PosixFilePermissions.fromString("r-x------"));
  f.source(SOURCE,"MODEL_CONFIGURATION");f.bytes(Files.readAllBytes(executable));
  for(int i=0;i<f.grants.size();i++){var g=f.grants.get(i);String condition=g.condition().replace("\"DOCUMENT_BODY\"","\"DOCUMENT_BODY\",\"MODEL_CONFIGURATION\"");f.grants.set(i,new PermissionProtocol.GrantRow(g.grantId(),g.subjectId(),g.projectId(),g.resourceKind(),g.resourceId(),g.action(),condition,g.status(),g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),g.revision()));}
  runner=new FormalTokenizerRunner(new FormalTokenizerRunner.Configuration(true,executable,f.original.sha256(),SOURCE,2097152,1024,Duration.ofSeconds(1)));
  var data=mock(DataSource.class);var connection=mock(java.sql.Connection.class);var statement=mock(java.sql.PreparedStatement.class);when(data.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenAnswer(i->{var rs=mock(java.sql.ResultSet.class);when(rs.next()).thenReturn(true,false);when(rs.getString(1)).thenReturn(f.original.objectKey());when(rs.getString(2)).thenReturn(f.original.objectVersion());when(rs.getString(3)).thenReturn(f.original.sha256());when(rs.getString(4)).thenReturn(f.original.sizeBytes().toString());when(rs.getString(5)).thenReturn(f.original.mediaType());return rs;});
  reader=new FormalInvocationSourceOriginals(f.access,f.sourceAuthority,f.sources,f.versionAuthority,f.lineage,f.transport,data,"OFFLINE_TEST","LOCAL",2097152,ModelVerificationInputService.LIMITS);
  runners=mock(ObjectProvider.class);readers=mock(ObjectProvider.class);when(runners.getIfAvailable()).thenReturn(runner);when(readers.getIfAvailable()).thenReturn(reader);
  service=new ModelVerificationTokenizerCandidates(f.access,f.models,f.resources,runners,readers,f.json,new ModelVerificationTokenizerSchema());
 }
 @AfterEach void after()throws Exception{f.close();}
 @Test void actualConfiguredHashAndRealSourceAuthorityYieldSingleHonestBasis()throws Exception{var value=service.read(f.subject,"1",MODEL);assertEquals(SOURCE,value.at("/tokenizer_basis/version_id").asText());assertEquals(f.original.sha256(),value.at("/tokenizer_basis/content_sha256").asText());assertTrue(value.path("missing_input_reasons").isEmpty());assertFalse(value.toString().contains(temp.toString()));assertEquals(0,f.requests.get());assertTrue(f.writes.isEmpty());verifyNoInteractions(f.transport);}
 @Test void noRuntimeReportsNullBasisRatherThanRandomSource(){when(runners.getIfAvailable()).thenReturn(null);var value=service.read(f.subject,"1",MODEL);assertTrue(value.path("tokenizer_basis").isNull());assertEquals("FORMAL_RUNTIME_UNAVAILABLE",value.path("missing_input_reasons").get(0).asText());}
 @Test void disabledRunnerIsUnavailable(){when(runners.getIfAvailable()).thenReturn(new FormalTokenizerRunner(new FormalTokenizerRunner.Configuration(false,null,null,null,0,0,null)));assertEquals("TOKENIZER_EXECUTABLE_UNAVAILABLE",service.read(f.subject,"1",MODEL).path("missing_input_reasons").get(0).asText());}
 @Test void changedPhysicalExecutableCannotSupplyCandidate()throws Exception{Files.setPosixFilePermissions(executable,PosixFilePermissions.fromString("rwx------"));Files.writeString(executable,"changed actual bytes");assertTrue(service.read(f.subject,"1",MODEL).path("tokenizer_basis").isNull());}
 @Test void unsafeWritableExecutableCannotSupplyCandidate()throws Exception{Files.setPosixFilePermissions(executable,PosixFilePermissions.fromString("rwxrwxrwx"));assertTrue(service.read(f.subject,"1",MODEL).path("tokenizer_basis").isNull());}
 @Test void sourcePurposeCannotBeBorrowedFromDocumentBody()throws Exception{f.source(SOURCE,"DOCUMENT_BODY");f.bytes(Files.readAllBytes(executable));assertEquals("TOKENIZER_SOURCE_UNAVAILABLE",service.read(f.subject,"1",MODEL).path("missing_input_reasons").get(0).asText());}
 @Test void sourceReadRevocationCannotExposeDeploymentBasis(){f.grants.removeIf(g->g.action().equals("source.read"));assertTrue(service.read(f.subject,"1",MODEL).path("tokenizer_basis").isNull());}
 @Test void noModelVerifyDeniesEvenWithReadableSource(){f.grants.removeIf(g->g.action().equals("model.verify"));assertThrows(Exception.class,()->service.read(f.subject,"1",MODEL));}
 @Test void staleContextCannotReturnCandidate(){assertThrows(Exception.class,()->service.read(f.subject,"2",MODEL));}
 @Test void schemaDisallowsPathsSecretsAndFakeReadyWithNullBasis()throws Exception{var schema=new ModelVerificationTokenizerSchema();var value=(com.fasterxml.jackson.databind.node.ObjectNode)service.read(f.subject,"1",MODEL);value.put("executable",executable.toString());assertThrows(Exception.class,()->schema.validate(value));value.remove("executable");value.putNull("tokenizer_basis");assertThrows(Exception.class,()->schema.validate(value));}
}
