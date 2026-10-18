package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationInvocationControllerTest {
    @Test void oversizedDeclaredBodyFailsBeforeOpeningStream()throws Exception {
        var request=mock(HttpServletRequest.class);when(request.getContentLengthLong()).thenReturn(16_777_217L);
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->SimulationInvocationController.body(request)).code());
        verify(request,never()).getInputStream();
    }
    @Test void unknownLengthStreamIsStillBounded()throws Exception {
        var request=mock(HttpServletRequest.class);when(request.getContentLengthLong()).thenReturn(-1L);
        var stream=mock(jakarta.servlet.ServletInputStream.class);when(request.getInputStream()).thenReturn(stream);
        when(stream.readNBytes(16_777_217)).thenReturn(new byte[16_777_217]);
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->SimulationInvocationController.body(request)).code());
        verify(stream).readNBytes(16_777_217);
    }
    @Test void dispatchPermitHasNoHttpSerializationRoute() {
        for(var method:SimulationInvocationController.class.getDeclaredMethods())
            assertNotEquals(SimulationInvocationService.Dispatch.class,method.getReturnType());
    }
    @Test void oversizedBodyProducesPublishedSafe413Envelope()throws Exception {
        var response=mock(jakarta.servlet.http.HttpServletResponse.class);var text=output(response);
        new SimulationInvocationController(null,null,new PermissionSchema()).denied(new TenantAccessException(ErrorCode.FILE_TOO_LARGE),response);
        verify(response).setStatus(413);assertTrue(text.toString().contains("FILE_TOO_LARGE"));
    }
    @Test void malformedJsonOrUnicodeProducesSafeSchemaRejectionWithoutEchoingInput()throws Exception {
        var response=mock(jakarta.servlet.http.HttpServletResponse.class);var text=output(response);
        new SimulationInvocationController(null,null,new PermissionSchema()).malformed(new java.nio.charset.MalformedInputException(1),response);
        verify(response).setStatus(422);assertTrue(text.toString().contains("SCHEMA_INVALID"));
        assertFalse(text.toString().contains("MalformedInput"));
    }

    @Test void internalRoutesUseWorkerLedgerWithoutReadingApplicationSession() throws Exception {
        var identities=mock(com.ruoyi.medical.identity.ApplicationSessionIdentity.class);
        var ledger=mock(SimulationInvocationService.class);
        var controller=new SimulationInvocationController(identities,ledger,new PermissionSchema());
        var request=mock(HttpServletRequest.class);var response=mock(jakarta.servlet.http.HttpServletResponse.class);
        byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var stream=mock(jakarta.servlet.ServletInputStream.class);when(request.getInputStream()).thenReturn(stream);
        when(stream.readNBytes(16_777_217)).thenReturn(body);
        when(request.getHeaders("X-Schema-Version")).thenAnswer(c->java.util.Collections.enumeration(java.util.List.of("1.0.0")));
        when(request.getHeaders("X-Simulation-Run-Token")).thenAnswer(c->java.util.Collections.enumeration(java.util.List.of("restricted-token")));
        var view=new SimulationInvocationService.View("00000000-0000-4000-8000-000000000001","RESERVED",0,"1");
        when(ledger.reserve(request,body)).thenReturn(view);when(ledger.read(request,body)).thenReturn(view);when(ledger.cancel(request,body)).thenReturn(view);
        assertEquals("SIMULATION",controller.reserve(request,response,null,"1.0.0").path("data").path("execution_mode").asText());
        controller.status(request,response,null,"1.0.0");controller.cancel(request,response,null,"1.0.0");
        verify(ledger).reserve(request,body);verify(ledger).read(request,body);verify(ledger).cancel(request,body);
        verifyNoMoreInteractions(ledger);verifyNoInteractions(identities);
        verify(response,times(3)).setHeader("Cache-Control","no-store");
    }
    @Test void missingRestrictedTokenCannotFallBackToSession() throws Exception {
        var identities=mock(com.ruoyi.medical.identity.ApplicationSessionIdentity.class);
        var ledger=mock(SimulationInvocationService.class);
        var request=mock(HttpServletRequest.class);
        when(request.getHeaders("X-Schema-Version")).thenReturn(java.util.Collections.enumeration(java.util.List.of("1.0.0")));
        var authentication=mock(org.springframework.security.core.Authentication.class);
        var controller=new SimulationInvocationController(identities,ledger,new PermissionSchema());
        assertThrows(SchemaRejection.class,()->controller.reserve(request,mock(jakarta.servlet.http.HttpServletResponse.class),authentication,"1.0.0"));
        verifyNoInteractions(identities,ledger,authentication);verify(request,never()).getInputStream();
    }

    private static java.io.ByteArrayOutputStream output(jakarta.servlet.http.HttpServletResponse response)throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream(){
            public boolean isReady(){return true;}
            public void setWriteListener(jakarta.servlet.WriteListener listener){}
            public void write(int value){bytes.write(value);}
        });return bytes;
    }

}
