package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authored only. Explicitly NOT_RUN during implementation-first pass. */
class FrameworkFormSchemaTest {
    private FrameworkFormSchema schema()throws Exception{return new FrameworkFormSchema(new PermissionSchema());}
    private String form(String type,String options){return "{\"schema_version\":\"1.0.0\",\"title\":\"Example\",\"description\":\"\",\"fields\":[{\"key\":\"name\",\"label\":\"Name\",\"type\":\""+type+"\",\"required\":true,\"options\":"+options+"}]}";}
    @Test void acceptsClosedDeclarativeFields()throws Exception{var s=schema();s.form(s.read(form("TEXT","[]")));s.form(s.read(form("SELECT","[\"One\",\"Two\"]")));}
    @Test void rejectsScriptsAndUnknownProperties()throws Exception{var s=schema();assertThrows(Exception.class,()->s.form(s.read(form("JAVASCRIPT","[]"))));assertThrows(Exception.class,()->s.form(s.read(form("TEXT","[]").replace("\"required\":true","\"required\":true,\"onClick\":\"alert(1)\""))));}
    @Test void rejectsOptionsOnNonSelectAndEmptySelect()throws Exception{var s=schema();assertThrows(Exception.class,()->s.form(s.read(form("TEXT","[\"bad\"]"))));assertThrows(Exception.class,()->s.form(s.read(form("SELECT","[]"))));}
    @Test void rejectsDuplicateAndPrototypeKeys()throws Exception{var s=schema();assertThrows(Exception.class,()->s.form(s.read(form("TEXT","[]").replace("\"name\"","\"constructor\""))));var n=s.read(form("TEXT","[]"));((com.fasterxml.jackson.databind.node.ArrayNode)n.required("fields")).add(n.required("fields").get(0).deepCopy());assertThrows(Exception.class,()->s.form(n));}
    @Test void duplicateJsonAndTrailingTokensFail()throws Exception{var s=schema();assertThrows(Exception.class,()->s.read("{\"x\":1,\"x\":2}"));assertThrows(Exception.class,()->s.read("{} {}"));}
    @Test void digestIsCanonicalAndContractPinned()throws Exception{var s=schema();assertEquals(s.hash(s.read("{\"a\":1,\"b\":2}")),s.hash(s.read("{\"b\":2,\"a\":1}")));assertTrue(s.digest.matches("[0-9a-f]{64}"));}
}
