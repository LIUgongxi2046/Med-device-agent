package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Synthetic arithmetic only. Not executed; not product or method validation evidence. */
class ProductTechnicalRequirementTest {
 @Test void approvedDecimalFormulaPreservesExplicitOutputScale(){var c=new ProductTechnicalRequirementSchema.Conversion("synthetic","mm","cm","0.1","0",3,"UNNECESSARY","formula");assertEquals("0.123",ProductTechnicalRequirementSchema.convert("1.23",c).toPlainString());}
 @Test void precisionCannotSilentlyChange(){var c=new ProductTechnicalRequirementSchema.Conversion("synthetic","mm","cm","0.1","0",1,"UNNECESSARY","formula");assertThrows(ArithmeticException.class,()->ProductTechnicalRequirementSchema.convert("1.23",c));}
 @Test void affineFormulaUsesExactDecimal(){var c=new ProductTechnicalRequirementSchema.Conversion("synthetic","a","b","1.2","0.3",2,"UNNECESSARY","formula");assertEquals("0.42",ProductTechnicalRequirementSchema.convert("0.1",c).toPlainString());}
}
