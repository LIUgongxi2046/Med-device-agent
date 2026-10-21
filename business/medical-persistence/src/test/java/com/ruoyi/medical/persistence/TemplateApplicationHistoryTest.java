package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deferred in-memory integrity tests; no database, provider, template approval, or service startup. */
class TemplateApplicationHistoryTest {
    private static final String ID="00000000-0000-4000-8000-000000000001",MANIFEST="a".repeat(64);
    private final Map<String,Object> values=Map.of("userId",ID,"projectId",ID,"profileId",ID,"templateId",ID);
    @Test void absentHeadAndHistoryAreExplicitRevisionZero()throws Exception {
        var mapper=mock(TemplateMapper.class);when(mapper.applicationHead(values)).thenReturn(List.of());when(mapper.applicationDecisions(values)).thenReturn(List.of());
        var result=TemplateApplicationHistory.load(mapper,values,new PermissionSchema(),mock(TemplateApplicabilitySchema.class),MANIFEST,"INTERNAL");
        assertEquals("0",result.revision());assertTrue(result.decisions().isEmpty());
    }
    @Test void nonzeroHeadCannotHideMissingHistory()throws Exception {
        var mapper=mock(TemplateMapper.class);when(mapper.applicationHead(values)).thenReturn(List.of(head("1",MANIFEST)));when(mapper.applicationDecisions(values)).thenReturn(List.of());
        var failure=assertThrows(TenantAccessException.class,()->TemplateApplicationHistory.load(mapper,values,new PermissionSchema(),mock(TemplateApplicabilitySchema.class),MANIFEST,"INTERNAL"));
        assertEquals(ErrorCode.CONTENT_MISMATCH,failure.code());
    }
    @Test void headManifestAndClassificationAreVerifiedBeforeDecisions()throws Exception {
        var mapper=mock(TemplateMapper.class);when(mapper.applicationHead(values)).thenReturn(List.of(head("1","b".repeat(64))));
        assertThrows(TenantAccessException.class,()->TemplateApplicationHistory.load(mapper,values,new PermissionSchema(),mock(TemplateApplicabilitySchema.class),MANIFEST,"INTERNAL"));
        verify(mapper,never()).applicationDecisions(values);
    }
    @Test void multipleHeadsFailClosed()throws Exception {
        var mapper=mock(TemplateMapper.class);when(mapper.applicationHead(values)).thenReturn(List.of(head("0",MANIFEST),head("0",MANIFEST)));
        assertThrows(TenantAccessException.class,()->TemplateApplicationHistory.load(mapper,values,new PermissionSchema(),mock(TemplateApplicabilitySchema.class),MANIFEST,"INTERNAL"));
        verify(mapper,never()).applicationDecisions(values);
    }
    private String head(String revision,String manifest)throws Exception {
        return JsonMapper.builder().build().writeValueAsString(Map.of("revision",revision,"schema_version","1.0.0","schema_manifest_sha256",manifest,
            "created_by",ID,"created_at","2026-10-20T00:00:00.000000Z","classification","INTERNAL"));
    }
}
