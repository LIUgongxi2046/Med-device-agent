# B26 complex source parsing

## Implementation and boundaries

The existing scan, source licence, actor/project authorization, lease, object-write recovery, immutable original/parse versions, and publication checks remain the entry points. The parser now supports a separately configured offline Docling 2.73.1 layout/table pipeline with a SHA-256-pinned local Tesseract binary and inventoried model/language assets. Remote services/plugins/downloads are disabled. This change does not install engines, approve binaries, acquire models, publish configurations, grant rights, or transmit documents to a third party.

Supported inputs: existing seven formats; scanned/mixed PDFs; embedded DOCX/PPTX images; PNG/JPEG and single-frame TIFF originals; recursively nested DOCX tables. Multi-frame TIFF is explicitly rejected rather than silently parsing only frame one. Charts without an implemented interpretation path remain verification issues.

## Configuration authoring and deployment

1. Operators separately approve/provision the local Docling dependencies, Tesseract executable, language data and Docling model directory. This work did not perform provisioning.
2. Under that approved environment, scripts/prepare_complex_source_release.py accepts explicit --tesseract, --artifacts, --tessdata, repeated --language and --output paths. It inventories/hashes existing files and reads package versions only; it never runs OCR. Output is an exclusive mode-0600 canonical JSON release and its hash. It refuses overlapping directories, missing language assets and version/hash changes.
3. Add complex-release-path and complex-release-sha256 to the appropriate medical.source-processing.bindings entry. Generate/publish the normal source-processing configuration with its actual tool scope and artifact digest. The release digest participates in both; seven-format legacy bindings retain their original scope. New complex bindings advertise IMAGE as well. Existing permission/approval publication is still mandatory.
4. Refresh the configured source-files inventory to include complex_parse.py and image_locator.py. Deploy the matching API schema/resources and UI together. There is no new database migration.
5. Normal upload, scanning, rights review and parse/reprocess commands feed the existing worker. No special bypass or direct execution endpoint is added.

## Provenance and consumers

Original uploaded bytes retain their SHA-256 and source_version. Parsed nodes/chunks retain parse_version-specific deterministic UUIDs, exact recognized text hashes and original page/image coordinates. PDF table cells retain table references, row/column ranges and spans. Nested DOCX cell addresses retain structural paths and original XML hashes. Embedded image nodes retain image byte digests and the containing original DOCX/PPTX locator. IMAGE locators carry the original image SHA-256, dimensions and normalized original coordinates; the local temporary PDF representation is never presented as a PDF original.

The IMAGE locator is an independent, versioned source-image-locator schema with pinned Java validation and Python/TypeScript consumers. Published dataset v1 and its catalogue digests are untouched. Source original/parsed readers, parsed-object verification, node/chunk materialization, source indexing, exact retrieval and retrieval UI accept this extension. Table/image structure and engine digest are materialized in content_ref and readable in the parsed-content view. PNG/JPEG originals render locally; TIFF remains downloadable with an explicit preview limitation.

Formal evidence uses the additive source-evidence-v2/tool-inputs-v2/tool-success-v2/task-tool-read-success-v2/artifacts-v2 catalogue extension. V1 schemas and hashes are unchanged and still reject IMAGE/HTML. The normal role/tool authoring must select and approve those exact publications and digests. TASK N02 and EVAL N02 readers retain physical original/parsed proofs, exact locator/text hash, source rights and immutable receipts. Registered artifact declaration, not payload inspection, selects the artifact validator. EVAL N02 uses a separate v2 business-output envelope; deterministic and unrelated old envelopes remain v1.

Professional executor config evidenceToolContracts explicitly maps AT-01 and AT-02 to 2.0.0 (EVAL may also select AT-08). Omit a key to retain v1. The config digest and observed tool parameter hashes participate in the normal deployment inventory; this option does not approve any tool or role. TASK AT-08 retains its independent registered v1 contract. Model parameters, request validation, bridge authorization and response validation all use the same selected publication. Unknown/non-role tools and mismatched responses fail closed.

N04 runtime now derives a TaskWriterEvidenceContract from the exact approved role output SectionDraft publication. It selects separately published v2 prompt/role/profile packages, validates the registered baseline body declaration, normalizes against that exact body/SectionDraft contract, and writes candidate/body objects with their actual schema reference and digest. Saved AT-06 submission and original recovery compare those declarations with the original input role and physical ledger metadata. A v1 baseline is not relabelled as v2, and IMAGE evidence does not authorize generating an arbitrary image attachment.

