# Normal N04, N05 and N07 package authoring

These package routes reuse the B02 immutable authoring pipeline. They do not reinterpret a TASK N02/N05 package as a saved-document review package, approve a release, create source rights, or install a runtime profile.

| Node | Preparation | Create/read | Recovery |
| --- | --- | --- | --- |
| N04 | `/api/projects/{projectId}/task-section-writer-package-preparation` | `/api/projects/{projectId}/task-section-writer-packages[/{operationId}]` | `/api/projects/{projectId}/task-section-writer-package-recoveries` |
| N05 | `/api/projects/{projectId}/task-source-verifier-package-preparation` | `/api/projects/{projectId}/task-source-verifier-packages[/{operationId}]` | `/api/projects/{projectId}/task-source-verifier-package-recoveries` |
| N07 | `/api/projects/{projectId}/document-review-package-preparation` | `/api/projects/{projectId}/document-review-packages[/{operationId}]` | `/api/projects/{projectId}/document-review-package-recoveries` |

All routes require a genuine current application session, context revision, the original `medical.task-professional-package-authoring.enabled` feature, and project `capability.configure / AGENT_PUBLICATION`. The server selects the profile from the route; clients cannot submit a profile override. The preparation digest, contract digest, proof operation, route and object directory are bound to each operation. Cross-profile replay and recovery are rejected.

Preparation checks registered output read profiles for all three package kinds, current source rights, managed adapter provenance, prompt capability choices, and profile artifacts. Role authoring physically reads the selected prompt and profile packages. The existing put/readback/immutable registration and original-only recovery protocol remains unchanged. Normal capability authoring and independent release approval are still required after package creation.

The new `professional-review-packages.catalogue.json` extension publishes the original N04 and N05 payload files without changing their bytes, together with the distinct N07 payload ABI. Java, Python and TypeScript catalogue loaders pin the extension digest. The base catalogue and prior extensions are unchanged.

For N07, the published mode is `DOCUMENT_REVIEW_N07_V1`, role name is `SourceVerifier`, node kind is `N07`, and prompt encoding is `DOCUMENT_REVIEW_INPUT_CANONICAL_JSON`. The job remains `SYSTEM_JOB / CHECK_DOCUMENT`. It is not a TASK run and has no synthesized plan or candidate-input artifact. The prompt includes the real immutable saved-document job input and physically consumed body/source content. Rule and source read receipts are independently persisted and bound to the review session.

Missing output-profile configuration, approved sources, managed adapters, prompt/role capabilities, published release, model/tokenizer/budget permissions, deployment files, or verified saved-document provenance blocks the normal path. This change seeds none of those approvals. Physical reads and host preparation are not model-send authorization; every fixed source is rechecked separately for the actual provider at transmission.

Development validation here was restricted to narrow compilation and syntax inspection. Newly added tests have not been run.
