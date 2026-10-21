import formalEvaluationContextHttp from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-context-http.schema.json'
import formalDisableRecovery from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-disable-recovery.schema.json'
import formalPolicyRead from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-policy-read.schema.json'
import formalPublicationAvailability from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-feature-availability.schema.json'
import formalPublicationReceipt from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-workflow.schema.json'
import formalScoreBinding from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-score-binding.schema.json'
import formalInputCandidates from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-input-candidates.schema.json'
import formalInputWorkflow from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-input-workflow.schema.json'
import formalInputDocument from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-input.schema.json'
import modelEnablementPolicy from '../../../../business/medical-persistence/src/main/resources/model-enablement-policy-preparation.schema.json'
import modelVerificationTokenizer from '../../../../business/medical-persistence/src/main/resources/model-verification-tokenizer-candidates.schema.json'
import modelVerificationInput from '../../../../business/medical-persistence/src/main/resources/model-verification-input-workflow.schema.json'
import formalInvocationHttp from '../../../../business/medical-persistence/src/main/resources/formal-invocation-http.schema.json'
import modelRuntime from '../../../../business/medical-persistence/src/main/resources/runtime-governance.schema.json'
import formalInvocation from '../../../../business/medical-persistence/src/main/resources/formal-invocation.schema.json'
import executionPolicy from '../../../../business/medical-persistence/src/main/resources/agent-execution-policy.schema.json'
import executionPolicyAvailability from '../../../../business/medical-persistence/src/main/resources/agent-execution-policy-feature-availability.schema.json'
import formalCapabilityCancellation from '../../../../business/medical-persistence/src/main/resources/formal-capability-cancellation.schema.json'
import runtimeRetention from '../../../../business/medical-persistence/src/main/resources/runtime-retention.schema.json'
import formalCapabilityContract from '../../../../business/medical-persistence/src/main/resources/agent-publication.schema.json'
import formalCapabilityAvailability from '../../../../business/medical-persistence/src/main/resources/agent-authoring-feature-availability.schema.json'
import businessAudit from '../../../../business/medical-persistence/src/main/resources/business-audit.schema.json'
import gruExport from '../../../../business/medical-persistence/src/main/resources/gru-record-export.schema.json'
import gruExportAvailability from '../../../../business/medical-persistence/src/main/resources/gru-record-export-feature-availability.schema.json'
import documentComparisonViews from '../../../../business/medical-persistence/src/main/resources/document-comparison-views.schema.json'
import documentComparisonAvailability from '../../../../business/medical-persistence/src/main/resources/document-comparison-feature-availability.schema.json'
import documentComparisonCore from '../../../../business/medical-persistence/src/main/resources/document-structural-comparison-core.schema.json'
import documentRevisionDirectory from '../../../../business/medical-persistence/src/main/resources/document-revision-directory.schema.json'
import documentHistoryFeatureAvailability from '../../../../business/medical-persistence/src/main/resources/document-history-feature-availability.schema.json'
import documentTechnicalCheck from '../../../../business/medical-persistence/src/main/resources/document-technical-check.schema.json'
import documentTechnicalCheckAvailability from '../../../../business/medical-persistence/src/main/resources/document-technical-check-feature-availability.schema.json'
import documentReviewReturn from '../../../../business/medical-persistence/src/main/resources/document-review-return.schema.json'
import documentRevisionAssignmentQueue from '../../../../business/medical-persistence/src/main/resources/document-revision-assignment-queue.schema.json'
import gruRecords from '../../../../business/medical-persistence/src/main/resources/gru-records.schema.json'
import gruFeatureAvailability from '../../../../business/medical-persistence/src/main/resources/gru-feature-availability.schema.json'
import simulationAuthorizedUseEvidence from '../../../../business/medical-persistence/src/main/resources/simulation-authorized-use-evidence.schema.json'
import simulationEvaluationPreparation from '../../../../business/medical-persistence/src/main/resources/simulation-evaluation-preparation.schema.json'
import simulationExecution from '../../../../agent-design/simulation-execution.schema.json'
import simulationInputArtifact from '../../../../business/medical-persistence/src/main/resources/simulation-input-artifact.schema.json'
import simulationInputArtifactLookup from '../../../../business/medical-persistence/src/main/resources/simulation-input-artifact-lookup.schema.json'
import simulationRuleWorkflow from '../../../../business/medical-persistence/src/main/resources/simulation-rule-version-workflow.schema.json'
import Ajv2020 from 'ajv/dist/2020.js'
import type { ValidateFunction } from 'ajv'
import addFormats from 'ajv-formats'
import type { AxiosResponse } from 'axios'
import { parse } from 'yaml'
import sharedText from '../../../../backend-design/interfaces.schema.yaml?raw'
import resourceDetailsText from '../../../../backend-design/resource-details.schema.yaml?raw'
import dataset from '../../../../data-design/dataset.schema.json'
import artifacts from '../../../../agent-design/artifacts.schema.json'
import taskList from '../../../../business/medical-persistence/src/main/resources/task-list.schema.json'
import runView from '../../../../business/medical-persistence/src/main/resources/run-view.schema.json'
import runControl from '../../../../business/medical-persistence/src/main/resources/run-control.schema.json'
import runCancellationView from './run-cancellation-view.schema.json'
import taskInputs from '../../../../business/medical-persistence/src/main/resources/tasks.schema.json'
import taskPlanView from '../../../../business/medical-persistence/src/main/resources/task-plan-view.schema.json'
import taskPlanWriteView from './task-plan-write-view.schema.json'
import taskPlanDecision from '../../../../business/medical-persistence/src/main/resources/task-plan-decision.schema.json'
import taskPlanReturnView from './task-plan-return-view.schema.json'
import taskCreateView from './task-create-view.schema.json'
import taskCreateDraft from '../../../../business/medical-persistence/src/main/resources/task-create-draft.schema.json'
import taskCreationSelection from '../../../../business/medical-persistence/src/main/resources/task-creation-selection.schema.json'
import taskScopeSelection from '../../../../business/medical-persistence/src/main/resources/task-scope-selection.schema.json'
import scenarioCatalogue from '../../../../business/medical-persistence/src/main/resources/scenario-catalogue.schema.json'
import identity from '../../../../business/medical-identity/src/main/resources/identity-session.schema.json'
import options from '../../../../business/medical-identity/src/main/resources/tenant-selection-options.schema.json'
import projects from '../../../../business/medical-persistence/src/main/resources/projects.schema.json'
import binding from '../../../../business/medical-identity/src/main/resources/identity-binding.schema.json'
import localAccountView from './local-account-view.schema.json'
import projectWriteView from './project-write-view.schema.json'
import budgets from '../../../../business/medical-persistence/src/main/resources/budgets.schema.json'
import budgetWriteView from './budget-write-view.schema.json'
import budgetHistory from '../../../../business/medical-persistence/src/main/resources/budget-history.schema.json'
import templates from '../../../../business/medical-persistence/src/main/resources/templates.schema.json'
import templateApproval from '../../../../business/medical-persistence/src/main/resources/template-approval.schema.json'
import templateApprovalView from './template-approval-view.schema.json'
import templateOperations from '../../../../business/medical-persistence/src/main/resources/template-operations.schema.json'
import templateApplication from '../../../../business/medical-persistence/src/main/resources/template-application.schema.json'
import templateApplicability from '../../../../business/medical-persistence/src/main/resources/template-applicability.schema.json'
import templateCreateView from './template-create-view.schema.json'
import ruleExpressions from '../../../../business/medical-persistence/src/main/resources/rule-expressions.schema.json'
import ruleWorkflow from '../../../../business/medical-persistence/src/main/resources/rule-version-workflow.schema.json'
import ruleWriteView from './rule-write-view.schema.json'
import evaluationResults from '../../../../business/medical-persistence/src/main/resources/evaluation-results.schema.json'
import evaluationView from './evaluation-view.schema.json'
import configurationDiscovery from '../../../../business/medical-persistence/src/main/resources/configuration-discovery.schema.json'
import modelConfiguration from '../../../../business/medical-persistence/src/main/resources/model-configuration.schema.json'
import modelConfigurationWrite from './model-configuration-write-view.schema.json'
import simulationModelConfiguration from '../../../../business/medical-persistence/src/main/resources/simulation-model-configuration.schema.json'
import simulationConfigurationDiscovery from '../../../../business/medical-persistence/src/main/resources/simulation-configuration-discovery.schema.json'
import simulationModelConfigurationWrite from './simulation-model-configuration-write-view.schema.json'
import enterpriseRegistration from '../../../../business/medical-persistence/src/main/resources/enterprise-registration.schema.json'
import enterpriseRegistrationView from './enterprise-registration-view.schema.json'
import enterpriseReview from '../../../../business/medical-persistence/src/main/resources/enterprise-registration-review.schema.json'
import permissionDecisions from '../../../../business/medical-persistence/src/main/resources/permission-decisions.schema.json'
import enterpriseRoles from '../../../../business/medical-persistence/src/main/resources/enterprise-roles.schema.json'
import memberInvitations from '../../../../business/medical-persistence/src/main/resources/member-invitations.schema.json'
import memberGovernanceRead from '../../../../business/medical-persistence/src/main/resources/member-governance-read.schema.json'
import roleWriteView from './role-write-view.schema.json'
import invitationAcceptView from './invitation-accept-view.schema.json'
import memberGovernanceWriteView from './member-governance-write-view.schema.json'
import collections from '../../../../business/medical-persistence/src/main/resources/collections.schema.json'
import collectionView from './collection-view.schema.json'
import frameworkNavigation from '../../../../business/medical-identity/src/main/resources/framework-navigation.schema.json'
import ownProfile from '../../../../business/medical-identity/src/main/resources/own-profile.schema.json'
import sourceUploads from '../../../../business/medical-persistence/src/main/resources/source-uploads.schema.json'
import sourceUploadStatus from '../../../../business/medical-persistence/src/main/resources/source-upload-status.schema.json'
import sourceIndexView from '../../../../business/medical-persistence/src/main/resources/source-index-view.schema.json'
import sourceExact from '../../../../business/medical-persistence/src/main/resources/source-exact.schema.json'
import sourceUploadView from './source-upload-view.schema.json'
import retrievalView from '../../../../business/medical-persistence/src/main/resources/retrieval-view.schema.json'
import sourceGovernance from '../../../../business/medical-persistence/src/main/resources/source-governance.schema.json'
import sourceRights from '../../../../business/medical-persistence/src/main/resources/source-rights-decisions.schema.json'
import exportApi from '../../../../business/medical-persistence/src/main/resources/export-api.schema.json'
import annotationReconciliations from '../../../../business/medical-persistence/src/main/resources/annotation-anchor-reconciliations.schema.json'
import annotationReconciliationWriteView from './annotation-reconciliation-write-view.schema.json'
import annotationAnchors from '../../../../business/medical-persistence/src/main/resources/annotation-anchors.schema.json'
import annotationReanchorWriteView from './annotation-reanchor-write-view.schema.json'
import annotationThreads from '../../../../business/medical-persistence/src/main/resources/annotation-threads.schema.json'
import annotationDecisions from '../../../../business/medical-persistence/src/main/resources/annotation-decisions.schema.json'
import annotationReplies from '../../../../business/medical-persistence/src/main/resources/annotation-replies.schema.json'
import annotationWriteView from './annotation-write-view.schema.json'
import requestMetadata from './request-metadata.schema.json'
import { parseErrorCode } from '../../../../contracts/typescript/src/errors'

