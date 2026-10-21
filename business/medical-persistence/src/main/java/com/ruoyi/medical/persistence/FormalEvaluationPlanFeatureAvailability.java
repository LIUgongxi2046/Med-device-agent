package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.env.Environment;

/** Instance readiness, not action authorization, migration health, runnable status or completion. */
@Component
public final class FormalEvaluationPlanFeatureAvailability {
    private final EnterpriseAccess access;private final ListableBeanFactory beans;private final Environment environment;
    private final PermissionSchema json;private final FormalEvaluationPlanPreparationSchema schema;
    public FormalEvaluationPlanFeatureAvailability(EnterpriseAccess a,ListableBeanFactory b,Environment e,PermissionSchema j,FormalEvaluationPlanPreparationSchema s){access=a;beans=b;environment=e;json=j;schema=s;}
    public JsonNode read(ApplicationSessionIdentity.Verified actor,String context){return access.execute(actor,a->{
        FormalEvaluationContextService.current(a,context);
        boolean configured=environment.getProperty("medical.formal-evaluation-plan.enabled",Boolean.class,false);
        boolean core=configured&&actual(FormalEvaluationPlanPreparationController.class)&&actual(FormalEvaluationPlanPreparationService.class)
                &&actual(FormalEvaluationBoundExecutions.class)&&actual(FormalEvaluationExecutionInputs.class)&&actual(FormalEvaluationExecutionAdmission.class)
                &&actual(FormalEvaluationPlanPreparationAuthority.class)&&actual(FormalEvaluationContextStore.class)&&actual(FormalEvaluationPlanExpansion.class);
        boolean metadata=configured&&actual(FormalEvaluationPlanPreparationController.class)&&actual(FormalEvaluationPlanPreparationReadService.class);
        boolean persistent=core&&actual(FormalEvaluationPlanPreparationLedger.class)&&actual(FormalEvaluationPlanPreparationRegistration.class);
        boolean transport=false;try{if(configured)transport=beans.getBean("formalEvaluationPlanObjectTransport",RuntimeObjectTransport.class)!=null;}catch(BeansException unavailable){}
        var result=json.object().put("schema_version","1.0.0");result.putObject("features").put("bound_preparation",core)
                .put("execution_recovery",metadata).put("plan_preview",core).put("plan_preparation",persistent&&transport).put("status",metadata).put("object_recovery",persistent&&transport);
        if(persistent&&transport)result.putNull("unavailable_reason");else result.put("unavailable_reason",configured?"DEPENDENCY_UNAVAILABLE":"FEATURE_DISABLED");
        return schema.validate("Availability",result);
    });}
    private <T> boolean actual(Class<T> type){try{return beans.getBeanProvider(type).getIfUnique()!=null;}catch(BeansException unavailable){return false;}}
}
