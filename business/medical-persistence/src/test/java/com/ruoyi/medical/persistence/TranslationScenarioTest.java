package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
/** Deferred synthetic fidelity examples, never approved customer samples. */
class TranslationScenarioTest {
 @Test void numericLexemesRemainExact(){assertEquals(List.of("1.00","-2.5","3%"),TranslationScenarioPipeline.numbers("Dose 1.00, offset -2.5; 3%"));assertNotEquals(TranslationScenarioPipeline.numbers("1.00"),TranslationScenarioPipeline.numbers("1"));}
 @Test void blockWhitespaceShapeIsPreserved(){assertEquals(TranslationScenarioPipeline.layout("  one\n\n  two"),TranslationScenarioPipeline.layout("  一\n\n  二"));assertNotEquals(TranslationScenarioPipeline.layout("one\ntwo"),TranslationScenarioPipeline.layout("one two"));}
 @Test void protectedTokenMultiplicityCannotDrop(){assertEquals(2,TranslationScenarioPipeline.occurrences("[A] text [A]","[A]"));}
}
