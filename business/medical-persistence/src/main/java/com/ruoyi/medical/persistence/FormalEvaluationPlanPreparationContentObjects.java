package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Genuine GET-only adapter; no generic registry bypass, object-key input or reconstruction fallback. */
public final class FormalEvaluationPlanPreparationContentObjects {
    private final SourceUploadTransport transport;
    public FormalEvaluationPlanPreparationContentObjects(SourceUploadTransport transport){this.transport=Objects.requireNonNull(transport);}
    JsonNode read(FormalEvaluationPlanPreparationContentAuthority.HistoricalRead proof)throws Exception {
        Objects.requireNonNull(proof);DocumentUploadTransferService.requireOutsideTransaction(proof.subject());proof.requireCurrent();
        S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->{proof.requireCurrent();return null;}).get();
        return transport.readDocument(proof.subject(),proof.tenant(),FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES,current,(reader,lease)->{
            S3ObjectReader.TimedAdmission guard=new S3ObjectReader.TimedAdmission(){
                public java.time.Duration remainingTime(){return lease.remainingTime();}
                public void requireCurrent(){lease.requireCurrent();current.requireCurrent();}
            };
            guard.requireCurrent();var reference=proof.reference();
            try(var verified=reader.readVerified(reference,guard)) {
                if(!reference.equals(verified.reference())||!reference.sha256().equals(verified.fullSha256()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                int length=Integer.parseInt(reference.sizeBytes());byte[] bytes=new byte[length];
                for(int at=0;at<length;){int chunk=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,length-at);guard.requireCurrent();byte[] actual=verified.chunk(at,chunk);
                    if(actual.length!=chunk)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);System.arraycopy(actual,0,bytes,at,chunk);at+=chunk;}
                guard.requireCurrent();var result=proof.consume(bytes);guard.requireCurrent();return result;
            }
        });
    }
}
