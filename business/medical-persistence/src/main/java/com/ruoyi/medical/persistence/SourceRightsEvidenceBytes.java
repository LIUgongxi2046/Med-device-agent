package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class SourceRightsEvidenceBytes {
    private SourceRightsEvidenceBytes() { }
    record Proof(ObjectRef reference,String fullSha256,String verifiedAt) { }

    static Proof read(ObjectRef reference,S3ObjectReader reader,S3ObjectReader.Admission admission) throws Exception {
        admission.requireCurrent();
        try(var verified=reader.readVerified(reference,admission)) {
            if(!reference.equals(verified.reference()) || !reference.sha256().equals(verified.fullSha256()))
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            admission.requireCurrent(); return new Proof(verified.reference(),verified.fullSha256(),Instant.now().truncatedTo(ChronoUnit.MICROS).toString());
        }
    }
}
