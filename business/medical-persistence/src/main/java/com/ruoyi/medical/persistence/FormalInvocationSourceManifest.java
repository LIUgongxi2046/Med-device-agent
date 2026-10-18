package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import java.util.*;
/** Reads the actual snake_case authority serialization, never caller-granted rights. */
final class FormalInvocationSourceManifest {
 private FormalInvocationSourceManifest(){}
 static SortedMap<String,String> rights(JsonNode sources){if(sources==null||!sources.isObject()||sources.isEmpty()||sources.size()>4096)bad();var result=new TreeMap<String,String>();sources.fields().forEachRemaining(e->{WireValues.uuid(e.getKey());var source=e.getValue().required("source");var rights=e.getValue().required("rights");String version=text(source,"source_version"),rightsSource=text(rights,"source_version"),revision=text(rights,"rights_revision");WireValues.uuid(revision);if(!e.getKey().equals(version)||!version.equals(rightsSource))bad();result.put(version,revision);});return Collections.unmodifiableSortedMap(result);}
 private static String text(JsonNode node,String key){var value=node.required(key);if(!value.isTextual())bad();return value.textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
