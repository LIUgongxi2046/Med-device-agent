export type Kind = 'PROMPT' | 'PROFILE' | 'ROLE'
export interface Basis { version_id: string; content_sha256: string }
export interface Artifact { artifact_id: string; sha256: string; size_bytes: string; media_type: string }
export interface Schema { schema_ref: string; schema_sha256: string }
export interface PromptPackage { schema_version: '1.0.0'; node_kind: 'N02'; system_instructions: string; input_encoding: 'TASK_NODE_INPUT_CANONICAL_JSON' }
export interface ProfilePackage { schema_version: '1.0.0'; node_kind: 'N02'; profile_name: 'medical-agent'; projection: 'TEXT_SYSTEM_USER_V1'; system_prefix: string; files: { relative: string; sha256: string; size_bytes: string }[]; adapter_artifact: Artifact }
export interface RolePackage { schema_version: '1.0.0'; node_kind: 'N02'; role_name: 'Researcher' | 'NetworkResearcher'; prompt_basis: Basis; profile_artifact: Artifact }
interface RequestBase { schema_version: '1.0.0'; preparation_sha256: string; content_sources: Basis[] }
export type Request = RequestBase & ({ kind: 'PROMPT'; package: PromptPackage } | { kind: 'PROFILE'; package: ProfilePackage } | { kind: 'ROLE'; package: RolePackage })
export type Prerequisite = 'MANAGED_ADAPTER_IMPORT_REQUIRED' | 'PHYSICAL_PROFILE_DEPLOYMENT_REQUIRED' | 'INDEPENDENT_CAPABILITY_AUTHORING_REQUIRED' | 'INDEPENDENT_RELEASE_APPROVAL_REQUIRED'
export interface Preparation {
  schema_version: '1.0.0'; project_id: string; context_revision: string; preparation_sha256: string; maximum_package_bytes: number; maximum_choices: number; recovery_mode: 'ORIGINAL_ONLY_MAY_REGISTER';
  kinds: { kind: Kind; schema: Schema }[];
  prompts: { version: Basis; config_id: string; status: string; source_artifact: Artifact; schema: Schema }[];
  profiles: { artifact: Artifact; schema: Schema }[];
  adapters: { artifact: Artifact; schema: Schema; component: 'C07'; candidates: { version: Basis; status: string }[] }[];
  sources: { version: Basis; rights: Basis; source_id: string; classification: string }[];
  prerequisites: Prerequisite[]
}
export interface Status { schema_version: '1.0.0'; state: 'NOT_FOUND' | 'PROCESSING' | 'REGISTERED'; operation_id: string | null; kind: Kind | null; artifact: Artifact | null; schema: Schema | null; object_version: string | null; production_eligible: false; tenant_id: string; project_id: string; created_by: string; operation_name: 'TASK-PROFESSIONAL-PACKAGE'; target_id: string | null; original_key_sha256: string | null; request_body_sha256: string | null; request_sha256: string | null }
