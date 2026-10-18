# 完整 Java 测试诊断（2026-10-18）

本报告区别于已通过的 140 套、1126 项定向回归。首次不限定测试的 Maven 在 StoredObjectReferenceTest 缺少既有对象清单时失败；随后使用 failure.ignore 仅为收集后续问题，不作为通过门禁。

诊断覆盖 221 套，记录 1456 项，失败 1、错误 78、跳过 0。后续 ruoyi-quartz 编译阶段出现 Java heap space，admin 未运行，故这也不是全部模块的完整结果。

已识别类别：既有数据库/对象存储/服务秘密/Temporal/Qdrant 未恢复；历史对象/前端产物 fixture 缺失；旧测试硬编码 Python 环境路径；三处已存在数据集按相对 cwd 读取导致失败。最后三处路径已改为 medical.projectRoot，不改变数据或断言，正在重跑。

不生成秘密或历史存储版本来伪造通过；不以禁用测试或 ignore 作为最终成功。真实外部测试仍需真实部署配置。

| 类 | 测试 | 异常 | 诊断 |
|---|---|---|---|
| LogoutPersistenceInputTest | actualDatasetCannotEstablishPersistentLogoutIdentity | java.nio.file.NoSuchFileException | ../../data-design/sample_dataset.json |
| LocalAccountPaginationTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| BackchannelLogoutTokenTest | actualRegisteredDatasetCannotActAsLogoutToken | java.nio.file.NoSuchFileException | ../../data-design/sample_dataset.json |
| ServiceCertificateTrustStoreTest | readsCurrentProtectedTrustAndRejectsInvalidFilesAndSignedRevocation | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/document-venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent" |
| LocalAccountInputTest | actualDatasetCannotBeTruncatedIntoABcryptPassword | java.nio.file.NoSuchFileException | ../../data-design/sample_dataset.json |
| ServiceCertificateServletTest | authorizesActualServletCertificatesAndRejectsBrowserCredentialsAndCurrentRevocation | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/document-venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent" |
| ServiceCertificateNativeTest | verifiesActualTlsPeerCertificatesAgainstCurrentRegistrationAndSignedCrl | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/document-venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent" |
| DependencyAccessAuthorityTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| InvitationSecurityTest | allInvitationQueriesUsePublishedDatabaseFields | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| IncomingUploadPartTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/object-storage/deployment.json |
| VersionResourceAuthorityTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| AgentReleaseStoreTest | actualDatabaseQueriesPreserveEveryMappedColumnWithoutCreatingBusinessRecords | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| TaskPlanWriteEntryTest | ownerQualificationUsesActualDatabaseColumnsAndProtectedSubject | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| TaskListSchemaTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| OperationSchemaTest | operationQueriesUseNativeParsersPublishedColumnsAndSafeMetadata | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| EnterpriseRoleSchemaTest | everyRoleQueryUsesActualPublishedColumnsAndNativeSqlParser | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| CollectionTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| BudgetSchemaTest | fullBudgetQueriesUseNativeParsersAndPublishedColumns | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| DocumentUploadBasisTest | currentTaskPlanSnapshotAndAllMembersUseCompleteActualDatabaseFields | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| RunReadTest | persistentSnapshotQueriesExecuteWithActualColumnsAndClosedEnterpriseAccess | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| SourceRightsBootstrapTest | actualParserResultCannotDeclareAnApprovalDecision | java.lang.NullPointerException |  |
| SourceUploadStatusBootstrapTest | actualLocalReadCapacityIsExplicit | java.lang.NullPointerException |  |
| SourceUploadStatusBootstrapTest | originalUploadAndCompletionDefinitionsKeepTheirPublishedBytes | java.lang.NullPointerException |  |
| TaskPlanDecisionEntryTest | actualDecisionReadPreservesCompleteColumnsAndProtectedSubject | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ServletUploadInputTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/object-storage/deployment.json |
| SourceDeletionVectorConnectionTest | connectsToTheActualLocalQdrantUsingTheWorkerClient | java.lang.IllegalArgumentException | 秘密引用必须使用现有私有文件 |
| SourceDeletionVectorConnectionTest | actualQdrantRejectsCollectionAccessWithoutAnIdentity | org.opentest4j.AssertionFailedError | expected: <UNAUTHENTICATED> but was: <UNAVAILABLE> |
| ProjectPaginationTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| SourceReadAuthorityTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ResourceReadAuthorityTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ObjectContentPagingTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/object-storage/deployment.json |
| TransferConcurrencyTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| EnterpriseRegistrationReviewSchemaTest | allProvisioningQueriesUsePublishedColumnsAndInjectedEnterprise | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| TaskReadTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| AgentReleaseManifestSchemaTest | rejectsTimeoutBudgetAndAutomaticPaidRetriesThatExceedTheExplicitLimits | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | rejectsUnknownAndDuplicateFieldsTrailingValuesAndInvalidUtf8 | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | bindsCompleteCurrentSchemaAndPreservesEveryInput | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresAllValidationRulesAndBothEventAdapters | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[1] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[2] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[3] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[4] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[5] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[6] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[7] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[8] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[9] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[10] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[11] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[12] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[13] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[14] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[15] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[16] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[17] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[18] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[19] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresEveryManifestField(String)[20] | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | preservesAllExplicitScopeModesAndRejectsUnspecifiedAudiences | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | rejectsPluginCyclesUnknownDependenciesAndRepeatedIdentities | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | rejectsConflictingVersionsAndUnboundConsumers | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| AgentReleaseManifestSchemaTest | requiresPublishedSchemasAndTheirExactOriginalDigests | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-venv/bin/python |
| UploadTransferTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ScopeSnapshotStoreTest | savesAndReopensExplicitEmptyScopeInActualDatabaseWithoutExpandingSources | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| DocumentFrontendStructureTest | acceptsTheCompleteFrontendTableEditsWithStableFieldAndNodeIdentities | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/business/medical-persistence/../../.implementation-work/document-frontend-structure.json |
| S3ObjectReaderTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/object-storage/deployment.json |
| S3ObjectWriterTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/object-storage/deployment.json |
| TemplateOperationQueriesTest | actualOperationAuditAndEventQueriesUseExistingDatabaseColumns | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ContentReadReceiptTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ArtifactRegistryReadTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| TemplateApplicabilityTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| PermissionStructureTest | resourceQueriesAndEpochUpdateUsePublishedDatabaseColumns | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| StoredObjectReferencesTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| SourceCompletionBootstrapTest | actualApprovedRightsDefinitionPreservesPreviouslyDeployedBytes | java.lang.NullPointerException | 实际原部署制品缺少 |
| UploadAuthorizationTest | currentDocumentQueryReadsEveryHeadColumnAndActualProjectStatus | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| ProjectSchemaTest | allProjectQueriesUseNativeParsersAndPublishedColumns | java.io.IOException | Cannot run program "/workspace/scratch/5e00f913e304/med-device-agent/.lld-data-work/venv/bin/python" (in directory "/workspace/scratch/5e00f913e304/med-device-agent"): error=2, No  |
| DocumentUploadIssuanceTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/runtime/service-secrets.json |
| StoredObjectReferenceTest |  | java.nio.file.NoSuchFileException | /workspace/scratch/5e00f913e304/med-device-agent/.implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json |
| WorkflowDeliveryTest | preservesActualTemporalInputAndRejectsChangedRepeatedDelivery | io.temporal.client.WorkflowServiceException | workflowId='f43b61e5-2532-453a-adf1-89cc98a6b0ed/03737ba6-ead6-4f09-af3c-3e2de0cc08b3', runId='', workflowType='medical.task.run' |
