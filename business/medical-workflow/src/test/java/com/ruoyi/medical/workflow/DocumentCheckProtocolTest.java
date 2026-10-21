package com.ruoyi.medical.workflow;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
/** Contract tests only; no Temporal/provider/runtime process is started. */
class DocumentCheckProtocolTest {
    private static final String ID=UUID.randomUUID().toString(), HASH="a".repeat(64);
    private DocumentCheckProtocol.Reference reference(String kind,String job,String node,String queue){return new DocumentCheckProtocol.Reference("1.0.0",kind,job,node,ID,ID,ID,ID,ID,ID,ID,HASH,HASH,HASH,HASH,ID,"n07-control",queue,60000,1000);}
    @Test void separateSystemJobIdentity(){var r=reference("SYSTEM_JOB","CHECK_DOCUMENT","N07","n07-professional");assertEquals(ID+"/check-document/"+ID,r.workflowId());assertThrows(IllegalArgumentException.class,()->reference("TASK","CHECK_DOCUMENT","N07","n07-professional"));assertThrows(IllegalArgumentException.class,()->reference("SYSTEM_JOB","PLAN_DRAFT","N07","n07-professional"));assertThrows(IllegalArgumentException.class,()->reference("SYSTEM_JOB","CHECK_DOCUMENT","N05","n07-professional"));}
    @Test void neverSharesControlQueue(){assertThrows(IllegalArgumentException.class,()->reference("SYSTEM_JOB","CHECK_DOCUMENT","N07","n07-control"));}
    @Test void successfulResultRequiresRealChecksAndNoApproval(){assertThrows(IllegalArgumentException.class,()->new DocumentCheckProtocol.Receipt("1.0.0",ID,ID,HASH,ID,HASH,"SUCCEEDED",ID,ID,HASH,HASH,List.of(),false));assertThrows(IllegalArgumentException.class,()->new DocumentCheckProtocol.Receipt("1.0.0",ID,ID,HASH,ID,HASH,"SUCCEEDED",ID,ID,HASH,HASH,List.of(ID),true));assertEquals(List.of(ID),new DocumentCheckProtocol.Receipt("1.0.0",ID,ID,HASH,ID,HASH,"SUCCEEDED",ID,ID,HASH,HASH,List.of(ID),false).checkIds());}
}
