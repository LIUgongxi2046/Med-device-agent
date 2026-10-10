import type { SchemaCatalogue } from './catalogue.js'
import type * as Protocol from './interfaces.js'
import { COMMON_DEFINITIONS, type CommonDefinitions } from './interfaces-common.js'
import { decimalValue, unsignedRevision, utcTime, uuidValue } from './wire.js'

export interface InterfaceDefinitions extends CommonDefinitions {
  Envelope: Protocol.Envelope; Error: Protocol.Error; ErrorDetail: Protocol.ErrorDetail; Operation: Protocol.Operation; PageInfo: Protocol.PageInfo; Record: Protocol.Record; RecordPage: Protocol.RecordPage
  Node: Protocol.Node; ContainerContent: Protocol.ContainerContent; TextContent: Protocol.TextContent; ImageContent: Protocol.ImageContent; TableContent: Protocol.TableContent; Cell: Protocol.Cell; FormulaContent: Protocol.FormulaContent
  FieldValue: Protocol.FieldValue; EvidenceLink: Protocol.EvidenceLink; FactUsage: Protocol.FactUsage; DocumentBody: Protocol.DocumentBody; DocumentFieldProjection: Protocol.DocumentFieldProjection; DocumentEvidenceProjection: Protocol.DocumentEvidenceProjection; BodyPage: Protocol.BodyPage; DocumentSnapshot: Protocol.DocumentSnapshot; RevisionInput: Protocol.RevisionInput
  UploadRequest: Protocol.UploadRequest; UploadGrant: Protocol.UploadGrant; UploadCompletion: Protocol.UploadCompletion; ArtifactReceipt: Protocol.ArtifactReceipt; CompareRequest: Protocol.CompareRequest; CandidateCompareRequest: Protocol.CandidateCompareRequest; Comparison: Protocol.Comparison; CandidateView: Protocol.CandidateView
  ReviewBaseline: Protocol.ReviewBaseline; EvidenceDecisionBatch: Protocol.EvidenceDecisionBatch; ReviewTarget: Protocol.ReviewTarget; ReviewBatch: Protocol.ReviewBatch; DecisionBatch: Protocol.DecisionBatch; AnnotationInput: Protocol.AnnotationInput
  ReauthRequest: Protocol.ReauthRequest; ReauthChallenge: Protocol.ReauthChallenge; ReauthCompletion: Protocol.ReauthCompletion; ReauthReceipt: Protocol.ReauthReceipt; CancelMessage: Protocol.CancelMessage; ConversationSnapshot: Protocol.ConversationSnapshot
  CredentialWrite: Protocol.CredentialWrite; CredentialReceipt: Protocol.CredentialReceipt; VerificationView: Protocol.VerificationView; ManagementBindings: Protocol.ManagementBindings; ManagementModule: Protocol.ManagementModule; ManagementOperation: Protocol.ManagementOperation
  ReportFilters: Protocol.ReportFilters; ReportExportRequest: Protocol.ReportExportRequest; ReportExportView: Protocol.ReportExportView; OperationLookup: Protocol.OperationLookup; OperationLookupResult: Protocol.OperationLookupResult; ReconciliationRequest: Protocol.ReconciliationRequest
  Stream: Protocol.Stream; StreamCursor: Protocol.StreamCursor; StatePayload: Protocol.StatePayload; WaitingPayload: Protocol.WaitingPayload; DocumentPayload: Protocol.DocumentPayload; InvalidationPayload: Protocol.InvalidationPayload; GovernancePayload: Protocol.GovernancePayload; PublicEvent: Protocol.PublicEvent; CandidateDelta: Protocol.CandidateDelta
  StepIdentity: Protocol.StepIdentity; ActionIdentity: Protocol.ActionIdentity; InputReadRequest: Protocol.InputReadRequest; ToolRead: Protocol.ToolRead; PlanCandidateRequest: Protocol.PlanCandidateRequest; CandidateReceipt: Protocol.CandidateReceipt; WaitingRequest: Protocol.WaitingRequest; WaitingReceipt: Protocol.WaitingReceipt
  CheckResultRequest: Protocol.CheckResultRequest; CheckReceipt: Protocol.CheckReceipt; WebAcquisitionRequest: Protocol.WebAcquisitionRequest; ContextRequest: Protocol.ContextRequest; ContextReceipt: Protocol.ContextReceipt; ObjectRef: Protocol.ObjectRef; AgentArtifactRequest: Protocol.AgentArtifactRequest
  InvocationReadRequest: Protocol.InvocationReadRequest; ContentChunk: Protocol.ContentChunk; InternalUploadRequest: Protocol.InternalUploadRequest; InternalUploadCompletion: Protocol.InternalUploadCompletion; InternalSearchRequest: Protocol.InternalSearchRequest; InternalSearchResult: Protocol.InternalSearchResult; InternalSourceReadRequest: Protocol.InternalSourceReadRequest; InternalSourceReadResult: Protocol.InternalSourceReadResult
}
export const INTERFACE_DEFINITIONS = [
  ...COMMON_DEFINITIONS,
  'Envelope', 'Error', 'ErrorDetail', 'Operation', 'PageInfo', 'Record', 'RecordPage', 'Node', 'ContainerContent', 'TextContent', 'ImageContent', 'TableContent', 'Cell', 'FormulaContent', 'FieldValue', 'EvidenceLink', 'FactUsage', 'DocumentBody', 'DocumentFieldProjection', 'DocumentEvidenceProjection', 'BodyPage', 'DocumentSnapshot', 'RevisionInput',
  'UploadRequest', 'UploadGrant', 'UploadCompletion', 'ArtifactReceipt', 'CompareRequest', 'CandidateCompareRequest', 'Comparison', 'CandidateView', 'ReviewBaseline', 'EvidenceDecisionBatch', 'ReviewTarget', 'ReviewBatch', 'DecisionBatch', 'AnnotationInput', 'ReauthRequest', 'ReauthChallenge', 'ReauthCompletion', 'ReauthReceipt', 'CancelMessage', 'ConversationSnapshot',
  'CredentialWrite', 'CredentialReceipt', 'VerificationView', 'ManagementBindings', 'ManagementModule', 'ManagementOperation', 'ReportFilters', 'ReportExportRequest', 'ReportExportView', 'OperationLookup', 'OperationLookupResult', 'ReconciliationRequest', 'Stream', 'StreamCursor', 'StatePayload', 'WaitingPayload', 'DocumentPayload', 'InvalidationPayload', 'GovernancePayload', 'PublicEvent', 'CandidateDelta',
  'StepIdentity', 'ActionIdentity', 'InputReadRequest', 'ToolRead', 'PlanCandidateRequest', 'CandidateReceipt', 'WaitingRequest', 'WaitingReceipt', 'CheckResultRequest', 'CheckReceipt', 'WebAcquisitionRequest', 'ContextRequest', 'ContextReceipt', 'ObjectRef', 'AgentArtifactRequest', 'InvocationReadRequest', 'ContentChunk', 'InternalUploadRequest', 'InternalUploadCompletion', 'InternalSearchRequest', 'InternalSearchResult', 'InternalSourceReadRequest', 'InternalSourceReadResult',
] as const satisfies readonly (keyof InterfaceDefinitions)[]
export interface InterfaceBinding<K extends keyof InterfaceDefinitions> {
  readonly definition: K; readonly schemaRef: string; readonly schemaSha256: string; readonly canonicalJson: string
  read(): InterfaceDefinitions[K]
}
export function bindInterface<K extends keyof InterfaceDefinitions>(catalogue: SchemaCatalogue, definition: K, value: unknown): InterfaceBinding<K> {
  const revision = catalogue.schemaRef('interfaces', 'Revision')
  const measurement = `${catalogue.schemaRef('interfaces', 'FieldValue')}/properties/decimal_value`
  const saved = catalogue.bind('interfaces', value, definition, (location, child) => {
    if (child === null) return
    if (location.format === 'uuid') uuidValue(child)
    if (location.format === 'date-time') utcTime(child)
    if (location.uri === revision) unsignedRevision(child)
    if (location.uri === measurement) decimalValue(child, 38, 12)
  })
  return Object.freeze({ ...saved, definition, read: (): InterfaceDefinitions[K] => JSON.parse(saved.canonicalJson) as InterfaceDefinitions[K] })
}
