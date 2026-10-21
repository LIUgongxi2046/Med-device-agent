package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskProfessionalConfigurationTest {
 @TempDir Path directory;
 @Test void defaultAndExplicitFalseNeverInstantiateTaskDependencies(){
  for(var properties:List.of(Map.<String,Object>of(),Map.<String,Object>of("medical.professional-runtime.task-enabled","false")))try(var c=new AnnotationConfigApplicationContext()){
   c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",properties));c.register(TaskProfessionalConfiguration.class,TaskInvocationConfiguration.class,ProfessionalRuntimeObservationConfiguration.class,TaskProfessionalRuntimeController.class,TaskProfessionalWorkerController.class,ProfessionalRuntimeObservationController.class,TaskResearchToolAccess.class);c.refresh();
   assertEquals(0,c.getBeanNamesForType(TaskProfessionalRuntimeService.class).length);assertEquals(0,c.getBeanNamesForType(TaskResearchToolAccess.class).length);assertEquals(0,c.getBeanNamesForType(TaskProfessionalRuntimeController.class).length);assertEquals(0,c.getBeanNamesForType(ProfessionalRuntimeObservationController.class).length);
  }
 }
 @Test void optInWithoutActualDeploymentAndDependenciesFailsStartup(){try(var c=new AnnotationConfigApplicationContext()){
  c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("medical.professional-runtime.task-enabled","true")));c.register(TaskProfessionalConfiguration.class);assertThrows(org.springframework.beans.BeansException.class,c::refresh);
 }}
 @Test void observationAndTaskRouteOwnersAreMutuallyExclusive(){
  for(boolean task:List.of(false,true))for(boolean observations:List.of(false,true))try(var c=new AnnotationConfigApplicationContext()){
   c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("medical.professional-runtime.task-enabled",task,"medical.professional-runtime.observations-enabled",observations)));
   c.registerBean("formalRunServiceIdentity",ServiceCertificateIdentity.class,()->mock(ServiceCertificateIdentity.class));c.registerBean(ProfessionalRuntimeObservations.class,()->mock(ProfessionalRuntimeObservations.class));c.registerBean(TaskProfessionalSupervisorGate.class,()->mock(TaskProfessionalSupervisorGate.class));c.registerBean(TaskHostControlBootstrap.class,()->mock(TaskHostControlBootstrap.class));c.registerBean(TaskProfessionalHostOrchestrator.class,()->mock(TaskProfessionalHostOrchestrator.class));c.registerBean(TaskInvocationBridge.class,()->mock(TaskInvocationBridge.class));c.registerBean(TaskResearchToolBridge.class,()->mock(TaskResearchToolBridge.class));c.registerBean(TaskRegisteredToolBridge.class,()->mock(TaskRegisteredToolBridge.class));c.registerBean(TaskResearchFinishOrchestrator.class,()->mock(TaskResearchFinishOrchestrator.class));
   // Peripheral component double: Spring injects this setter on the mocked registered-tool bridge too.
   c.registerBean(ApprovedWebAcquisitionService.class,()->mock(ApprovedWebAcquisitionService.class));
   c.register(ProfessionalRuntimeObservationController.class,TaskProfessionalRuntimeController.class,TaskProfessionalWorkerController.class);c.refresh();
   assertEquals(observations&&!task?1:0,c.getBeanNamesForType(ProfessionalRuntimeObservationController.class).length);assertEquals(task?1:0,c.getBeanNamesForType(TaskProfessionalRuntimeController.class).length);assertEquals(task?1:0,c.getBeanNamesForType(TaskProfessionalWorkerController.class).length);
   var routes=new HashSet<String>();for(Class<?> owner:List.of(ProfessionalRuntimeObservationController.class,TaskProfessionalRuntimeController.class,TaskProfessionalWorkerController.class)){if(c.getBeanNamesForType(owner).length==0)continue;for(var method:owner.getDeclaredMethods()){var mapping=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);if(mapping!=null)for(String path:mapping.value())assertTrue(routes.add(path),"duplicate route "+path);}}
  }
 }
 @Test void deploymentMustBeExplicitOwnedPrivateRegularBoundedFile()throws Exception{
  var file=directory.resolve("deployment.json");Files.writeString(file,"{}");Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));String owner=Files.getOwner(file).getName();
  assertThrows(Exception.class,()->TaskProfessionalConfiguration.readDeployment(file,owner,"a".repeat(64),1));
  Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"));assertThrows(IllegalArgumentException.class,()->TaskProfessionalConfiguration.readDeployment(file,owner,"a".repeat(64),1024));
  Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));var link=directory.resolve("link.json");Files.createSymbolicLink(link,file);assertThrows(IllegalArgumentException.class,()->TaskProfessionalConfiguration.readDeployment(link,owner,"a".repeat(64),1024));
  assertThrows(IllegalArgumentException.class,()->TaskProfessionalConfiguration.readDeployment(Path.of("relative.json"),owner,"a".repeat(64),1024));
 }
 @Test void deploymentCannotSmuggleDuplicateTrailingOrUnknownConfiguration()throws Exception{
  var file=directory.resolve("deployment.json");String owner=Files.getOwner(directory).getName();
  for(String body:List.of("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}","{} {}","{\"isolated\":true}")){
   Files.writeString(file,body);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));assertThrows(Exception.class,()->TaskProfessionalConfiguration.readDeployment(file,owner,"a".repeat(64),4096));
  }
 }
}
