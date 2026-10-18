package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.beans.factory.ListableBeanFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Direct controller calls and actual MyBatis BoundSql; no HTTP filter, SQL database or S3 integration claim. */
class DocumentComparisonBoundaryTest {
 final DocumentComparisonServiceTest f;
 DocumentComparisonBoundaryTest()throws Exception {f=new DocumentComparisonServiceTest();}
 MockHttpServletRequest request(){var r=new MockHttpServletRequest();r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");return r;}
 @Test void controllerRequiresExactSingletonHeadersAndClosedQueries()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  var r=request();r.addHeader("X-Schema-Version","1.0.0");var duplicate=r;
  assertThrows(SchemaRejection.class,()->c.read(duplicate,new MockHttpServletResponse(),null,DocumentComparisonServiceTest.id(40)));
  r=request();r.setQueryString("unexpected=1");var query=r;
  assertThrows(SchemaRejection.class,()->c.read(query,new MockHttpServletResponse(),null,DocumentComparisonServiceTest.id(40)));
  r=request();r.setContent(new byte[]{1});var body=r;
  assertThrows(SchemaRejection.class,()->c.read(body,new MockHttpServletResponse(),null,DocumentComparisonServiceTest.id(40)));
  verifyNoInteractions(ids,service);
 }
 @Test void controllerBoundsBodyAfterIdentityBeforeDispatch()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  var r=request();r.addHeader("Idempotency-Key",DocumentComparisonServiceTest.KEY);r.addHeader("X-CSRF-TOKEN","csrf");r.setContent(new byte[f.enabled.maximumInputBytes()+1]);
  assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->c.create(r,new MockHttpServletResponse(),null)).code());verify(ids).requireCurrent(r,null);verifyNoInteractions(service);
 }
 @Test void controllerBoundsUnknownLengthStreamAndRequiresCsrf()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  var missing=request();missing.addHeader("Idempotency-Key",DocumentComparisonServiceTest.KEY);
  assertThrows(SchemaRejection.class,()->c.create(missing,new MockHttpServletResponse(),null));
  var r=new MockHttpServletRequest(){@Override public long getContentLengthLong(){return -1;}};
  r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");r.addHeader("Idempotency-Key",DocumentComparisonServiceTest.KEY);r.addHeader("X-CSRF-TOKEN","csrf");r.setContent(new byte[f.enabled.maximumInputBytes()+1]);
  assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->c.create(r,new MockHttpServletResponse(),null)).code());verify(ids).requireCurrent(r,null);verifyNoInteractions(service);
 }
 @Test void unauthenticatedCreateNeverReadsBodyOrDispatches()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  var r=new MockHttpServletRequest(){@Override public jakarta.servlet.ServletInputStream getInputStream(){throw new AssertionError("Unauthenticated body read");}};
  r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");r.addHeader("Idempotency-Key",DocumentComparisonServiceTest.KEY);r.addHeader("X-CSRF-TOKEN","csrf");
  when(ids.requireCurrent(r,null)).thenThrow(new org.springframework.security.authentication.BadCredentialsException("No current session"));
  assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->c.create(r,new MockHttpServletResponse(),null));verifyNoInteractions(service);
 }
 @Test void recoveryUsesSingletonOriginalAsciiKeyHeader()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  String key="original/?#key-0123456789";var r=request();r.addHeader("Idempotency-Key",key);when(ids.requireCurrent(r,null)).thenReturn(f.subject);when(service.recovery(f.subject,"1",key)).thenReturn(f.json.tree(Map.of("state","NOT_FOUND")));
  c.recovery(r,new MockHttpServletResponse(),null);verify(service).recovery(f.subject,"1",key);
  clearInvocations(ids,service);r.addHeader("Idempotency-Key",key);assertThrows(SchemaRejection.class,()->c.recovery(r,new MockHttpServletResponse(),null));verifyNoInteractions(ids,service);
 }
 @Test void comparisonKeysPreserveOriginalVisibleAsciiContract(){
  DocumentComparisonSchema.key("original/?#key-0123456789");DocumentComparisonSchema.key("!".repeat(16));DocumentComparisonSchema.key("~".repeat(128));
  for(String value:List.of("a".repeat(15),"a".repeat(129),"space not allowed key","control-key-01234\n","nonascii-key-01234é"))assertThrows(SchemaRejection.class,()->DocumentComparisonSchema.key(value));
 }
 @Test void controllerSuccessHasNoStoreSchemaAndResourceHeaders()throws Exception{
  var ids=mock(ApplicationSessionIdentity.class);var service=mock(DocumentComparisonService.class);var c=new DocumentComparisonController(ids,service,f.json,f.enabled);
  var r=request();var response=new MockHttpServletResponse();when(ids.requireCurrent(r,null)).thenReturn(f.subject);when(service.read(f.subject,"1",DocumentComparisonServiceTest.id(40))).thenReturn(f.json.tree(Map.of("ok",true)));
  var result=c.read(r,response,null,DocumentComparisonServiceTest.id(40));assertTrue(result.path("data").path("ok").asBoolean());assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("1.0.0",response.getHeader("X-Schema-Version"));assertEquals("1",response.getHeader("X-Resource-Revision"));assertNotNull(response.getHeader("X-Request-Id"));
 }
 @Test void availabilityUsesRegisteredControllerAndCurrentContextOnly()throws Exception{
  var beans=mock(ListableBeanFactory.class);when(beans.getBeanNamesForType(DocumentComparisonController.class,false,false)).thenReturn(new String[0]);var availability=new DocumentComparisonFeatureAvailability(f.access,beans,f.json);
  var disabled=availability.read(f.subject,"1");assertFalse(disabled.at("/features/document_comparison").asBoolean());assertTrue(disabled.path("maximum_request_bytes").isNull());verify(beans,never()).getBean(DocumentComparisonPolicy.class);
  when(beans.getBeanNamesForType(DocumentComparisonController.class,false,false)).thenReturn(new String[]{"controller"});when(beans.getBean(DocumentComparisonPolicy.class)).thenReturn(f.enabled);
  var enabled=availability.read(f.subject,"1");assertTrue(enabled.at("/features/document_comparison").asBoolean());assertEquals(f.enabled.maximumInputBytes().intValue(),enabled.path("maximum_request_bytes").asInt());
  clearInvocations(beans);assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->availability.read(f.subject,"2")).code());verifyNoInteractions(beans);verifyNoInteractions(f.mapper,f.documents,f.transport);
 }
 @Test void availabilityControllerRejectsQueryAndWritesClosedEnvelope()throws Exception{
  var beans=mock(ListableBeanFactory.class);when(beans.getBeanNamesForType(DocumentComparisonController.class,false,false)).thenReturn(new String[0]);var availability=new DocumentComparisonFeatureAvailability(f.access,beans,f.json);var ids=mock(ApplicationSessionIdentity.class);var c=new DocumentComparisonFeatureAvailabilityController(ids,availability,f.json);
  var r=request();r.setQueryString("");assertThrows(SchemaRejection.class,()->c.read(r,new MockHttpServletResponse(),null));verifyNoInteractions(ids);
  var valid=request();when(ids.requireCurrent(valid,null)).thenReturn(f.subject);var response=new MockHttpServletResponse();var output=c.read(valid,response,null);assertFalse(output.at("/data/features/document_comparison").asBoolean());assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("1.0.0",response.getHeader("X-Schema-Version"));
 }
 @Test void actualServiceMapsResolveEveryExecutedMyBatisParameter()throws Exception{
  Configuration configuration=new Configuration();
  try(var input=getClass().getResourceAsStream("/medical-mappers/TenantSelectionMapper.xml")){assertNotNull(input);new XMLMapperBuilder(input,configuration,"TenantSelectionMapper.xml",configuration.getSqlFragments()).parse();}
  try(var input=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/medical-mappers/DocumentComparisonMapper.xml"))){new XMLMapperBuilder(input,configuration,"DocumentComparisonMapper.xml",configuration.getSqlFragments()).parse();}
  var member=new ArtifactRegistryProtocol.MemberRow(DocumentComparisonServiceTest.id(50),DocumentComparisonServiceTest.id(51));
  when(f.left.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",DocumentComparisonServiceTest.id(1),DocumentComparisonServiceTest.id(4),DocumentComparisonServiceTest.id(8),List.of(member)));
  f.successfulStorage();f.create();f.service.read(f.subject,"1",f.intent.comparisonId());f.service.recovery(f.subject,"1",DocumentComparisonServiceTest.KEY);
  Set<String> methods=new HashSet<>();
  for(var invocation:mockingDetails(f.mapper).getInvocations()){
   String name=invocation.getMethod().getName();methods.add(name);var values=(Map<?,?>)invocation.getArgument(0);assertNotNull(values.get("userId"),name+" requires actor");
   var statement=configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper."+name);var sql=statement.getBoundSql(values);var meta=configuration.newMetaObject(values);
   for(var mapping:sql.getParameterMappings()){String key=mapping.getProperty();Object value=sql.hasAdditionalParameter(key)?sql.getAdditionalParameter(key):meta.getValue(key);assertNotNull(value,name+" missing SQL parameter "+key);}
  }
  assertTrue(methods.containsAll(Set.of("insertIntent","insertArtifact","insertRegistration","startOperation","completeOperation","operationRevision","startAudit","finalAudit","startEvent","finalEvent","insertAccessMember","insertLineage","artifactOwner")),methods.toString());
 }
}
