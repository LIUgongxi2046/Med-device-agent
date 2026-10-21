package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Original physical read is outside TK04 transaction; exact descendant check is inside it. */
public final class TaskPlanDraftOriginGuard {
    private final EnterpriseAccess access;private final TaskPlanDraftOriginLocator locator;private final TaskPlanDraftSavedCandidate candidates;
    private final TaskPlanDraftSavedToolReceipt tools;private final TaskPlanDraftHumanCheck checks;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    public TaskPlanDraftOriginGuard(EnterpriseAccess access,TaskPlanDraftOriginLocator locator,TaskPlanDraftSavedCandidate candidates,TaskPlanDraftSavedToolReceipt tools,TaskPlanDraftHumanCheck checks,TaskPlanDraftSchema schema,PermissionSchema json){this.access=access;this.locator=locator;this.candidates=candidates;this.tools=tools;this.checks=checks;this.schema=schema;this.json=json;}
    Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,TaskPlanWriteProtocol.View draft)throws Exception {
        var found=access.execute(subject,a->ThrowingSupplier.of(()->{TaskPlanDraftAdmission.current(a,context);return locator.locate(a,task,draft);}).get());
        if(found.isEmpty())return new Prepared(null,null);var f=found.get();var saved=candidates.read(subject,context,f.project(),f.task(),f.run());return new Prepared(f,saved);
    }
    Verified require(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,TaskPlanWriteProtocol.View draft,Prepared prepared)throws Exception {
        Objects.requireNonNull(prepared);var actual=locator.locate(a,task,draft);if(actual.isEmpty()){if(prepared.found!=null)bad();return new Verified(null,null,null);}
        if(prepared.found==null||!prepared.found.equals(actual.get())||prepared.saved==null)bad();var saved=prepared.saved.require(a);var f=actual.get();
        if(!saved.job().run().equals(f.run())||!saved.receiptSha256().equals(f.origin().required("candidate_receipt_sha256").asText())||!saved.artifactId().equals(f.origin().required("candidate_artifact_id").asText()))bad();
        var original=tools.require(saved);var checked=checks.draft(saved,draft.planRevision());checked.requireComplete();return new Verified(f,original,checked);
    }
    public static final class Prepared {private final TaskPlanDraftOriginLocator.Found found;private final TaskPlanDraftSavedCandidate.Saved saved;private Prepared(TaskPlanDraftOriginLocator.Found found,TaskPlanDraftSavedCandidate.Saved saved){this.found=found;this.saved=saved;}}
    public final class Verified {
        private final TaskPlanDraftOriginLocator.Found found;private final TaskPlanDraftSavedToolReceipt.Verified tool;private final TaskPlanDraftHumanCheck.Checked checked;
        private Verified(TaskPlanDraftOriginLocator.Found found,TaskPlanDraftSavedToolReceipt.Verified tool,TaskPlanDraftHumanCheck.Checked checked){this.found=found;this.tool=tool;this.checked=checked;}
        void requireUnchanged()throws Exception{if(found!=null){tool.requireUnchanged();checked.requireComplete();}}
        JsonNode authorizations()throws Exception {requireUnchanged();var value=json.object().put("kind","PLAN_DRAFT_ORIGIN_GUARD").put("required",found!=null);if(found!=null){value.put("run_id",found.run()).put("origin_draft_revision",found.originDraft()).put("origin_receipt_sha256",found.originReceiptSha256()).put("target_draft_revision",found.targetDraft()).put("target_sha256",found.targetHash()).put("check_receipt_sha256",checked.receiptSha256()).put("tool_receipt_sha256",schema.hash(tool.receipt()));value.set("ancestry",json.tree(found.chain()));value.set("check",checked.receipt());}return value;}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
