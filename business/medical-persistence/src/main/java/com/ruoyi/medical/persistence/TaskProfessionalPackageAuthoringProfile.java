package com.ruoyi.medical.persistence;

/** Closed server-selected routes and payload ABI. Never deserialized from request data. */
enum TaskProfessionalPackageAuthoringProfile {
    SECTION_WRITER_N04_V2("2.0.0", "task-section-writer-package-authoring-v2.schema.json", "task-section-writer-packages-v2", "TASK-SECTION-WRITER-PACKAGE-V2"),
    SOURCE_VERIFIER_N05_V2("2.0.0", "task-source-verifier-package-authoring-v2.schema.json", "task-source-verifier-packages-v2", "TASK-SOURCE-VERIFIER-PACKAGE-V2"),
    DOCUMENT_REVIEW_N07_V2("2.0.0", "document-review-package-authoring-v2.schema.json", "document-review-packages-v2", "DOCUMENT-REVIEW-PACKAGE-V2"),
    PLAN_DRAFT_N01("1.0.0", "task-plan-draft-package-authoring.schema.json", "task-plan-draft-packages", "TASK-PLAN-DRAFT-PACKAGE-N01"),
    SECTION_WRITER_N04("1.0.0", "task-section-writer-package-authoring.schema.json", "task-section-writer-packages", "TASK-SECTION-WRITER-PACKAGE-N04"),
    SOURCE_VERIFIER_N05("1.0.0", "task-source-verifier-package-authoring.schema.json", "task-source-verifier-packages", "TASK-SOURCE-VERIFIER-PACKAGE-N05"),
    DOCUMENT_REVIEW_N07("1.0.0", "document-review-package-authoring.schema.json", "document-review-packages", "DOCUMENT-REVIEW-PACKAGE-N07"),
    V1("1.0.0", "task-professional-package-authoring.schema.json", "task-professional-packages", "TASK-PROFESSIONAL-PACKAGE"),
    V2("2.0.0", "task-professional-package-authoring-v2.schema.json", "task-professional-packages-v2", "TASK-PROFESSIONAL-PACKAGE-V2");

    final String version, contractResource, objectDirectory, proofOperation;
    TaskProfessionalPackageAuthoringProfile(String version,String contractResource,String objectDirectory,String proofOperation) {
        this.version=version;this.contractResource=contractResource;this.objectDirectory=objectDirectory;this.proofOperation=proofOperation;
    }
    String route(String project){return "/api/projects/"+project+"/"+objectDirectory;}
    String objectKey(String tenant,String project,String operation,String artifact){return "tenants/"+tenant+"/projects/"+project+"/"+objectDirectory+"/"+operation+"/"+artifact;}
    String payloadVersion(String kind){return name().endsWith("_V2")||this==V2&&!"ROLE".equals(kind)?"2.0.0":"1.0.0";}
}