export interface Context {
  tenant_id: string; context_revision: string; auth_epoch: string; governance_epoch: string
}
export interface Member {
  tenant_id: string; member_id: string; status: 'INVITED' | 'ACTIVE'; valid_from: string; valid_to: string | null; revision: string
}
export interface MedicalSession {
  schema_version: '1.0.0'
  user: { user_id: string; username: string | null; display_name: string | null; platform_role: 'PLATFORM_ADMIN' | 'USER' | null }
  memberships: Member[]; current_context: Context | null
  csrf: { header_name: 'X-CSRF-TOKEN'; token: string }
  action_summary: { action: string; project_id: string | null; resource_kind: string; resource_id: string | null }[]
  invitation_entry: { method: 'POST'; path_template: '/api/member-invitations/{id}/acceptances' }
}
export interface SelectionOptions {
  schema_version: '1.0.0'; user_id: string; expected_context_revision: string; selected_tenant_id: string | null; memberships: Member[]
}
export interface Basis { version_id: string; content_sha256: string }
export interface Attribute { key: string; status: 'KNOWN' | 'UNKNOWN'; value: unknown; authority_refs: Basis[]; reason: string }
export interface Project {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; name: string; responsible_user_id: string
  status: 'PENDING' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED'; revision: string
  profile: { profile_revision: string; attributes: Attribute[]; authority_refs: Basis[]; content_sha256: string; created_by: string; created_at: string }
  creation_basis: Basis; creation_reason: string; created_by: string; created_at: string; updated_at: string
  children: { tasks: string | null; active_runs: string | null; waiting_items: string | null }; allowed_actions: string[]
}
export interface Listing { schema_version: '1.0.0'; projects: Project[]; total: string; next_after: string | null }
export interface Envelope { schema_version: '1.0.0'; request_id: string; resource_revision: string | null; data: unknown }
export interface ApiError {
  schema_version: '1.0.0'; code: string; message: string; request_id: string; resource_revision: string | null
  allowed_actions: string[]; retryable: boolean; operation_id: string | null; details: { field: string; code: string }[]
}

