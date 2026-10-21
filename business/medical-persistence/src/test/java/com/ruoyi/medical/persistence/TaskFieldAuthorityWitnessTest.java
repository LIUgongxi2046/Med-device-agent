package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Local authority-witness wire regressions, not evidence of an actual grant or approval. */
class TaskFieldAuthorityWitnessTest {
    static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
    PermissionProtocol.GrantRow grant(String revision) {
        return new PermissionProtocol.GrantRow(id(1),id(2),id(3),"TASK",id(4),"task.field_target_input.write","{}","ACTIVE",
                LocalDateTime.of(2026,10,20,0,0),null,id(5),"1.0.0","a".repeat(64),new BigInteger(revision));
    }
    @Test void everyUint64RevisionSurvivesCanonicalStorageAndTypedDecodingExactly() throws Exception {
        var schema=new TaskFieldTargetSchema();
        for(String value:List.of("1","9007199254740992","9007199254740993","18446744073709551615")) {
            var original=grant(value);var witness=TaskFieldAuthorityWitness.tree(original);
            assertTrue(witness.required("revision").isTextual());assertEquals(value,witness.required("revision").textValue());
            var persisted=schema.read(schema.canonical(witness));assertEquals(original,TaskFieldAuthorityWitness.grant(persisted));
        }
    }
    @Test void revisionOnlyChangesAboveIeeeIntegerRangeCannotCollide() throws Exception {
        var schema=new TaskFieldTargetSchema();var first=TaskFieldAuthorityWitness.tree(grant("9007199254740992"));
        var second=TaskFieldAuthorityWitness.tree(grant("9007199254740993"));
        assertNotEquals(schema.canonical(first),schema.canonical(second));assertNotEquals(schema.hash(first),schema.hash(second));
        assertNotEquals(TaskFieldAuthorityWitness.grant(first),TaskFieldAuthorityWitness.grant(second));
    }
    @Test void grantDecoderNeverCoercesFractionalMalformedOrOutOfRangeRevision() throws Exception {
        var source=(com.fasterxml.jackson.databind.node.ObjectNode)AgentPublicationWitness.tree(grant("1"));
        var fractional=source.deepCopy().put("revision",1.25);
        assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.grant(fractional));
        for(String invalid:List.of("-1","0","01","1.0","18446744073709551616","not-a-revision")) {
            var node=source.deepCopy().put("revision",invalid);
            assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.grant(node),invalid);
        }
        var missing=source.deepCopy();missing.remove("revision");assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.grant(missing));
        var absent=source.deepCopy().putNull("revision");assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.grant(absent));
        assertEquals(grant("1"),TaskFieldAuthorityWitness.grant(source));assertTrue(source.required("revision").isIntegralNumber());
    }
    @Test void nestedSourceCountsAndGateRevisionsRemainLossless() throws Exception {
        var schema=new TaskFieldTargetSchema();var json=new PermissionSchema();var input=json.object();
        var source=input.putObject("source").putObject("selection");
        for(String key:List.of("gate_revision","expected_count","verified_count"))source.put(key,new BigInteger("9007199254740993"));
        var encoded=TaskFieldAuthorityWitness.tree(input);var persisted=schema.read(schema.canonical(encoded));
        for(String key:List.of("gate_revision","expected_count","verified_count"))assertEquals("9007199254740993",persisted.at("/source/selection/"+key).textValue());
        assertTrue(source.required("gate_revision").isIntegralNumber());
        for(String key:List.of("revision","gate_revision","expected_count","verified_count")) {
            var invalid=json.object().put(key,1.25);assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.tree(invalid));
            var malformed=json.object().put(key,"01");assertThrows(IllegalArgumentException.class,()->TaskFieldAuthorityWitness.tree(malformed));
        }
    }
    @Test void nestedGrantRevisionsBecomeExactStringsWithoutMutatingInput() {
        var original=AgentPublicationWitness.tree(List.of(grant("9007199254740993")));var encoded=TaskFieldAuthorityWitness.tree(original);
        assertTrue(original.at("/0/revision").isIntegralNumber());assertEquals("9007199254740993",encoded.at("/0/revision").textValue());
        assertEquals(original.at("/0/valid_from"),encoded.at("/0/valid_from"));assertEquals(original.at("/0/action"),encoded.at("/0/action"));
    }
}
