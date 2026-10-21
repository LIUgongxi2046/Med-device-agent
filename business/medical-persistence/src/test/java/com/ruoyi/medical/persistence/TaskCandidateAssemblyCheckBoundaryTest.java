package com.ruoyi.medical.persistence;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred tests. No database or runtime is invoked by authoring these cases. */
class TaskCandidateAssemblyCheckBoundaryTest {
    @Test void committedCheckCannotBeConstructedFromCallerJson(){
        for(var constructor:TaskCandidateAssemblyCheckService.Verified.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(Arrays.stream(TaskCandidateAssemblyCheckService.class.getMethods()).filter(m->m.getName().equals("save")||m.getName().equals("requireSaved")).allMatch(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{TaskCandidateAssemblyAuthority.Verified.class,TaskCandidateAssemblyObjectLedger.Registered.class})));
    }
    @Test void manifestCanOnlyGainCheckThroughPrivateBusinessProof()throws Exception{
        var method=TaskCandidateAssemblyAuthority.Verified.class.getMethod("withCommittedCheck",TaskCandidateAssemblyCheckService.Verified.class);
        assertEquals(TaskCandidateAssemblyAuthority.Verified.class,method.getReturnType());
        assertThrows(NoSuchMethodException.class,()->TaskCandidateAssemblyAuthority.Verified.class.getMethod("withCommittedCheck",String.class));
    }
}
