package com.ruoyi.medical.persistence;

/** Immutable ledger identity, not permission. TASK bindings are reconstructed only from
 * their exact registered context. Verification jobs retain their original reader. */
sealed interface FormalInvocationBinding permits FormalInvocationBinding.Verification,TaskInvocationRecords.Binding,TaskPlanDraftInvocationRecords.Binding,TaskSectionWriterInvocationRecords.Binding,TaskSourceVerifierInvocationRecords.Binding,DocumentReviewInvocationRecords.Binding,FormalEvaluationInvocationRecords.Binding {
    String tenant();String project();String verification();String actor();String run();String principal();
    String step();String action();String context();String model();String request();String requestHash();
    String inputHash();String dependencies();String dependenciesHash();String classification();
    default boolean task(){return false;}
    record Verification(FormalVerificationJobs.Job original) implements FormalInvocationBinding {
        public Verification {java.util.Objects.requireNonNull(original);}
        public String tenant(){return original.tenant();}public String project(){return original.project();}
        public String verification(){return original.verification();}public String actor(){return original.actor();}
        public String run(){return original.run();}public String principal(){return original.principal();}
        public String step(){return original.step();}public String action(){return original.action();}
        public String context(){return original.context();}public String model(){return original.model();}
        public String request(){return original.request();}public String requestHash(){return original.requestHash();}
        public String inputHash(){return original.inputHash();}public String dependencies(){return original.dependencies();}
        public String dependenciesHash(){return original.dependenciesHash();}public String classification(){return original.classification();}
    }
}