Implementation is under source-level integration review following workspace recovery. Do not count B26 complete until initial-body/save/read, N05/N07 review and N03 numeric source-evidence paths are reconciled with their explicit approved contracts. No automatic stored-v1-to-v2 upgrade is provided.

## Failure and recovery

Missing OCR configuration leaves PDF OCR_REQUIRED/image verification issues; standalone IMAGE cannot succeed without an engine. Missing/changed assets, malformed release, runtime/package changes, non-success conversion, missing locations and resource limits cannot fabricate text or receipts. Unknown locations produce issues and partial status when valid nodes exist; empty extraction fails. Engine exceptions produce the existing PROCESSOR_EXIT failed attempt and failure manifest. The normal bounded lease and object-write reconciliation remain in charge; a deliberate new parse version is required for a later configured retry. Recognition accuracy is UNKNOWN and human verification NOT_PERFORMED, not inferred from successful processing.

## Verification status

Python AST and JSON syntax inspection completed. javac is absent. Tests, compilation/full builds, services, migrations, installs, network/provider calls, actual OCR, actual scanner runs and pushes: NOT_RUN as requested. Deferred unit coverage: tests/test_complex_source_parsing.py. No synthetic test fixture is production OCR evidence.

Before runtime acceptance, execute the deferred tests and real approved-corpus checks: scanned and mixed PDFs, merged/multi-page tables, image rotations/crop coordinates, non-Latin language assets, PNG/JPEG/TIFF, nested DOCX/merged cells, embedded pictures, engine/model mutation, revoked rights during OCR, failed/empty extraction, original/parsed exact reads, index/retrieval, stale lease and uncertain object-write recovery. Confirm pinned Docling/Tesseract API compatibility and resource budgets in the approved deployment. Until then actual OCR integration verification remains NOT_RUN.

## Additive deterministic N03 evidence path (source implementation, 2026-10-21)

Explicit v2 context/decimal/typed-target authoring uses `/api/v2/projects/...` and rejects header/body version disagreement. Existing v1 routes remain v1. Actual source objects and parsed bundles are read and hash-checked before numeric/context spans are recorded. Context decisions remain independently approved; schema selection does not grant an approval. The target must match the approved role's exact v2 fixed-input and FieldCheckResult output publications. Typed target manifests, input metadata, comparison requests/results and registered output bindings preserve their actual version. Saved output recovery rechecks original role pins, intent declaration and registered schema ref/digest before N04 consumes it. Legacy outputs are not upgraded from payload content.

Deferred tests were written in `TaskStrictFieldEvidenceVersionTest`; they have NOT_RUN. No Java compilation, tests, OCR/provider execution, services or migrations were run. Full B26 completion still depends on shared document/review/EVAL integration review; this note is not a runtime acceptance claim.

## EVAL case and deterministic publication closure

The additive Case2 author/register/approve/read path is exposed separately under `/api/v2/formal-evaluation-case-originals`. Physical case writes and subsequent registration use the declared Case2 publication; registration and independent approval metadata retain version2 and its pinned digest. Original readers require the stored declaration and reject a v1 artifact carrying v2 content. The approved context assembler input and approved role output must match the selected case generation. Prompt/tool package bytes remain their independently declared v1 contracts where they contain no source-evidence extension.

Deterministic N03/N06 execution now obtains its exact output definition and digest from the approved original role at both execution and recovery. Business output physical objects preserve that selected envelope publication rather than defaulting to v1 or choosing a validator from an untrusted stored body. Saved/cross-file document evidence records actual body schema declarations from the physical read and rechecks current registered metadata. Deferred `FormalEvaluationEvidenceVersionTest` is written, NOT_RUN.

## Final source-only assessment (2026-10-21 02:21 UTC)

The previously listed TASK document/review and EVAL propagation dependencies have now been implemented in the shared source tree. No known core source-path gap remains in this implementation review. This is code-path completion only, not operational acceptance: Java/TypeScript compilation, test suites, database migrations, actual OCR and provider execution remain NOT_RUN. The configured offline OCR packages/assets still must be supplied and approved through the normal deployment procedure before actual parsing can succeed; missing or altered pins fail closed. Unsupported multiframe image inputs and unmappable structures remain explicit rejections or incomplete results, never invented source text.
