package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.ruoyi.medical.persistence.DocumentReviewReturnServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual metadata entry point and current-authority path, not a replacement HTTP/body reader. */
class DocumentReadMetadataAuthorizationTest {
    @Test void actualMetadataPrepareRejectsRevokedDocumentAuthorityWithoutObjectTransport() throws Exception {
        var permissions=mock(PermissionMapper.class); var resources=mock(ResourceReadAuthority.class);
        var transport=mock(SourceUploadTransport.class); var content=mock(ArtifactContentService.class);
        var documents=mock(DocumentReadMapper.class); var artifacts=mock(ArtifactRegistryMapper.class);
        var subject=mock(ApplicationSessionIdentity.Verified.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR,"actor","Actor","USER"));
        var admission=new EnterpriseAccess.Admission(subject,id(70),new BusinessSessionProtocol.Context(TENANT,"1","1","1"),
                new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        when(permissions.resourceOwner("DOCUMENT",DOCUMENT)).thenReturn(List.of(new PermissionProtocol.OwnerRow(DOCUMENT,PROJECT)));
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(any(),any(),eq("document.read"),eq("DOCUMENT_BODY"));
        var service=new DocumentReadService(mock(EnterpriseAccess.class),documents,permissions,resources,mock(TaskMapper.class),
                mock(ScopeSnapshotSchema.class),artifacts,mock(DependencyLineage.class),mock(ArtifactSourceAuthority.class),content,transport,
                mock(DocumentUploadPolicy.class),mock(DocumentReadPolicy.class),mock(ArtifactContentReadPolicy.class),mock(ArtifactContentPageCache.class),new PermissionSchema());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,
                ()->service.authorizeMetadata(admission,"1",DOCUMENT,REVISION)).code());
        verifyNoInteractions(transport,content,documents,artifacts);
        // A previously retained metadata proof must run the same current authorization again.
        var stateType=Class.forName(DocumentReadService.class.getName()+"$State");
        var stateConstructor=stateType.getDeclaredConstructors()[0]; stateConstructor.setAccessible(true);
        var header=mock(DocumentReadProtocol.Header.class);when(header.documentId()).thenReturn(DOCUMENT);when(header.documentRevision()).thenReturn(REVISION);
        Object state=stateConstructor.newInstance(header,"1",java.util.Map.of(),java.util.Map.of(),HASH,
                new DocumentReadProtocol.Watermarks(BigInteger.ONE,BigInteger.ONE),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),"1","1");
        var proofConstructor=DocumentReadService.MetadataRead.class.getDeclaredConstructor(stateType);proofConstructor.setAccessible(true);
        var proof=proofConstructor.newInstance(state);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireMetadataCurrent(admission,proof)).code());
        verify(resources,times(2)).require(any(),any(),eq("document.read"),eq("DOCUMENT_BODY"));
        verifyNoInteractions(transport,content,documents,artifacts);
    }
}