const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allowUnionTypes: true, allErrors: true })
addFormats(ajv)
ajv.addSchema(parse(sharedText), 'urn:medical:shared')
ajv.addSchema(parse(sharedText), 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml')
ajv.addSchema(parse(resourceDetailsText), 'https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml')
ajv.addSchema(dataset, 'https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json')
ajv.addSchema(artifacts, 'https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json')
ajv.addSchema(taskList)
ajv.addSchema(taskInputs)
ajv.addSchema(taskPlanView)
ajv.addSchema(taskPlanWriteView)
ajv.addSchema(taskPlanDecision)
ajv.addSchema(taskPlanReturnView)
ajv.addSchema(taskCreateView)
ajv.addSchema(taskCreateDraft)
ajv.addSchema(taskCreationSelection)
ajv.addSchema(taskScopeSelection)
ajv.addSchema(scenarioCatalogue)
ajv.addSchema(identity, 'urn:medical:identity')
ajv.addSchema({ ...options, $defs: identity.$defs }, 'urn:medical:selection-options')
ajv.addSchema(projects)
ajv.addSchema(binding)
ajv.addSchema(localAccountView)
ajv.addSchema(projectWriteView)
ajv.addSchema(budgets)
ajv.addSchema(budgetWriteView)
ajv.addSchema(budgetHistory)
ajv.addSchema(templates)
ajv.addSchema(templateApproval)
ajv.addSchema(templateApprovalView)
ajv.addSchema(templateOperations)
ajv.addSchema(templateApplicability)
ajv.addSchema(templateApplication)
ajv.addSchema(templateCreateView)
ajv.addSchema(ruleExpressions)
ajv.addSchema(ruleWorkflow)
ajv.addSchema(ruleWriteView)
ajv.addSchema(evaluationResults)
ajv.addSchema(evaluationView)
ajv.addSchema(configurationDiscovery)
ajv.addSchema(modelConfiguration)
ajv.addSchema(modelConfigurationWrite)
ajv.addSchema(simulationModelConfiguration)
ajv.addSchema(simulationConfigurationDiscovery)
ajv.addSchema(simulationModelConfigurationWrite)
ajv.addSchema(enterpriseRegistration)
ajv.addSchema(enterpriseRegistrationView)
ajv.addSchema(enterpriseReview)
ajv.addSchema(permissionDecisions)
ajv.addSchema(enterpriseRoles)
ajv.addSchema(memberInvitations)
ajv.addSchema(memberGovernanceRead)
ajv.addSchema(roleWriteView)
ajv.addSchema(invitationAcceptView)
ajv.addSchema(memberGovernanceWriteView)
ajv.addSchema(collections)
ajv.addSchema(collectionView)
ajv.addSchema(frameworkNavigation)
ajv.addSchema(ownProfile)
ajv.addSchema(sourceUploads)
ajv.addSchema(sourceUploadStatus)
ajv.addSchema(sourceIndexView)
ajv.addSchema(sourceUploadView)
ajv.addSchema(retrievalView)
ajv.addSchema(sourceGovernance)
ajv.addSchema(sourceRights, 'urn:medical:source-rights-decisions:1.0.0')
ajv.addSchema(exportApi)
ajv.addSchema(requestMetadata)
const validators = new Map<string, ValidateFunction>()
ajv.addSchema(runView)
ajv.addSchema(sourceExact)
ajv.addSchema(runControl, 'urn:medical:run-control:1.0.0')
ajv.addSchema(annotationReconciliations)
ajv.addSchema(annotationReconciliationWriteView)
ajv.addSchema(annotationAnchors)
ajv.addSchema(annotationReanchorWriteView)
ajv.addSchema(annotationThreads)
ajv.addSchema(annotationReplies)
ajv.addSchema(annotationDecisions)
ajv.addSchema(annotationWriteView)
ajv.addSchema(runCancellationView)
ajv.addSchema(simulationExecution)
ajv.addSchema(simulationInputArtifact)
ajv.addSchema(simulationInputArtifactLookup)
ajv.addSchema(simulationRuleWorkflow)
ajv.addSchema(simulationEvaluationPreparation)
ajv.addSchema(simulationAuthorizedUseEvidence)
ajv.addSchema(gruRecords)
ajv.addSchema(gruFeatureAvailability)
ajv.addSchema(documentTechnicalCheck)
ajv.addSchema(documentTechnicalCheckAvailability)
ajv.addSchema(documentReviewReturn)
ajv.addSchema(documentRevisionAssignmentQueue)
ajv.addSchema(documentComparisonViews)
ajv.addSchema(documentComparisonAvailability)
ajv.addSchema(documentComparisonCore)
ajv.addSchema(gruExport)
ajv.addSchema(gruExportAvailability)
ajv.addSchema(businessAudit)
ajv.addSchema(documentRevisionDirectory)
ajv.addSchema(documentHistoryFeatureAvailability)
ajv.addSchema(formalCapabilityContract)
ajv.addSchema(formalCapabilityAvailability)
ajv.addSchema(runtimeRetention)
ajv.addSchema(formalCapabilityCancellation)
ajv.addSchema(executionPolicy)
ajv.addSchema(executionPolicyAvailability)
ajv.addSchema(modelRuntime)
ajv.addSchema(formalInvocation)
ajv.addSchema(formalInvocationHttp)
ajv.addSchema(formalInputCandidates)
ajv.addSchema(formalInputWorkflow)
ajv.addSchema(formalInputDocument)
ajv.addSchema(modelEnablementPolicy)
ajv.addSchema(modelVerificationInput)
ajv.addSchema(modelVerificationTokenizer)
ajv.addSchema(formalEvaluationContextHttp)
ajv.addSchema(formalDisableRecovery)
ajv.addSchema(formalPolicyRead)
ajv.addSchema(formalPublicationAvailability)
ajv.addSchema(formalPublicationReceipt)
ajv.addSchema(formalScoreBinding)
const allowedSchemaReferences = new Set<string>([
  ...['Availability','Query','Receipt','Recovery','View','Summary','Page'].map(name => formalEvaluationContextHttp.$id + '#/$defs/' + name),
  formalDisableRecovery.$id, ...['Recovery','RecoveredDisable'].map(name => formalDisableRecovery.$id + '#/$defs/' + name),
  formalPolicyRead.$id, formalPolicyRead.$id + '#/$defs/Read',
  formalPublicationAvailability.$id,
  formalPublicationReceipt.$id,
  ...['FormalScoreBinding','FormalRecovery','FormalReceipt'].map(name => formalScoreBinding.$id + '#/$defs/' + name),
  formalInputCandidates.$id + '#/$defs/Candidates',
  formalInputDocument.$id + '#/$defs/Document',
  ...['Register','Receipt','Read','Availability'].map(name => formalInputWorkflow.$id + '#/$defs/' + name),
  ...['Candidate','Register','Receipt','Read','Recovery'].map(name => modelEnablementPolicy.$id + '#/$defs/' + name),
  modelVerificationTokenizer.$id + '#/$defs/Candidates',
  ...['Id','ModelRuntimeAvailability','ModelEnableRequest','ModelEnableReceipt','ModelEnableRecovery','ConnectionResult','ConnectionPassed'].map(name => modelRuntime.$id + '#/$defs/' + name),
  formalInvocation.$id + '#/$defs/VerificationRequest',
  ...['Id','Candidates','Register','Receipt','Read','Recovery'].map(name => modelVerificationInput.$id + '#/$defs/' + name),
  ...['Cancel','CancelReceipt','CancelRecovery'].map(name => formalInvocationHttp.$id + '#/$defs/' + name),
  executionPolicyAvailability.$id,
  ...Object.keys(executionPolicy.$defs).map(name => executionPolicy.$id + '#/$defs/' + name),
  ...['CancelRequest', 'CancelReceipt', 'CancelRecovery'].map(name => formalCapabilityCancellation.$id + '#/$defs/' + name),
  ...['Id', 'Context', 'Availability', 'RetentionRequest', 'RetentionView', 'RetentionList', 'RetentionReceipt', 'RetentionRecovery', 'RetireRequest', 'RetireReceipt', 'RetireRecovery', 'CancelRequest', 'CancelReceipt', 'CancelRecovery'].map(name => runtimeRetention.$id + '#/$defs/' + name),
  formalCapabilityAvailability.$id,
  ...['FormalCapability', 'FormalCapabilityFixed', 'RuntimePermission', 'RuntimeRoleScope', 'CapabilityQuery', 'Receipt', 'RecordView', 'List']
    .map(name => formalCapabilityContract.$id + '#/$defs/' + name),
  ...['Availability', 'Query', 'Page'].map(name => businessAudit.$id + '#/$defs/' + name),
  documentComparisonAvailability.$id,
  ...Object.keys(documentComparisonViews.$defs).map(name => documentComparisonViews.$id + '#/$defs/' + name),
  ...Object.keys(documentComparisonCore.$defs).map(name => documentComparisonCore.$id + '#/$defs/' + name),
  gruExportAvailability.$id,
  ...Object.keys(gruExport.$defs).map(name => gruExport.$id + '#/$defs/' + name),
  documentHistoryFeatureAvailability.$id,
  ...Object.keys(documentRevisionDirectory.$defs).map(name => documentRevisionDirectory.$id + '#/$defs/' + name),
  documentTechnicalCheckAvailability.$id,
  ...Object.keys(documentTechnicalCheckAvailability.$defs).map(name => documentTechnicalCheckAvailability.$id + '#/$defs/' + name),
  ...Object.keys(documentTechnicalCheck.$defs).map(name => documentTechnicalCheck.$id + '#/$defs/' + name),
  ...Object.keys(documentReviewReturn.$defs).map(name => 'urn:medical:document-return-for-revision:1.0.0#/$defs/' + name),
  ...Object.keys(documentRevisionAssignmentQueue.$defs).map(name => 'urn:medical:document-revision-assignment-queue:1.0.0#/$defs/' + name),
  gruFeatureAvailability.$id,
  ...Object.keys(gruRecords.$defs).map(name => 'urn:medical:gru-records:1.0.0#/$defs/' + name),
  ...Object.keys(simulationExecution.$defs).map(name => 'urn:medical:simulation-execution:1.0.0#/$defs/' + name),
  ...Object.keys(simulationInputArtifact.$defs).map(name => 'urn:medical:simulation-input-artifact:1.0.0#/$defs/' + name),
  ...Object.keys(simulationInputArtifactLookup.$defs).map(name => 'urn:medical:simulation-input-artifact-lookup:1.0.0#/$defs/' + name),
  ...Object.keys(simulationAuthorizedUseEvidence.$defs).map(name => 'urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/' + name),
  ...Object.keys(simulationEvaluationPreparation.$defs).map(name => 'urn:medical:simulation-evaluation-preparation:1.0.0#/$defs/' + name),
  ...Object.keys(simulationRuleWorkflow.$defs).map(name => 'urn:medical:simulation-rule-version-workflow:1.0.0#/$defs/' + name),
  'urn:medical:annotation-reconciliation-write-view:1.0.0',
  ...Object.keys(annotationReconciliations.$defs).map(name => 'urn:medical:annotation-anchor-reconciliations:1.0.0#/$defs/' + name),
  'urn:medical:annotation-reanchor-write-view:1.0.0',
  ...Object.keys(annotationAnchors.$defs).map(name => 'urn:medical:annotation-anchors:1.0.0#/$defs/' + name),
  ...Object.keys(annotationThreads.$defs).map(name => 'urn:medical:annotation-threads:1.0.0#/$defs/' + name),
  ...Object.keys(annotationDecisions.$defs).map(name => 'urn:medical:annotation-decisions:1.0.0#/$defs/' + name),
  ...Object.keys(annotationReplies.$defs).map(name => 'urn:medical:annotation-replies:1.0.0#/$defs/' + name),
  'urn:medical:annotation-write-view:1.0.0',
  ...Object.keys(modelConfiguration.$defs).map(name => 'urn:medical:model-configuration:1.0.0#/$defs/' + name),
  'urn:medical:model-configuration-write-view:1.0.0',
  ...Object.keys(simulationModelConfiguration.$defs).map(name => 'urn:medical:simulation-model-configuration:1.0.0#/$defs/' + name),
  'urn:medical:simulation-model-configuration-write-view:1.0.0',
  ...['Query', 'SimulationModelList'].map(name => 'urn:medical:simulation-configuration-discovery:1.0.0#/$defs/' + name),
  'urn:medical:shared#/$defs/Envelope', 'urn:medical:shared#/$defs/Error', 'urn:medical:identity',
  ...['Field', 'PublicationInput', 'Manifest', 'PublicationReceipt', 'SearchInput', 'Hit', 'Page']
    .map(name => 'urn:medical:source-exact:1.0.0#/$defs/' + name),
  'urn:medical:identity#/$defs/SelectionRequest', 'urn:medical:identity#/$defs/SelectionReceipt',
  'urn:medical:selection-options', 'urn:medical:projects:1.0.0#/$defs/Listing', 'urn:medical:projects:1.0.0#/$defs/Project',
  'urn:medical:projects:1.0.0#/$defs/Waiting', 'urn:medical:projects:1.0.0#/$defs/WaitingListing',
  'urn:medical:identity-binding:1.0.0', 'urn:medical:local-account-view:1.0.0#/$defs/View',
  'urn:medical:local-account-view:1.0.0#/$defs/Pending', 'urn:medical:identity#/$defs/LocalAccountCredential',
  'urn:medical:identity#/$defs/LocalAccountMutation',
  'urn:medical:projects:1.0.0#/$defs/Create', 'urn:medical:projects:1.0.0#/$defs/Revise',
  'urn:medical:projects:1.0.0#/$defs/Decide', 'urn:medical:projects:1.0.0#/$defs/Receipt',
  'urn:medical:projects:1.0.0#/$defs/Attributes', 'urn:medical:shared#/$defs/OperationLookup',
  'urn:medical:shared#/$defs/OperationLookupResult', 'urn:medical:shared#/$defs/Operation',
  'urn:medical:project-write-view:1.0.0', 'urn:medical:budget-write-view:1.0.0',
  'urn:medical:budget-history:1.0.0#/$defs/Entry', 'urn:medical:budget-history:1.0.0#/$defs/Listing',
  'urn:medical:templates:1.0.0#/$defs/Content', 'urn:medical:template-approval:1.0.0#/$defs/Preview',
  'urn:medical:templates:1.0.0#/$defs/Id',
  'urn:medical:template-approval:1.0.0#/$defs/Decide', 'urn:medical:template-approval:1.0.0#/$defs/Receipt',
  'urn:medical:template-approval:1.0.0#/$defs/Id', 'urn:medical:template-approval-view:1.0.0',
  'urn:medical:templates:1.0.0#/$defs/VersionInput', 'urn:medical:templates:1.0.0#/$defs/Basis',
  'urn:medical:enterprise-registration-view:1.0.0',
  ...['Request', 'Receipt', 'Detail', 'Page', 'Id'].map(name => 'urn:medical:enterprise-registration-review:1.0.0#/$defs/' + name),
  'urn:medical:permission-decisions:1.0.0#/$defs/Condition',
  'urn:medical:permission-decisions:1.0.0#/$defs/Id',
  ...['Request', 'Receipt', 'Scope', 'Basis', 'Time', 'Action'].map(name => 'urn:medical:permission-decisions:1.0.0#/$defs/' + name),
  'urn:medical:member-governance-write-view:1.0.0',
  ...['Query', 'Member', 'Grant', 'Invitation', 'MembersPage', 'GrantsPage', 'InvitationsPage']
    .map(name => 'urn:medical:member-governance-read:1.0.0#/$defs/' + name),
  'urn:medical:member-invitations:1.0.0#/$defs/RoleDefinition', 'urn:medical:role-write-view:1.0.0',
  'urn:medical:invitation-accept-view:1.0.0',
  'urn:medical:invitation-accept-view:1.0.0#/$defs/Selection',
  ...['Id', 'Accept', 'AcceptanceReceipt', 'Credential', 'Revision'].map(name => 'urn:medical:member-invitations:1.0.0#/$defs/' + name),
  ...['Create', 'CreationReceipt', 'Roles'].map(name => 'urn:medical:member-invitations:1.0.0#/$defs/' + name),
  ...['Id', 'Publish', 'Revoke', 'Role', 'Listing', 'Receipt', 'Time'].map(name => 'urn:medical:enterprise-roles:1.0.0#/$defs/' + name),
  'urn:medical:collection-view:1.0.0',
  'urn:medical:framework-navigation:1.0.0',
  'urn:medical:source-upload-view:1.0.0',
  'urn:medical:source-upload-status:1.0.0#/$defs/View',
  'urn:medical:source-index-view:1.0.0#/$defs/Listing',
  'urn:medical:request-metadata:1.0.0',
  'urn:medical:request-metadata:1.0.0#/$defs/Id',
  'https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator',
  'urn:medical:retrieval-view:1.0.0#/$defs/SearchInput',
  'urn:medical:retrieval-view:1.0.0#/$defs/Hit',
  'urn:medical:retrieval-view:1.0.0#/$defs/Page',
  'urn:medical:source-governance:1.0.0#/$defs/DecisionInput',
  'urn:medical:source-governance:1.0.0#/$defs/BindingInput',
  'urn:medical:source-governance:1.0.0#/$defs/BindingReceipt',
  'urn:medical:source-governance:1.0.0#/$defs/BindingView',
  'urn:medical:source-governance:1.0.0#/$defs/CatalogueItem',
  'urn:medical:source-governance:1.0.0#/$defs/Listing',
  'urn:medical:source-governance:1.0.0#/$defs/ArtifactCataloguePage',
  'urn:medical:source-governance:1.0.0#/$defs/UsageItem',
  'urn:medical:source-governance:1.0.0#/$defs/UsagePage',
  'urn:medical:source-governance:1.0.0#/$defs/ParsedContentItem',
  'urn:medical:source-governance:1.0.0#/$defs/OriginalContent',
  'urn:medical:source-governance:1.0.0#/$defs/ParsedContent',
  'urn:medical:source-governance:1.0.0#/$defs/ProcessingInput',
  'urn:medical:source-governance:1.0.0#/$defs/ProcessingReceipt',
  'urn:medical:source-governance:1.0.0#/$defs/LifecycleInput',
  'urn:medical:source-governance:1.0.0#/$defs/LifecycleReceipt',
  'urn:medical:source-governance:1.0.0#/$defs/Review',
  'urn:medical:source-governance:1.0.0#/$defs/View',
  'urn:medical:source-governance:1.0.0#/$defs/Receipt',
  ...['PreviewInput', 'DecisionInput', 'Proposal', 'Preview', 'Receipt', 'Proofs']
    .map(name => 'urn:medical:source-rights-decisions:1.0.0#/$defs/' + name),
  ...['CreateInput', 'RetryInput', 'ProcessInput', 'Basis', 'Validation', 'Item', 'View', 'History', 'Receipt']
    .map(name => 'urn:medical:export-api:1.0.0#/$defs/' + name),
  ...['Id', 'Hash', 'Create', 'Created', 'Part', 'Registration', 'Completion'].map(name => 'urn:medical:source-uploads:1.0.0#/$defs/' + name),
  ...['Filter', 'Item', 'Page'].map(name => 'urn:medical:task-list:1.0.0#/$defs/' + name),
  ...['View', 'Step', 'Waiting', 'Invocation', 'Budget'].map(name => 'urn:medical:run-view:1.0.0#/$defs/' + name),
  ...['View', 'Run', 'Operation'].map(name => 'urn:medical:run-control:1.0.0#/$defs/' + name),
  'urn:medical:shared#/$defs/CancelMessage',
  'urn:medical:run-cancellation-view:1.0.0',
  'urn:medical:tasks:1.0.0#/$defs/PlanVersionInput',
  'urn:medical:tasks:1.0.0#/$defs/Create',
  'urn:medical:task-create-view:1.0.0',
  'urn:medical:task-create-draft:1.0.0',
  ...['Query', 'Owners'].map(name => 'urn:medical:task-creation-selection:1.0.0#/$defs/' + name),
  ...['Query', 'Page'].map(name => 'urn:medical:task-scope-selection:1.0.0#/$defs/' + name),
  ...['Query', 'Page'].map(name => 'urn:medical:scenario-catalogue:1.0.0#/$defs/' + name),
  'urn:medical:task-plan-view:1.0.0#/$defs/View',
  'urn:medical:task-plan-view:1.0.0#/$defs/Basis',
  'urn:medical:task-plan-write-view:1.0.0',
  ...['Baseline', 'Request', 'Receipt'].map(name => 'urn:medical:task-plan-decision:1.0.0#/$defs/' + name),
  'urn:medical:task-plan-return-view:1.0.0',
  ...['Id', 'Operation', 'DocumentSnapshot', 'DocumentBody', 'TextContent', 'FieldValue', 'BodyPage', 'RevisionInput', 'UploadRequest', 'UploadGrant', 'UploadCompletion', 'ArtifactReceipt', 'ContentChunk', 'PublicEvent', 'StreamCursor', 'CandidateDelta', 'CandidateView', 'Selection']
    .map(name => 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/' + name),
  'https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml#/$defs/TaskDetail',
  ...['Request', 'Pending', 'Profile'].map(name => 'urn:medical:own-profile:1.0.0#/$defs/' + name),
  ...['MemberSelection', 'Content', 'Create', 'VersionInput', 'Member', 'Manifest', 'View', 'Created', 'Page']
    .map(name => 'urn:medical:collections:1.0.0#/$defs/' + name),
  ...['Request', 'Detail', 'Page', 'Uuid'].map(name => 'urn:medical:enterprise-registration:1.0.0#/$defs/' + name),
  ...['LocalAccountPage', 'LocalAccountItem'].map(name => 'urn:medical:identity#/$defs/' + name),
  'urn:medical:template-operations:1.0.0#/$defs/Created', 'urn:medical:template-create-view:1.0.0',
  ...['Create', 'Append', 'Finalize', 'Definition', 'Score', 'Result', 'Receipt', 'Basis', 'Cell'].map(name => 'urn:medical:evaluation-results:1.0.0#/$defs/' + name),
  ...['Id', 'Read', 'Scores', 'Pending'].map(name => 'urn:medical:evaluation-view:1.0.0#/$defs/' + name),
  'urn:medical:rule-write-view:1.0.0',
  ...['Query', 'RuleList', 'ModelList'].map(name => 'urn:medical:configuration-discovery:1.0.0#/$defs/' + name),
  ...['Id', 'Hash', 'Basis', 'VersionInput', 'StoredRule', 'Read', 'Created', 'Preview', 'Decide', 'Receipt']
    .map(name => 'urn:medical:rule-version-workflow:1.0.0#/$defs/' + name),
  ...['Content', 'Constant', 'Result'].map(name => 'urn:medical:rule-expressions:1.0.0#/$defs/' + name),
  ...['Query', 'Page'].map(name => 'urn:medical:template-applicability:1.0.0#/$defs/' + name),
  'urn:medical:templates:1.0.0#/$defs/Condition',
  ...['DocumentType', 'ApplicationPath', 'ConditionSchema', 'FormatProfile', 'Bases', 'Node', 'Field', 'Repeat', 'Attachment', 'CrossDocumentRule']
    .map(name => 'urn:medical:templates:1.0.0#/$defs/' + name),
  ...['Create', 'Decide', 'Approval', 'Account', 'Listing', 'Receipt', 'Limits', 'Id', 'Money', 'Currency', 'Time']
    .map(name => 'urn:medical:budgets:1.0.0#/$defs/' + name)])

function schemaValidator(reference: string): ValidateFunction | undefined {
  // Membership is checked before AJV: registered but unapproved fragments stay inaccessible.
  if (!allowedSchemaReferences.has(reference)) return undefined
  const cached = validators.get(reference)
  if (cached) return cached
  const validator = ajv.getSchema(reference)
  if (!validator) throw new Error('医疗接口Schema缺少登记定义')
  validators.set(reference, validator)
  return validator
}

// Explicit build/test gate. Runtime calls stay synchronous and compile only on first use.
export function compileAllMedicalSchemas(): number {
  for (const reference of allowedSchemaReferences) {
    if (!schemaValidator(reference)) throw new Error('医疗接口Schema缺少登记定义')
  }
  return allowedSchemaReferences.size
}

export class MedicalFailure extends Error {
  constructor(readonly code: string, message: string, readonly requestId: string | null = null) {
    super(message)
    this.name = 'MedicalFailure'
  }
}

export function fail(code: string, message: string, requestId: string | null = null): never {
  const failure = new MedicalFailure(code, message, requestId)
  window.dispatchEvent(new CustomEvent('medical-failure', { detail: failure }))
  throw failure
}

export function revision(value: string): string {
  if (!/^(0|[1-9][0-9]{0,19})$/.test(value) || BigInt(value) > 18446744073709551615n) fail('SCHEMA_INVALID', '版本字段超出批准范围')
  return value
}

export function validate<T>(reference: string, value: unknown): T {
  let validator: ValidateFunction | undefined
  try { validator = schemaValidator(reference) }
  catch { fail('SCHEMA_INVALID', '医疗接口Schema编译失败') }
  if (!validator || !validator(value)) fail('SCHEMA_INVALID', '医疗接口响应未通过Schema校验')
  return value as T
}

export function schemaIssues(reference: string, value: unknown): { path: string; code: string }[] {
  const validator = schemaValidator(reference)
  if (!validator) throw new Error('医疗表单Schema缺少登记定义')
  if (validator(value)) return []
  return (validator.errors ?? []).map(error => ({ path: error.instancePath, code: error.keyword }))
}

export function envelope(response: AxiosResponse<unknown>): Envelope {
  if (response.headers['cache-control'] !== 'no-store' || response.headers['x-schema-version'] !== '1.0.0') {
    fail('SCHEMA_INVALID', '医疗接口Header未通过校验')
  }
  if (response.status < 200 || response.status >= 300) {
    const error = validate<ApiError>('urn:medical:shared#/$defs/Error', response.data)
    parseErrorCode(error.code)
    if (response.headers['x-request-id'] !== error.request_id) fail('SCHEMA_INVALID', '错误响应请求身份不一致')
    if (error.resource_revision !== null) revision(error.resource_revision)
    fail(error.code, error.message, error.request_id)
  }
  const value = validate<Envelope>('urn:medical:shared#/$defs/Envelope', response.data)
  if (response.headers['x-request-id'] !== value.request_id) fail('SCHEMA_INVALID', '响应请求身份不一致')
  if (value.resource_revision !== null) revision(value.resource_revision)
  return value
}

export function validContext(context: Context): void {
  revision(context.context_revision); revision(context.auth_epoch); revision(context.governance_epoch)
}

export function validSession(value: MedicalSession, resourceRevision: string | null): void {
  const tenants = new Set<string>()
  for (const member of value.memberships) {
    revision(member.revision)
    if (tenants.has(member.tenant_id) || member.valid_to !== null && Date.parse(member.valid_to) <= Date.parse(member.valid_from)) {
      fail('SCHEMA_INVALID', '企业成员关联或有效期限无效')
    }
    tenants.add(member.tenant_id)
  }
  const context = value.current_context
  if (context === null) {
    if (resourceRevision !== null || value.action_summary.length !== 0) fail('SCHEMA_INVALID', '未选择企业的授权状态无效')
    return
  }
  validContext(context)
  if (resourceRevision !== context.context_revision || !value.memberships.some(member => member.tenant_id === context.tenant_id && member.status === 'ACTIVE')) {
    fail('SCHEMA_INVALID', '当前企业与成员身份不一致')
  }
}
