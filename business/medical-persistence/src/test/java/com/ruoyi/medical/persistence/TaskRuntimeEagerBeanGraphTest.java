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

/** Eager real factory/constructor graph, not a set of mocks for the cycle participants.
 * Only peripheral persistence, I/O and policy boundaries are doubles. No configuration,
 * sockets, keys, database migrations, execution or model requests are created. */
class TaskRuntimeEagerBeanGraphTest {
 private record Factory(Object configuration,Method method){}
 private static Map<Class<?>,Factory> factories()throws Exception {
  var result=new LinkedHashMap<Class<?>,Factory>();
  select(result,new RunTokenAuthorityConfiguration(),"runTokenAuthority");
  select(result,new FormalWorkerAccessConfiguration(),"formalWorkerAccess");
  select(result,new ModelRuntimeConfiguration(),"connectionVerificationPassedAuthority","connectionVerificationService","modelEligibilityAuthority");
  select(result,new FormalInvocationConfiguration(),"formalModelVerificationBootstrap","formalVerificationJobs","formalInvocationStarts","formalInvocationCurrent","formalInvocationReservations","formalInvocationObjectRegistration","formalInvocationSettlement","formalInvocationCompleted","formalInvocationSendPreparation","formalInvocationService");
  select(result,new TaskInvocationConfiguration(),"taskInvocationOriginals","taskInvocationRecords","taskInvocationResults","taskInvocationContext","taskInvocationService");
  select(result,new TaskProfessionalConfiguration(),"taskProfessionalRuntimeService","taskProfessionalSupervisor","taskProfessionalObjectLedger","taskProfessionalContentAuthority","taskProfessionalObjectGovernance","taskProfessionalObjectRegistration","taskResearchToolReceipts");
  return result;
 }
 private static void select(Map<Class<?>,Factory> target,Object configuration,String...names){
  for(String name:names){var method=Arrays.stream(configuration.getClass().getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();method.setAccessible(true);assertNull(target.put(method.getReturnType(),new Factory(configuration,method)));}
 }
 private static final List<Class<?>> CONSTRUCTED=List.of(FormalTaskNodeAuthority.class,FormalWorkerControlAuthority.class,FormalRunControlAccess.class,TaskNodeProducerAuthority.class,TaskNodeProducerService.class);
 @Test void actualEagerTokenModelInvocationAndHistoryGraphHasNoCycle()throws Exception {
  try(var context=context(null)){
   context.refresh();
   for(Class<?> type:factories().keySet())assertFalse(mockingDetails(context.getBean(type)).isMock(),type.getSimpleName());
   for(Class<?> type:CONSTRUCTED)assertFalse(mockingDetails(context.getBean(type)).isMock(),type.getSimpleName());
   assertNotNull(context.getBean(TaskInvocationContext.class));assertNotNull(context.getBean(RunTokenAuthority.class));
  }
 }
 @Test void missingFactualCompletionReaderFailsRatherThanFallingBack()throws Exception{
  try(var context=context(FormalInvocationCompleted.class)){assertThrows(org.springframework.beans.BeansException.class,context::refresh);}
 }
 private static AnnotationConfigApplicationContext context(Class<?> omitted)throws Exception {
  var context=new AnnotationConfigApplicationContext();context.setAllowCircularReferences(false);
  var factories=factories();var participants=new HashSet<>(factories.keySet());participants.addAll(CONSTRUCTED);
  var dependencies=new LinkedHashSet<Class<?>>();
  for(var factory:factories.values())collect(factory.method.getParameters(),dependencies);
  for(Class<?> type:CONSTRUCTED)collect(constructor(type).getParameters(),dependencies);
  // Explicit peripheral setter dependency of the TaskRegisteredToolReceipts double.
  // All cycle participants below remain real factories/constructors.
  dependencies.add(ApprovedWebAcquisitionService.class);
  dependencies.removeAll(participants);
  for(Class<?> type:dependencies)registerMock(context,type);
  for(var entry:factories.entrySet())if(entry.getKey()!=omitted)registerFactory(context,entry.getKey(),()->invoke(context,entry.getValue()));
  for(Class<?> type:CONSTRUCTED)if(type!=omitted)registerFactory(context,type,()->construct(context,type));
  return context;
 }
 private static Constructor<?> constructor(Class<?> type){return Arrays.stream(type.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();}
 private static void collect(Parameter[] parameters,Set<Class<?>> dependencies){for(var p:parameters){Class<?> type=p.getType();if(type==ObjectProvider.class)type=(Class<?>)((ParameterizedType)p.getParameterizedType()).getActualTypeArguments()[0];if(!type.isPrimitive()&&type!=Duration.class&&type!=String.class)dependencies.add(type);}}
 private static Object[] arguments(AnnotationConfigApplicationContext context,Parameter[] parameters){return Arrays.stream(parameters).map(p->{
  if(p.getType()==ObjectProvider.class)return context.getBeanProvider(ResolvableType.forType(((ParameterizedType)p.getParameterizedType()).getActualTypeArguments()[0]));
  if(p.getType()==long.class)return 3600L;if(p.getType()==int.class)return 1000;if(p.getType()==Duration.class)return Duration.ofSeconds(30);
  if(p.getType()==String.class)throw new AssertionError("Unexpected eager string configuration: "+p);
  return context.getBean(p.getType());
 }).toArray();}
 private static Object invoke(AnnotationConfigApplicationContext context,Factory factory){try{return factory.method.invoke(factory.configuration,arguments(context,factory.method.getParameters()));}catch(InvocationTargetException e){throw propagate(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static Object construct(AnnotationConfigApplicationContext context,Class<?> type){try{var constructor=constructor(type);return constructor.newInstance(arguments(context,constructor.getParameters()));}catch(InvocationTargetException e){throw propagate(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static RuntimeException propagate(Throwable failure){if(failure instanceof RuntimeException runtime)return runtime;if(failure instanceof Error error)throw error;return new IllegalStateException(failure);}
 @SuppressWarnings({"rawtypes","unchecked"}) private static void registerMock(AnnotationConfigApplicationContext c,Class type)throws Exception{
  // Producer construction consumes host-selected schema metadata, not an authority grant.
  if(type==TaskNodeProducerSchema.class){var schema=new TaskNodeProducerSchema(TaskNodeProducerProfile.RESEARCH);c.registerBean(type,()->schema);}
  else c.registerBean(type,()->mock(type));
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void registerFactory(AnnotationConfigApplicationContext c,Class type,java.util.function.Supplier<Object> supplier){c.registerBean(type,supplier);}
}
