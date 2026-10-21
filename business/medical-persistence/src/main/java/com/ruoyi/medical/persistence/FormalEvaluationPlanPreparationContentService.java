package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.Objects;

/** Explicit historical read. There is intentionally no current-execution or compilation method. */
public final class FormalEvaluationPlanPreparationContentService {
    private final FormalEvaluationPlanPreparationContentAuthority authority;
    private final FormalEvaluationPlanPreparationContentObjects objects;
    public FormalEvaluationPlanPreparationContentService(FormalEvaluationPlanPreparationContentAuthority authority,FormalEvaluationPlanPreparationContentObjects objects){
        this.authority=Objects.requireNonNull(authority);this.objects=Objects.requireNonNull(objects);
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,byte[] request)throws Exception {
        var proof=authority.require(subject,context,request);var result=objects.read(proof);proof.requireCurrent();return result;
    }
}
