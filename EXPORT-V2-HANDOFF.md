# Export v2 source closure (B26/A11 compatibility slice)

Implemented source-only 2026-10-21. No task count change.

- ExportExecutionService now obtains DocumentReadService.FixedRead (complete source-aware read), matches body artifact/digest/bytes, retains the proof, and rechecks it at execution/object-write/completion boundaries.
- Explicit command selection comes from the actual registered body schema ref and digest. Known v1 remains unchanged JSON; known v2 uses the additive pinned document-export-command-v2 root schema. Unknown/mismatched publications fail closed.
- V2 command canonical bytes are read-only and SHA-256 fenced before and after execution. A fresh per-invocation HMAC authenticates those bytes via the private subprocess stdin pipe. The key is never part of JSON, argv, environment, persisted output, or public API. This is internal process authentication, not a new clinical signature or durable signing certificate.
- Python selects DocumentEvidenceBindings only from authenticated v2 command authority, never the body's version field. Schema ref/digest, body digest, and attempt identity bind rendering; v2 identities also enter plan/input hashes. DOCX, PDF, and Markdown retain actual renderer checks.
- Backend checks returned command/body/schema identity for v2, output/validation bytes, exact unique body-node set, and existing converter/font/layout checks. Download additionally performs fixed source-aware read/freshness checks; B10 current same-version signature and B29 release gates remain intact.
- Existing export item lease recovery and retry behavior stay unchanged: original item is recovered; successful items are not duplicated. Command working files remain ephemeral, as before; a recovered lease gets a fresh authenticated execution of the same immutable document/item, not a newly created successful-format item.

Modified files: ExportService.java, ExportExecutionService.java, ExportSchema.java; document export.py, export_command.py; new export_contract.py, document-export-command-v2.schema.json, tests/test_document_export_contract_v2.py. Catalogue registration uses the document-operations-v2 extension.

Validation: Python production/deferred-test files AST-parsed only (not imported/executed). Six boundary tests were authored but NOT_RUN. No tests, Java build, renderers, Word/LibreOffice/Docling, providers, services, migrations, or network operations were run. Java compile/build remains NOT_RUN; end-to-end release still requires configured fonts/converter, real registered inputs, current signature and B29 human/channel verification. Visual/channel checks remain NOT_RUN/UNKNOWN, never fabricated.

Historical testing note: NOT_RUN statements above describe the status when this handoff was written. For current verification results and remaining limits, see docs/development/DEVELOPMENT-STATUS-20261021.md.
