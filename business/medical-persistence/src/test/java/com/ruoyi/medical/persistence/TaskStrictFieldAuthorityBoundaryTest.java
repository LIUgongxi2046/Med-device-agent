package com.ruoyi.medical.persistence;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskStrictFieldAuthorityBoundaryTest {
    @Test void executionAndReadProofsHaveNoPublicConstructors(){
        for(Class<?> type:new Class<?>[]{TaskStrictFieldInputAuthority.Prepared.class,TaskStrictFieldInputAuthority.Verified.class,
                TaskStrictFieldTargetAuthority.Verified.class,TaskStrictFieldSourceReader.Read.class,TaskStrictFieldNode.Current.class,TaskStrictFieldNode.Handle.class})
            for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getName());
    }
    @Test void fixedRunCapabilityRequiresActualPrivateTargetProof()throws Exception {
        var method=TaskFieldTargetInputCapabilityService.class.getDeclaredMethod("requireFixedRun",EnterpriseAccess.Admission.class,
            AgentPublicationAuthority.VerifiedPublication.class,AgentExecutionPolicyAuthority.VerifiedPolicy.class,PermissionProtocol.Basis.class,
            String.class,String.class,String.class,String.class,String.class,TaskStrictFieldTargetAuthority.Verified.class);
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertNotNull(TaskFieldTargetInputCapabilityService.class.getDeclaredMethod("requireCurrent",EnterpriseAccess.Admission.class,
            AgentPublicationAuthority.VerifiedPublication.class,AgentExecutionPolicyAuthority.VerifiedPolicy.class,PermissionProtocol.Basis.class,String.class,String.class,String.class,String.class,String.class));
    }
    @Test void n03InputNeverClaimsN02OrDocumentArtifact(){
        assertEquals("urn:medical:task-strict-field-input:1.0.0",TaskStrictFieldSchema.INPUT_REF);
        assertEquals("urn:medical:task-strict-field-result:1.0.0",TaskStrictFieldSchema.RESULT_REF);
        assertThrows(IllegalArgumentException.class,()->new StrictFieldComparisonEngine.Limits(0,1,1,1,1,1,1,1));
    }
}
