import * as metadata from '@/api/medical/request-metadata'
import { schemaIssues } from '@/api/medical/protocol'

declare global { interface Window { metadataChecks: typeof metadata & { schemaIssues: typeof schemaIssues } } }
schemaIssues('urn:medical:request-metadata:1.0.0', {})
window.metadataChecks = { ...metadata, schemaIssues }
