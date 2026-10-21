package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TaskStrictFieldTypedProtocolTest {
    @Test void globalIsNeverAnUnevidencedDefault(){
        assertThrows(IllegalArgumentException.class,()->new TaskStrictFieldTypedProtocol.Context(TaskStrictFieldTypedProtocol.Mode.GLOBAL,List.of(),null,null));
    }
    @Test void scopedLabelsRequireExactOriginalCodepointSpans(){
        var label=new TaskStrictFieldTypedProtocol.Label("10000000-0000-4000-8000-000000000001",new StrictComparisonProtocol.Span(1,3),"型号");
        var context=new TaskStrictFieldTypedProtocol.Context(TaskStrictFieldTypedProtocol.Mode.SCOPED,List.of(label),null,null);
        assertDoesNotThrow(()->TaskStrictFieldTypedProtocol.requireContext(context,"😀型号 A"));
        assertThrows(IllegalArgumentException.class,()->TaskStrictFieldTypedProtocol.requireContext(context,"😀型式 A"));
    }
    @Test void globalEvidenceMustMatchOriginalAndCannotCarryScopedLabels(){
        var context=new TaskStrictFieldTypedProtocol.Context(TaskStrictFieldTypedProtocol.Mode.GLOBAL,List.of(),new StrictComparisonProtocol.Span(0,3),"all");
        assertDoesNotThrow(()->TaskStrictFieldTypedProtocol.requireContext(context,"all models"));
        assertThrows(IllegalArgumentException.class,()->TaskStrictFieldTypedProtocol.requireContext(context,"ALL models"));
    }
    @Test void approvalCannotBeMintedFromDataConstructors(){
        assertTrue(java.util.Arrays.stream(TaskStrictFieldContextService.Verified.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }
}
