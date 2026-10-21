package com.ruoyi.medical.persistence;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual B01 + F1 factory graph with circular references disabled. Factories are invoked
 * eagerly with real cycle participants; peripheral policy, SQL and TLS boundaries are doubles.
 * This is constructor closure, not deployed configuration/handshake acceptance.
 * Eager real factory/constructor graph, not a set of mocks for the cycle participants.
 * Only peripheral persistence, I/O and policy boundaries are doubles. No configuration,
 * sockets, keys, database migrations, execution or model requests are created. */
class TaskNodeRuntimeEagerBeanGraphTest {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
 private java.nio.file.Path objectDirectory;
 private record Factory(Object configuration,Method method){}
 private Map<Class<?>,Factory> factories()throws Exception {
  var result=new LinkedHashMap<Class<?>,Factory>();
  select(result,new RunTokenAuthorityConfiguration(),"runTokenAuthority");
  select(result,new FormalWorkerAccessConfiguration(),"formalWorkerAccess");
  select(result,new ModelRuntimeConfiguration(),"connectionVerificationPassedAuthority","connectionVerificationService","modelEligibilityAuthority");
  select(result,new FormalInvocationConfiguration(),"formalModelVerificationBootstrap","formalVerificationJobs","formalInvocationStarts","formalInvocationCurrent","formalInvocationReservations","formalInvocationObjectRegistration","formalInvocationSettlement","formalInvocationCompleted","formalInvocationSendPreparation","formalInvocationService");
  select(result,new TaskInvocationPackagesConfiguration(),"taskInvocationPackages");
  select(result,new TaskInvocationConfiguration(),"taskInvocationOriginals","taskInvocationRecords","taskInvocationResults","taskInvocationContext","taskInvocationService");
  select(result,new TaskProfessionalConfiguration(),"taskProfessionalRuntimeService","taskProfessionalSupervisor","taskProfessionalObjectLedger","taskProfessionalContentAuthority","taskProfessionalObjectGovernance","taskProfessionalObjectRegistration","taskResearchToolReceipts","taskProfessionalHostAccess","taskProfessionalPredecessorEvidence","taskProfessionalPromptCompiler","taskProfessionalHostOrchestrator","taskHostControlBootstrap","taskRegisteredToolAuthority","taskRegisteredToolWaitingService","taskRegisteredToolReceipts","taskRegisteredToolBridge");
  selectAll(result,new TaskRegisteredToolWaitingConfiguration());
  selectAll(result,new TaskRegisteredToolWaitingAnswerConfiguration());
  select(result,new TaskRunWaitingConfiguration(),"taskRunWaitingCommitService","taskRunRecoveryEndpointFactoryV2");
  selectAll(result,new TaskRunStageConfigurationV2());
  select(result,new TaskStrictFieldConfiguration(),"taskStrictFieldSchema","taskStrictFieldInputAuthority","taskStrictFieldPreparation","taskStrictFieldMaterializer","taskStrictFieldSourceReader","taskStrictFieldResultRegistration","taskStrictFieldNode","taskStrictFieldOriginalResult","taskStrictFieldDispatchReservation","taskStrictFieldExecutionBridge","taskStrictFieldCompletionAuthority");
  select(result,new TaskRunWorkflowV2Configuration(),"taskRunWorkflowProtocolBinding");
  selectAll(result,new TaskNodeRuntimeConfiguration());
  selectAll(result,new TaskNodeProducerConfiguration());
  selectAll(result,new TaskRunActivityConfiguration());
  return result;
 }
 private static void select(Map<Class<?>,Factory> target,Object configuration,String...names){
  for(String name:names){var method=Arrays.stream(configuration.getClass().getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing real factory: "+configuration.getClass().getSimpleName()+"."+name));method.setAccessible(true);assertNull(target.put(method.getReturnType(),new Factory(configuration,method)));}
 }
 private static final List<Class<?>> CONSTRUCTED=List.of();
 private static void selectAll(Map<Class<?>,Factory> target,Object configuration){for(var method:configuration.getClass().getDeclaredMethods())if(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)){method.setAccessible(true);assertNull(target.put(method.getReturnType(),new Factory(configuration,method)));}}
 @org.junit.jupiter.api.BeforeEach void directory()throws Exception{objectDirectory=java.nio.file.Files.createDirectory(temporary.resolve("objects"),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));}
 @Test void actualNodeConfigurationClosesAcceptedStartInputGraphAndActivityFactories()throws Exception {
  try(var context=context(null)){
   context.refresh();
   for(Class<?> type:factories().keySet())assertFalse(mockingDetails(context.getBean(type)).isMock(),type.getSimpleName());
   for(Class<?> type:CONSTRUCTED)assertFalse(mockingDetails(context.getBean(type)).isMock(),type.getSimpleName());
   assertNotNull(context.getBean(TaskRunActivityEndpointFactory.class));assertNotNull(context.getBean(TaskRunActivityPreparation.class));assertNotNull(context.getBean(RunTokenAuthority.class));
  }
 }
 @Test void missingFactualCompletionReaderFailsRatherThanFallingBack()throws Exception{
  try(var context=context(FormalInvocationCompleted.class)){assertThrows(org.springframework.beans.BeansException.class,context::refresh);}
 }
 @Test void missingTypedStageExecutionFailsEnabledV2RatherThanHidingTheStage()throws Exception{
  try(var context=context(TaskStrictFieldExecutionBridge.class)){assertThrows(org.springframework.beans.BeansException.class,context::refresh);}
 }
 private AnnotationConfigApplicationContext context(Class<?> omitted)throws Exception {
  var context=new AnnotationConfigApplicationContext();context.setAllowCircularReferences(false);
  // Use Spring's sole Environment, with explicit bounded values consumed by the real bridge.
  context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("eager-graph-fixture",Map.of(
   "medical.task-strict-field.lease-lifetime-seconds",60L,
   "medical.task-strict-field.token-lifetime-seconds",30L)));
  var factories=factories();var participants=new HashSet<>(factories.keySet());participants.addAll(CONSTRUCTED);
  var dependencies=new LinkedHashSet<Class<?>>();
  for(var factory:factories.values())collect(factory.method.getParameters(),dependencies);
  for(Class<?> type:CONSTRUCTED)collect(constructor(type).getParameters(),dependencies);
  // Explicit setter dependencies on real completion and registered-tool participants.
  dependencies.add(TaskResearchEvidenceContract.class);
  dependencies.add(ApprovedWebAcquisitionService.class);
  dependencies.removeAll(participants);
  dependencies.remove(org.springframework.core.env.Environment.class);
  for(Class<?> type:dependencies)registerMock(context,type);
  for(var entry:factories.entrySet())if(entry.getKey()!=omitted)registerFactory(context,entry.getKey(),()->invoke(context,entry.getValue()));
  for(Class<?> type:CONSTRUCTED)if(type!=omitted)registerFactory(context,type,()->construct(context,type));
  return context;
 }
 private static Constructor<?> constructor(Class<?> type){return Arrays.stream(type.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();}
 private static void collect(Parameter[] parameters,Set<Class<?>> dependencies){for(var p:parameters){Class<?> type=p.getType();if(type==ObjectProvider.class)type=(Class<?>)((ParameterizedType)p.getParameterizedType()).getActualTypeArguments()[0];if(!type.isPrimitive()&&type!=Duration.class&&type!=String.class&&type!=java.nio.file.Path.class)dependencies.add(type);}}
 private Object[] arguments(AnnotationConfigApplicationContext context,Parameter[] parameters){return Arrays.stream(parameters).map(p->{
  if(p.getType()==ObjectProvider.class)return context.getBeanProvider(ResolvableType.forType(((ParameterizedType)p.getParameterizedType()).getActualTypeArguments()[0]));
  if(p.getType()==java.nio.file.Path.class)return objectDirectory;
  if(p.getType()==long.class)return 3600L;
  if(p.getType()==int.class){var value=p.getAnnotation(Value.class);if(value==null)throw new AssertionError("Explicit int fixture required: "+p);return switch(value.value()){
   case "${medical.task-delegation.maximum-candidates}" -> 10;
   case "${medical.task-runtime.maximum-lineage-versions}","${medical.task-runtime.maximum-lineage-relations}","${medical.task-runtime.maximum-dependencies}","${medical.task-runtime.maximum-questions}","${medical.task-run.activities.maximum-termination-facts}" -> 100;
   case "${medical.task-runtime.maximum-object-bytes}","${medical.task-runtime.maximum-replay-bytes}","${medical.formal-invocation.maximum-input-bytes}" -> 1048576;
   default -> throw new AssertionError("Unknown bounded fixture: "+value.value());};}
  if(p.getType()==Duration.class)return Duration.ofSeconds(30);
  if(p.getType()==String.class)throw new AssertionError("Unexpected eager string configuration: "+p);
  return context.getBean(p.getType());
 }).toArray();}
 private Object invoke(AnnotationConfigApplicationContext context,Factory factory){try{return factory.method.invoke(factory.configuration,arguments(context,factory.method.getParameters()));}catch(InvocationTargetException e){throw propagate(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private Object construct(AnnotationConfigApplicationContext context,Class<?> type){try{var constructor=constructor(type);return constructor.newInstance(arguments(context,constructor.getParameters()));}catch(InvocationTargetException e){throw propagate(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static RuntimeException propagate(Throwable failure){if(failure instanceof RuntimeException runtime)return runtime;if(failure instanceof Error error)throw error;return new IllegalStateException(failure);}
 /** A peripheral double is not a production configuration class: registering the
  * completed singleton prevents @EnableConfigurationProperties on its declared
  * type from importing unrelated real transport configuration. */
 @SuppressWarnings({"rawtypes","unchecked"}) private static void registerMock(AnnotationConfigApplicationContext c,Class type){
  Object value;
  if(type==TaskStrictFieldRuntimePolicy.class)value=new TaskStrictFieldRuntimePolicy(100,100,100,1048576,1048576,10000,1000000,1000,1048576);
  else value=mock(type);
  c.getBeanFactory().registerSingleton("peripheral:"+type.getName(),value);
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void registerFactory(AnnotationConfigApplicationContext c,Class type,java.util.function.Supplier<Object> supplier){c.registerBean(type,supplier);}
}
