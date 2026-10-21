package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Deployment fixtures and explicit process double only; never launches a provider/runtime. */
class TaskInvocationLocalBridgeTest {
 @TempDir Path root;TaskInvocationLocalBridge.Configuration config;Path packages;
 @BeforeEach void fixture()throws Exception{
  root=root.toAbsolutePath().normalize();
  packages=Files.createDirectory(root.resolve("packages"));var files=new ArrayList<Map<String,String>>();
  for(String name:List.of("medical_model_gateway","medical_contracts")){var folder=Files.createDirectory(packages.resolve(name));byte[] bytes=(name+" fixture").getBytes();Files.write(folder.resolve("__init__.py"),bytes);files.add(Map.of("path",name+"/__init__.py","sha256",WireValues.sha256(bytes)));}
  var executable=Files.writeString(root.resolve("python"),"explicit unexecuted executable fixture");Files.setPosixFilePermissions(executable,PosixFilePermissions.fromString("rwx------"));
  var entry=Files.writeString(root.resolve("entry.py"),"explicit unexecuted entry fixture");var manifest=Files.write(root.resolve("manifest.json"),ContentReadAuthorization.canonicalBytes(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("schema_version","1.0.0","files",files))));var privateFile=Files.writeString(root.resolve("config.json"),"{}");
  config=new TaskInvocationLocalBridge.Configuration(executable,hash(executable),entry,hash(entry),packages,manifest,hash(manifest),privateFile,Duration.ofSeconds(1));
 }
 @Test void exactPackageReadinessDoesNotRunProcess()throws Exception{var runner=mock(TaskInvocationLocalBridge.Runner.class);new TaskInvocationLocalBridge(config,runner).requireReady();verifyNoInteractions(runner);}
 @Test void undeclaredPackageFileDenied()throws Exception{Files.writeString(packages.resolve("extra.py"),"unlisted");assertThrows(TenantAccessException.class,()->new TaskInvocationLocalBridge(config).requireReady());}
 @Test void changedDeclaredBytesDenied()throws Exception{Files.writeString(packages.resolve("medical_contracts/__init__.py"),"changed");assertThrows(TenantAccessException.class,()->new TaskInvocationLocalBridge(config).requireReady());}
 @Test void symlinkPackageDenied()throws Exception{Files.createSymbolicLink(packages.resolve("link"),config.entrypoint());assertThrows(TenantAccessException.class,()->new TaskInvocationLocalBridge(config).requireReady());}
 @Test void timeoutAndRelativeExecutableDenied(){assertThrows(IllegalArgumentException.class,()->new TaskInvocationLocalBridge.Configuration(Path.of("python"),config.executableSha256(),config.entrypoint(),config.entrypointSha256(),packages,config.packageManifest(),config.packageManifestSha256(),config.privateConfiguration(),Duration.ofSeconds(1)));assertThrows(IllegalArgumentException.class,()->new TaskInvocationLocalBridge.Configuration(config.executable(),config.executableSha256(),config.entrypoint(),config.entrypointSha256(),packages,config.packageManifest(),config.packageManifestSha256(),config.privateConfiguration(),Duration.ofMinutes(6)));}
 @Test void factualAcknowledgementIsNotModelResult()throws Exception{var command=mock(TaskInvocationService.DispatchCall.class);when(command.consume()).thenReturn("{}".getBytes());when(command.invocationId()).thenReturn(TaskInvocationFixture.id(1));var bridge=new TaskInvocationLocalBridge(config,(c,b)->("{\"schema_version\":\"1.0.0\",\"mode\":\"DISPATCH\",\"invocation_id\":\""+command.invocationId()+"\",\"dispatch_attempt_id\":\""+TaskInvocationFixture.id(2)+"\",\"recorded\":true}").getBytes());var outcome=bridge.execute(command);assertTrue(outcome.recorded());assertEquals(TaskInvocationFixture.id(2),outcome.attemptId());verify(command).consume();}
 @Test void processFailureIsSanitizedAndNeverRetried()throws Exception{var command=mock(TaskInvocationService.DispatchCall.class);when(command.consume()).thenReturn("{}".getBytes());var runner=mock(TaskInvocationLocalBridge.Runner.class);when(runner.run(any(),any())).thenThrow(new IllegalStateException("private credential fixture must not escape"));var e=assertThrows(TaskInvocationLocalBridge.Unknown.class,()->new TaskInvocationLocalBridge(config,runner).execute(command));assertEquals("TASK_ADAPTER_OUTCOME_UNKNOWN",e.getMessage());assertNull(e.getCause());verify(runner).run(any(),any());}
 @Test void recoveryOutputMustMatchModeInvocationAndOriginalAttempt()throws Exception {
  for(String mode:List.of("RECOVER","DISPATCH"))for(String attempt:List.of(TaskInvocationFixture.id(2),TaskInvocationFixture.id(3))){
   var command=mock(TaskInvocationRecovery.RecoveryCall.class);when(command.consume()).thenReturn("{}".getBytes());when(command.invocationId()).thenReturn(TaskInvocationFixture.id(1));when(command.attemptId()).thenReturn(TaskInvocationFixture.id(2));
   var bridge=new TaskInvocationLocalBridge(config,(c,b)->("{\"schema_version\":\"1.0.0\",\"mode\":\""+mode+"\",\"invocation_id\":\""+command.invocationId()+"\",\"dispatch_attempt_id\":\""+attempt+"\",\"recorded\":true}").getBytes());
   if(mode.equals("RECOVER")&&attempt.equals(command.attemptId()))assertTrue(bridge.recover(command).recorded());else assertThrows(TaskInvocationLocalBridge.Unknown.class,()->bridge.recover(command));
  }
 }
 @Test void recoveryCannotAcceptFalseRecordedNullAttempt()throws Exception {
  var command=mock(TaskInvocationRecovery.RecoveryCall.class);when(command.consume()).thenReturn("{}".getBytes());when(command.invocationId()).thenReturn(TaskInvocationFixture.id(1));when(command.attemptId()).thenReturn(TaskInvocationFixture.id(2));
  var bridge=new TaskInvocationLocalBridge(config,(c,b)->("{\"schema_version\":\"1.0.0\",\"mode\":\"RECOVER\",\"invocation_id\":\""+command.invocationId()+"\",\"dispatch_attempt_id\":null,\"recorded\":false}").getBytes());assertThrows(TaskInvocationLocalBridge.Unknown.class,()->bridge.recover(command));
 }
 private static String hash(Path path)throws Exception{return WireValues.sha256(Files.readAllBytes(path));}
}
