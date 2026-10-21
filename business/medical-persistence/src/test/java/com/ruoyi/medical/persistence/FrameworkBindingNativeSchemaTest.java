package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authored only; NOT_RUN in the implementation-first pass. */
class FrameworkBindingNativeSchemaTest {
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    @Test void unknownPasswordFieldsAndDuplicateKeysCannotReachOriginalDto()throws Exception {
        var s=new FrameworkBindingNativeSchema();
        assertThrows(SchemaRejection.class,()->s.validateRequest("framework.SysUserController.changeStatus",bytes("{\"userId\":2,\"status\":\"0\",\"password\":\"not-allowed\"}")));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->s.validateRequest("framework.SysUserController.changeStatus",bytes("{\"userId\":2,\"userId\":3,\"status\":\"0\"}")));
    }
    @Test void statusStillHasExactValidNativeShape()throws Exception {
        assertEquals("0",new FrameworkBindingNativeSchema().validateRequest("framework.SysUserController.changeStatus",bytes("{\"userId\":2,\"status\":\"0\"}")).required("status").asText());
    }
    @Test void queryScopeSqlCannotBeSuppliedByClient()throws Exception {
        var s=new FrameworkBindingNativeSchema();
        assertThrows(SchemaRejection.class,()->s.validateParameters("framework.SysUserController.list",Map.of("params[dataScope]",new String[]{"OR 1=1"})));
        assertThrows(SchemaRejection.class,()->s.validateParameters("framework.SysUserController.list",Map.of("params.dataScope",new String[]{"OR 1=1"})));
    }
    @Test void sortRequiresParallelUniqueBoundedNumericLists()throws Exception {
        var s=new FrameworkBindingNativeSchema();
        assertThrows(SchemaRejection.class,()->s.validateRequest("framework.SysDeptController.updateSort",bytes("{\"deptIds\":\"2,3\",\"orderNums\":\"1\"}")));
        assertThrows(SchemaRejection.class,()->s.validateRequest("framework.SysDeptController.updateSort",bytes("{\"deptIds\":\"2,2\",\"orderNums\":\"1,2\"}")));
    }
}
