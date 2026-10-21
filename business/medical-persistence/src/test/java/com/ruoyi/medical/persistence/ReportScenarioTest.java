package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
/** Deferred synthetic arithmetic fixtures, not customer acceptance evidence. */
class ReportScenarioTest {
 private ReportScenarioSchema.Input input(String k,String n){return new ReportScenarioSchema.Input(k,null,null,null,n,"mg");}
 @Test void decimalNeverUsesBinaryFloat(){var c=new ReportScenarioSchema.Calculation("total","SUM",List.of("a","b"),2,"UNNECESSARY","mg","s","","");var s=new ReportScenarioSchema.Selection("1.0.0","REPORT_GENERATION",List.of("s"),List.of(input("a","0.1"),input("b","0.2")),List.of(c));assertEquals("0.30",ReportScenarioSchema.calculate(s,c).toPlainString());}
 @Test void zeroDenominatorCannotBecomeReportValue(){var c=new ReportScenarioSchema.Calculation("ratio","RATIO",List.of("a","b"),2,"HALF_EVEN","1","s","","");var s=new ReportScenarioSchema.Selection("1.0.0","REPORT_GENERATION",List.of("s"),List.of(input("a","1"),input("b","0")),List.of(c));assertThrows(ArithmeticException.class,()->ReportScenarioSchema.calculate(s,c));}
}
