package com.ruoyi.medical.persistence;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.StrictComparisonPolicyDefinition.*;

/** Local policy validation tests; do not claim actual policy approvals or source authority. */
class StrictComparisonDefinitionTest {
    private static final Version VERSION=new Version("implementation-v1","a".repeat(64));
    private static final Context GLOBAL=new Context(ContextMode.GLOBAL,List.of());
    private static final Combination FIELD=new Combination("field",GLOBAL,GLOBAL);
    private static Definition definition(List<Combination> coverage){return new Definition("1.0.0",Operator.DECIMAL_EXACT,new Precision(PrecisionMode.EXACT_SCALE,2),new Unit(UnitMode.EXACT_DECLARED,"mg"),new ApplicabilityScope(Applicability.APPLICABLE,null,VERSION),CoverageMode.EXACT_DECLARED_COMBINATIONS,coverage,VERSION,List.of(VERSION));}
    @Test void allExplicitPolicyDimensionsRemainImmutable(){var d=definition(List.of(FIELD));assertEquals(2,d.precision().scale());assertEquals("mg",d.unit().literal());assertThrows(UnsupportedOperationException.class,()->d.coverage().clear());assertThrows(UnsupportedOperationException.class,()->d.basis().clear());d.requireCoverage(List.of(FIELD));d.requireImplementation(VERSION);}
    @Test void precisionCannotGuessDecimalInterpretation(){assertThrows(IllegalArgumentException.class,()->new Precision(PrecisionMode.EXACT_SCALE,null));assertThrows(IllegalArgumentException.class,()->new Precision(PrecisionMode.EXACT_SCALE,13));assertThrows(IllegalArgumentException.class,()->new Precision(PrecisionMode.NOT_APPLICABLE,0));assertThrows(IllegalArgumentException.class,()->new Definition("1.0.0",Operator.TEXT_EXACT,new Precision(PrecisionMode.EXACT_SCALE,0),new Unit(UnitMode.EXACT_DECLARED,null),new ApplicabilityScope(Applicability.UNKNOWN,null,VERSION),CoverageMode.EXACT_DECLARED_COMBINATIONS,List.of(FIELD),VERSION,List.of(VERSION)));}
    @Test void contextAndUnitsNeverNormalizeOrInventScope(){assertThrows(IllegalArgumentException.class,()->new Context(ContextMode.GLOBAL,List.of("model")));assertThrows(IllegalArgumentException.class,()->new Context(ContextMode.SCOPED,List.of()));assertThrows(IllegalArgumentException.class,()->new Context(ContextMode.SCOPED,List.of("m","m")));assertThrows(IllegalArgumentException.class,()->new Unit(UnitMode.EXACT_DECLARED,""));assertNull(new Unit(UnitMode.EXACT_DECLARED,null).literal());assertNotEquals(new Unit(UnitMode.EXACT_DECLARED,"mg"),new Unit(UnitMode.EXACT_DECLARED,"g"));}
    @Test void coverageChecksSetsWithoutDroppingMissingOrDuplicateCombinations(){var d=definition(List.of(FIELD));assertThrows(IllegalArgumentException.class,()->d.requireCoverage(List.of()));assertThrows(IllegalArgumentException.class,()->d.requireCoverage(List.of(FIELD,FIELD)));assertThrows(IllegalArgumentException.class,()->definition(List.of(FIELD,FIELD)));var a=new Combination("field",new Context(ContextMode.SCOPED,List.of("a","b")),GLOBAL);var b=new Combination("field",new Context(ContextMode.SCOPED,List.of("b","a")),GLOBAL);definition(List.of(a)).requireCoverage(List.of(b));assertThrows(IllegalArgumentException.class,()->definition(List.of(a,b)));}
    @Test void changedImplementationIsNotAccepted(){assertThrows(IllegalArgumentException.class,()->definition(List.of(FIELD)).requireImplementation(new Version("implementation-v2","a".repeat(64))));assertThrows(IllegalArgumentException.class,()->definition(List.of(FIELD)).requireImplementation(new Version("implementation-v1","b".repeat(64))));}
}
