package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.WireValues;
import java.util.TreeSet;

/** Lossless identity for the new task source receipt contract, not historical JCS contracts. */
final class TaskSourceIdentity {
    private static final JsonMapper JSON=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private TaskSourceIdentity() {}
    static boolean same(JsonNode left,JsonNode right) {
        if(left==null || right==null)return left==right;
        if(left.isNumber() && right.isNumber()) {
            if(left.isIntegralNumber() && right.isIntegralNumber())return left.bigIntegerValue().equals(right.bigIntegerValue());
            return left.decimalValue().compareTo(right.decimalValue())==0;
        }
        if(left.isObject() && right.isObject()) {
            if(left.size()!=right.size())return false;
            var keys=left.fieldNames();while(keys.hasNext()){String key=keys.next();if(!right.has(key)||!same(left.get(key),right.get(key)))return false;}return true;
        }
        if(left.isArray() && right.isArray()) {
            if(left.size()!=right.size())return false;
            for(int i=0;i<left.size();i++)if(!same(left.get(i),right.get(i)))return false;return true;
        }
        return left.equals(right);
    }
    static String sha256(Object value) throws Exception {
        return WireValues.sha256(ContentReadAuthorization.canonicalBytes(tag(JSON.valueToTree(value))));
    }
    // Tag every type, so a caller object cannot collide with an encoded numeric leaf.
    private static JsonNode tag(JsonNode node) {
        var result=JsonNodeFactory.instance.arrayNode();
        if(node.isNumber())return result.add("number").add(node.decimalValue().stripTrailingZeros().toPlainString());
        if(node.isObject()) {
            result.add("object");var fields=result.addArray();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);
            for(String key:keys){var field=fields.addArray();field.add(key);field.add(tag(node.get(key)));}return result;
        }
        if(node.isArray()){result.add("array");var values=result.addArray();for(var item:node)values.add(tag(item));return result;}
        result.add(node.getNodeType().name());result.add(node);return result;
    }
}
