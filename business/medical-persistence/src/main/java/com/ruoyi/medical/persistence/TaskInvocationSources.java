package com.ruoyi.medical.persistence;

import java.util.List;

/** Closed source-selection view. Only a genuine private N02, N04 or N05 input proof can
 * create a view; it does not change the node kind or grant model transmission. */
sealed interface TaskInvocationSources permits TaskInvocationSources.Research,TaskInvocationSources.Writer,TaskInvocationSources.Verifier,TaskInvocationSources.DocumentReview {
    String tenantId(); String projectId(); String runId(); String stepId(); String snapshotId();
    String inputId(); com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(); String artifactKind();
    List<TaskNodeInputProtocol.SourceSelection> fixedSources();
    void requireUnchanged() throws Exception;
    static TaskInvocationSources of(TaskNodeInputAuthority.Verified input) { return new Research(java.util.Objects.requireNonNull(input)); }
    static TaskInvocationSources of(TaskSectionWriterInputAuthority.Verified input) { return new Writer(java.util.Objects.requireNonNull(input)); }
    static TaskInvocationSources of(TaskSourceVerifierInputAuthority.Verified input) { return new Verifier(java.util.Objects.requireNonNull(input)); }
    final class Research implements TaskInvocationSources {
        private final TaskNodeInputAuthority.Verified input;
        private Research(TaskNodeInputAuthority.Verified input){this.input=input;}
        public String artifactKind(){return TaskNodeInputRegistration.KIND;}
        public String tenantId(){return input.tenantId();} public String projectId(){return input.projectId();}
        public String runId(){return input.runId();} public String stepId(){return input.stepId();}
        public String snapshotId(){return input.snapshotId();}
        public String inputId(){return input.inputId();} public com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(){return input.objectReference();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return input.fixedSources();}
        public void requireUnchanged()throws Exception{input.requireUnchanged();}
    }
    final class Writer implements TaskInvocationSources {
        private final TaskSectionWriterInputAuthority.Verified input;
        private Writer(TaskSectionWriterInputAuthority.Verified input){this.input=input;}
        public String artifactKind(){return "TASK_SECTION_WRITER_INPUT";}
        public String tenantId(){return input.tenantId();} public String projectId(){return input.projectId();}
        public String runId(){return input.runId();} public String stepId(){return input.stepId();}
        public String snapshotId(){return input.snapshotId();}
        public String inputId(){return input.inputId();} public com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(){return input.objectReference();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return input.fixedSources();}
        public void requireUnchanged()throws Exception{input.requireUnchanged();}
    }
    final class Verifier implements TaskInvocationSources {
        private final TaskSourceVerifierInputAuthority.Verified input;
        private Verifier(TaskSourceVerifierInputAuthority.Verified input){this.input=input;}
        public String artifactKind(){return "TASK_SOURCE_VERIFIER_INPUT";}
        public String tenantId(){return input.tenantId();} public String projectId(){return input.projectId();}
        public String runId(){return input.runId();} public String stepId(){return input.stepId();}
        public String snapshotId(){return input.snapshotId();}
        public String inputId(){return input.inputId();} public com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(){return input.objectReference();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return input.fixedSources();}
        public void requireUnchanged()throws Exception{input.requireUnchanged();}
    }
    static TaskInvocationSources of(DocumentReviewRuntimeAuthority.Verified input) { return new DocumentReview(java.util.Objects.requireNonNull(input)); }
    final class DocumentReview implements TaskInvocationSources {
        private final DocumentReviewRuntimeAuthority.Verified input;
        private DocumentReview(DocumentReviewRuntimeAuthority.Verified input){this.input=input;}
        public String artifactKind(){return "DOCUMENT_BODY";}
        public String tenantId(){return input.job().tenant();} public String projectId(){return input.job().project();}
        public String runId(){return input.job().run();} public String stepId(){return input.job().step();}
        public String snapshotId(){return input.document().header().snapshotId();}
        public String inputId(){return input.document().header().bodyArtifactId();}
        public com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(){return input.document().objectReference();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){
            var result=new java.util.ArrayList<TaskNodeInputProtocol.SourceSelection>();
            for(var requirement:input.document().bodyIntent().sources()){
                var source=input.document().fixedSources().get(requirement.sourceVersion());
                if(source==null||source.selection()==null)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
                var selected=source.selection();result.add(new TaskNodeInputProtocol.SourceSelection(selected.sourceVersion(),selected.parseVersion(),selected.indexVersion(),selected.approvalRevision(),selected.bindingRevision(),source.rights().rightsRevision(),requirement.purpose()));
            }return List.copyOf(result);
        }
        public void requireUnchanged()throws Exception{input.requireUnchanged();}
    }

}
