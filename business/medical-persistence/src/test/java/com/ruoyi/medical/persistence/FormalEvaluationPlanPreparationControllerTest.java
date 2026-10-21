package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual staged090 controllers, HTTP helpers, envelope and schema decoders in standalone MockMvc.
 * Verified-session identity, domain services and bean providers are explicit doubles. Answers invoke
 * the real schema only; these tests do not prove authorization, database/object behavior, availability
 * calculation, application component wiring, or Spring Security's CSRF-token verification. */
class FormalEvaluationPlanPreparationControllerTest {
    static final String ROOT="/api/formal-evaluation-plan-preparations";
    static final String AVAILABILITY="/api/formal-evaluation-plan-feature-availability";
    enum Kind { EXECUTION, EXECUTION_RECOVERY, PREVIEW, PREPARE, STATUS, RECOVERY }
    record Route(String path, Kind kind, boolean write) { }
    static final List<Route> ROUTES=List.of(new Route(ROOT+"/executions",Kind.EXECUTION,true),
            new Route(ROOT+"/executions/recovery",Kind.EXECUTION_RECOVERY,false),new Route(ROOT+"/preview",Kind.PREVIEW,false),
            new Route(ROOT,Kind.PREPARE,true),new Route(ROOT+"/status",Kind.STATUS,false),new Route(ROOT+"/recovery",Kind.RECOVERY,false));
    final FormalEvaluationPlanPreparationSchemaTest fixture=new FormalEvaluationPlanPreparationSchemaTest();
    final PermissionSchema json=fixture.json;
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationPlanPreparationService service=mock(FormalEvaluationPlanPreparationService.class);
    final FormalEvaluationPlanPreparationReadService reader=mock(FormalEvaluationPlanPreparationReadService.class);
    final FormalEvaluationPlanFeatureAvailability availability=mock(FormalEvaluationPlanFeatureAvailability.class);
    final ObjectProvider<FormalEvaluationPlanPreparationService> services=provider(service);
    final ObjectProvider<FormalEvaluationPlanPreparationReadService> readers=provider(reader);
    final MockMvc mvc;

