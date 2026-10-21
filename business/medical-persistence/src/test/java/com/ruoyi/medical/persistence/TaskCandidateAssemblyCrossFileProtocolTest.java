package com.ruoyi.medical.persistence;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Data-contract tests only; no producer approval, external read or model call. */
final class TaskCandidateAssemblyCrossFileProtocolTest {
    @Test void documentSelectorsMustNameOneExactRevision(){assertThrows(IllegalArgumentException.class,()->new TaskCandidateAssemblyCrossFileProtocol.Selector("reported","DOCUMENT",null,id(),"DECIMAL_VALUE",null));}
    @Test void candidateSelectorsCannotSmuggleAnExternalRevision(){assertThrows(IllegalArgumentException.class,()->new TaskCandidateAssemblyCrossFileProtocol.Selector("reported","CANDIDATE",id(),id(),"DECIMAL_VALUE",null));}
    @Test void arbitraryExpressionValueSlotsAreNotSupported(){assertThrows(IllegalArgumentException.class,()->new TaskCandidateAssemblyCrossFileProtocol.Selector("reported","CANDIDATE",null,id(),"EXPRESSION",null));}
    @Test void selectedListsAreImmutable(){var rules=new ArrayList<TaskCandidateAssemblyCrossFileProtocol.Rule>();var input=new TaskCandidateAssemblyCrossFileProtocol.Input("1.0.0",List.of(),rules);rules.add(new TaskCandidateAssemblyCrossFileProtocol.Rule(id(),new PermissionProtocol.Basis(id(),"a".repeat(64)),List.of()));assertTrue(input.rules().isEmpty());assertThrows(UnsupportedOperationException.class,()->input.rules().clear());}
    private static String id(){return UUID.randomUUID().toString();}
}