    FormalEvaluationPlanPreparationControllerTest() throws Exception {
        when(identities.requireCurrent(any(),any())).thenReturn(subject);
        when(service.prepareExecution(any(),anyString(),anyString(),any())).thenAnswer(call->{fixture.schema.boundStart(call.getArgument(3));return fixture.registration();});
        when(reader.recoverExecution(any(),anyString(),any())).thenAnswer(call->{fixture.schema.boundRecovery(call.getArgument(2));return fixture.registration();});
        when(service.preview(any(),anyString(),any())).thenAnswer(call->{fixture.schema.request(call.getArgument(2));return fixture.preview();});
        when(service.prepare(any(),anyString(),anyString(),any())).thenAnswer(call->{fixture.schema.request(call.getArgument(3));return fixture.prepared();});
        when(reader.status(any(),anyString(),any())).thenAnswer(call->{fixture.schema.recovery(call.getArgument(2));return fixture.statusReceipt();});
        when(service.recover(any(),anyString(),any())).thenAnswer(call->{fixture.schema.recovery(call.getArgument(2));return fixture.prepared();});
        when(availability.read(any(),anyString())).thenReturn(fixture.availability());
        mvc=build(services,readers);
    }
    @SuppressWarnings("unchecked") static <T> ObjectProvider<T> provider(T value) {
        var result=(ObjectProvider<T>)mock(ObjectProvider.class);when(result.getIfUnique()).thenReturn(value);return result;
    }
    MockMvc build(ObjectProvider<FormalEvaluationPlanPreparationService> writer,ObjectProvider<FormalEvaluationPlanPreparationReadService> metadata) {
        return MockMvcBuilders.standaloneSetup(new FormalEvaluationPlanPreparationController(identities,writer,metadata,json),
                new FormalEvaluationPlanFeatureAvailabilityController(identities,availability,json)).build();
    }
    byte[] body(Route route) throws Exception {
        Object value=switch(route.kind()) {
            case EXECUTION -> FormalEvaluationPlanPreparationSchemaTest.boundStart();
            case EXECUTION_RECOVERY -> fixture.boundRecovery();
            case PREVIEW, PREPARE -> FormalEvaluationPlanPreparationSchemaTest.request();
            case STATUS, RECOVERY -> fixture.recovery();
        };
        return (" \n"+fixture.schema.canonical(value).replace("1.0.0","\\u0031.0.0")+" \t").getBytes(StandardCharsets.UTF_8);
    }
    MockHttpServletRequestBuilder request(Route route) throws Exception {
        var request=post(route.path()).header("X-Schema-Version","1.0.0").header("X-Context-Revision","7")
                .header("X-CSRF-TOKEN","fixture-csrf-token").contentType("application/json").content(body(route));
        if(route.write())request.header("Idempotency-Key",FormalEvaluationPlanPreparationSchemaTest.ORIGINAL_KEY);
        return request;
    }
    MockHttpServletRequestBuilder availabilityRequest() { return get(AVAILABILITY).header("X-Schema-Version","1.0.0").header("X-Context-Revision","7"); }
    MockHttpServletRequestBuilder without(MockHttpServletRequestBuilder request,String header) { return request.with(q->{q.removeHeader(header);return q;}); }
    MockHttpServletRequestBuilder replace(MockHttpServletRequestBuilder request,String header,String value) { return request.with(q->{q.removeHeader(header);q.addHeader(header,value);return q;}); }
    ResultActions error(MockMvc use,MockHttpServletRequestBuilder request,int statusCode,ErrorCode code) throws Exception {
        return use.perform(request).andExpect(status().is(statusCode)).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Schema-Version","1.0.0")).andExpect(header().exists("X-Request-Id"))
                .andExpect(result->assertEquals(code.name(),fixture.schema.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).required("code").asText()));
    }

    @Test void everyPostRouteReturnsNoStoreRevisionOneAndClosedData() throws Exception {
        for(var route:ROUTES) {
            var response=mvc.perform(request(route)).andExpect(status().is(route.write()?201:200))
                    .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Schema-Version","1.0.0"))
                    .andExpect(header().string("X-Resource-Revision","1")).andExpect(header().exists("X-Request-Id"))
                    .andReturn().getResponse();
            var envelope=fixture.schema.read(response.getContentAsString(StandardCharsets.UTF_8));
            assertEquals("1.0.0",envelope.required("schema_version").asText());
            assertEquals("1",envelope.required("resource_revision").asText());
            assertEquals(response.getHeader("X-Request-Id"),envelope.required("request_id").asText());
            assertDoesNotThrow(()->UUID.fromString(envelope.required("request_id").asText()));
            String definition=switch(route.kind()) {
                case EXECUTION, EXECUTION_RECOVERY -> "Registration";
                case PREVIEW -> "Preview";
                case PREPARE, RECOVERY -> "Prepared";
                case STATUS -> "Status";
            };
            fixture.schema.validate(definition,envelope.required("data"));
        }
    }

    @Test void forwardsExactOriginalKeyUnmodifiedBodyContextAndVerifiedIdentity() throws Exception {
        for(var route:ROUTES)mvc.perform(request(route)).andExpect(status().is(route.write()?201:200));
        String key=FormalEvaluationPlanPreparationSchemaTest.ORIGINAL_KEY;
        verify(service).prepareExecution(eq(subject),eq("7"),eq(key),eq(body(ROUTES.get(0))));
        verify(reader).recoverExecution(eq(subject),eq("7"),eq(body(ROUTES.get(1))));
        verify(service).preview(eq(subject),eq("7"),eq(body(ROUTES.get(2))));
        verify(service).prepare(eq(subject),eq("7"),eq(key),eq(body(ROUTES.get(3))));
        verify(reader).status(eq(subject),eq("7"),eq(body(ROUTES.get(4))));
        verify(service).recover(eq(subject),eq("7"),eq(body(ROUTES.get(5))));
        verifyNoMoreInteractions(service,reader);
    }

    @Test void metadataReadsWorkWithWriterUnavailableAndNeverResolveWriterProvider() throws Exception {
        when(services.getIfUnique()).thenReturn(null);
        for(var route:ROUTES)if(route.kind()==Kind.EXECUTION_RECOVERY||route.kind()==Kind.STATUS)mvc.perform(request(route)).andExpect(status().isOk());
        verifyNoInteractions(services,service);
        verify(reader).recoverExecution(eq(subject),eq("7"),eq(body(ROUTES.get(1))));
        verify(reader).status(eq(subject),eq("7"),eq(body(ROUTES.get(4))));
        verify(readers,times(2)).getIfUnique();
    }

    @Test void preparationPreviewAndObjectRecoveryUseServiceWithoutMetadataProvider() throws Exception {
        when(readers.getIfUnique()).thenReturn(null);
        for(var route:ROUTES)if(route.kind()!=Kind.EXECUTION_RECOVERY&&route.kind()!=Kind.STATUS)
            mvc.perform(request(route)).andExpect(status().is(route.write()?201:200));
        verifyNoInteractions(readers,reader);
        verify(services,times(4)).getIfUnique();
        verify(service).recover(eq(subject),eq("7"),eq(body(ROUTES.get(5))));
    }

    @Test void unavailableSelectedProviderReports503AfterIdentityAcrossAllPosts() throws Exception {
        when(services.getIfUnique()).thenReturn(null);when(readers.getIfUnique()).thenReturn(null);
        for(var route:ROUTES)error(mvc,request(route),503,ErrorCode.DEPENDENCY_UNAVAILABLE);
        verify(identities,times(6)).requireCurrent(any(),any());verifyNoInteractions(service,reader);
    }

    @Test void missingIdentityWinsOverUnavailableProvidersForEveryRoute() throws Exception {
        when(services.getIfUnique()).thenReturn(null);when(readers.getIfUnique()).thenReturn(null);
        when(identities.requireCurrent(any(),any())).thenThrow(new BadCredentialsException("synthetic revoked session"));
        for(var route:ROUTES)error(mvc,request(route),401,ErrorCode.AUTHENTICATION_REQUIRED);
        error(mvc,availabilityRequest(),401,ErrorCode.AUTHENTICATION_REQUIRED);
        verifyNoInteractions(services,readers,service,reader,availability);
    }

    @Test void readsRejectIdempotencyHeadersAndEveryPostRequiresSingleCsrfHeader() throws Exception {
        for(var route:ROUTES) {
            if(!route.write())error(mvc,request(route).header("Idempotency-Key",FormalEvaluationPlanPreparationSchemaTest.ORIGINAL_KEY),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,without(request(route),"X-CSRF-TOKEN"),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,replace(request(route),"X-CSRF-TOKEN"," "),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,request(route).header("X-CSRF-TOKEN","second-token"),422,ErrorCode.SCHEMA_INVALID);
        }
        verifyNoInteractions(identities,services,readers,service,reader);
    }

    @Test void writesRequireSingleOriginalKeyWithStrictLengthAndAscii() throws Exception {
        for(var route:ROUTES)if(route.write()) {
            error(mvc,without(request(route),"Idempotency-Key"),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,request(route).header("Idempotency-Key",FormalEvaluationPlanPreparationSchemaTest.ORIGINAL_KEY),422,ErrorCode.SCHEMA_INVALID);
            for(String key:List.of("short","k".repeat(129),"k".repeat(16)+" ","原始键".repeat(6),"original-key-one,original-key-two"))
                error(mvc,replace(request(route),"Idempotency-Key",key),422,ErrorCode.SCHEMA_INVALID);
        }
        verifyNoInteractions(service,reader);
    }

    @Test void duplicateAndCommaCombinedHeadersRejectBeforeIdentityAndProviders() throws Exception {
        for(var route:ROUTES)for(String header:List.of("X-Schema-Version","X-Context-Revision","X-CSRF-TOKEN")) {
            error(mvc,request(route).header(header,"duplicate"),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,replace(request(route),header,"first,second"),422,ErrorCode.SCHEMA_INVALID);
        }
        for(String header:List.of("X-Schema-Version","X-Context-Revision"))
            error(mvc,availabilityRequest().header(header,"duplicate"),422,ErrorCode.SCHEMA_INVALID);
        verifyNoInteractions(identities,services,readers,service,reader,availability);
    }

    @Test void missingUnsupportedSchemaOrInvalidContextCannotReachIdentity() throws Exception {
        for(var route:ROUTES) {
            error(mvc,without(request(route),"X-Schema-Version"),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,replace(request(route),"X-Schema-Version","2.0.0"),422,ErrorCode.SCHEMA_UNSUPPORTED);
            error(mvc,without(request(route),"X-Context-Revision"),422,ErrorCode.SCHEMA_INVALID);
            for(String revision:List.of("01","-1","1.0"," 1","1".repeat(21)))
                error(mvc,replace(request(route),"X-Context-Revision",revision),422,ErrorCode.SCHEMA_INVALID);
        }
        verifyNoInteractions(identities,services,readers,service,reader);
    }

    @Test void queryStringsAreRejectedOnEveryRouteBeforeIdentity() throws Exception {
        for(var route:ROUTES)error(mvc,request(route).with(q->{q.setQueryString("offset=0");return q;}),422,ErrorCode.SCHEMA_INVALID);
        error(mvc,availabilityRequest().with(q->{q.setQueryString("offset=0");return q;}),422,ErrorCode.SCHEMA_INVALID);
        verifyNoInteractions(identities,services,readers,service,reader,availability);
    }

    @Test void emptyAndOversizedBodiesCannotReachAnyDomainMethod() throws Exception {
        for(var route:ROUTES) {
            error(mvc,request(route).content(new byte[0]),422,ErrorCode.SCHEMA_INVALID);
            error(mvc,request(route).content(new byte[FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES+1]),413,ErrorCode.FILE_TOO_LARGE);
        }
        verifyNoInteractions(service,reader);
    }

    @Test void unknownContentLengthStillEnforcesStreamingBodyLimit() throws Exception {
        var streamMvc=MockMvcBuilders.standaloneSetup(new FormalEvaluationPlanPreparationController(identities,services,readers,json))
                .addFilters((request,response,chain)->chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest)request){
                    @Override public long getContentLengthLong(){return -1;}
                    @Override public int getContentLength(){return -1;}
                },response)).build();
        for(var route:ROUTES)error(streamMvc,request(route).content(new byte[FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES+1]),413,ErrorCode.FILE_TOO_LARGE);
        verifyNoInteractions(service,reader);
    }

    @Test void exactMaximumBodiesReachSchemaAndKeepTheByteBoundary() throws Exception {
        for(var route:ROUTES) {
            byte[] raw=body(route), bounded=Arrays.copyOf(raw,FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES);
            Arrays.fill(bounded,raw.length,bounded.length,(byte)' ');
            mvc.perform(request(route).content(bounded)).andExpect(status().is(route.write()?201:200));
        }
    }

    @Test void actualSchemaErrorsAreMappedTo422ThroughEveryControllerRoute() throws Exception {
        for(var route:ROUTES) {
            String raw=new String(body(route),StandardCharsets.UTF_8).trim();
            for(byte[] malformed:List.of(("{\"schema_version\":\"1.0.0\","+raw.substring(1)).getBytes(StandardCharsets.UTF_8),
                    (raw+" {}").getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xc3,0x28},"{\"unknown\":true}".getBytes(StandardCharsets.UTF_8)))
                error(mvc,request(route).content(malformed),422,ErrorCode.SCHEMA_INVALID);
        }
    }

    @Test void availabilityGetUsesOrdinaryIdentityAndRevisionOneWithoutCsrf() throws Exception {
        var response=mvc.perform(availabilityRequest()).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Resource-Revision","1"))
                .andReturn().getResponse();
        var envelope=fixture.schema.read(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("1",envelope.required("resource_revision").asText());
        assertFalse(envelope.at("/data/features/plan_preparation").booleanValue());
        assertEquals("DEPENDENCY_UNAVAILABLE",envelope.at("/data/unavailable_reason").asText());
        fixture.schema.validate("Availability",envelope.required("data"));
        verify(availability).read(subject,"7");verifyNoInteractions(services,readers,service,reader);
    }

    @Test void availabilityGetRejectsBodyAndIdempotencyHeader() throws Exception {
        error(mvc,availabilityRequest().content("{}"),422,ErrorCode.SCHEMA_INVALID);
        error(mvc,availabilityRequest().header("Idempotency-Key",FormalEvaluationPlanPreparationSchemaTest.ORIGINAL_KEY),422,ErrorCode.SCHEMA_INVALID);
        verifyNoInteractions(identities,availability,services,readers,service,reader);
    }

    @Test void readAndObjectRecoveryKeepDomainErrorStatusCodesAndDoNotLeakDetails() throws Exception {
        for(var pair:List.of(new Object[]{ErrorCode.ACCESS_DENIED,403},new Object[]{ErrorCode.OBJECT_UNAVAILABLE,404},
                new Object[]{ErrorCode.IDEMPOTENCY_CONFLICT,409},new Object[]{ErrorCode.SCOPE_STALE,409},new Object[]{ErrorCode.DEPENDENCY_UNAVAILABLE,503})) {
            doThrow(new TenantAccessException((ErrorCode)pair[0])).when(reader).status(any(),anyString(),any());
            doThrow(new TenantAccessException((ErrorCode)pair[0])).when(service).recover(any(),anyString(),any());
            error(mvc,request(ROUTES.get(4)),(Integer)pair[1],(ErrorCode)pair[0]);
            error(mvc,request(ROUTES.get(5)),(Integer)pair[1],(ErrorCode)pair[0]);
        }
        doThrow(new IllegalStateException("private-provider-secret-fixture")).when(service).preview(any(),anyString(),any());
        var response=error(mvc,request(ROUTES.get(2)),500,ErrorCode.INTERNAL_ERROR).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("private-provider-secret-fixture"));
    }
}
