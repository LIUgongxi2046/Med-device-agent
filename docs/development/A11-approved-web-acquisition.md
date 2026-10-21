# A11 / AT-09 approved web acquisition

Implementation only. Tests, compilation, migrations, service startup and network/provider calls have NOT been run. Migration 163 must be registered by the root coordinator.

## Normal producer and consumer

1. POST `/api/web-query-revisions` with schema 1.0.0, project/question UUIDs, literal query, exact lowercase domains (no wildcards), maximum_results 1–10, approved credential reference/revision, purpose `WEB_ACQUISITION`, expires_at and classification. Current project `source.write` permission is required. GET returns the frozen definition, its hash, fixed Brave endpoint and exact outbound query, including server-added site filters.
2. POST `/api/web-query-revisions/{revision}/decisions` with decision APPROVED, exact definition_sha256, expected_revision and reason. Requires current project `source.approve` authority for WEB_ACQUISITION. Approval explicitly authorizes query transmission to Brave and original GETs on only those exact domains. The same route revokes an approved revision. Edits require a new draft/review; revoked revisions cannot revive.
3. POST `/api/web-query-revisions/{revision}/acquisitions` with no body and a stable Idempotency-Key. The service rechecks approval, live approver membership/binding/grant revision, active project, expiry, credential-manifest approval and caller grant immediately before every send/read segment. AT-09 also runs this real producer from the existing formal runtime bridge, with the actual C07 tool call, NetworkResearcher role, N02/question binding, current published tool/role versions and exact action/lease proof. DTO tool names or caller-supplied receipt UUIDs cannot grant execution.
4. GET `/api/web-acquisitions/{id}` returns status and candidate IDs. `/originals` returns original response bodies (base64), exact URLs, HTTP statuses/headers, observation times, SHA-256 and complete/incomplete flags. Reads are current tenant/project source.read gated. Returned external text is untrusted input, never an instruction.
5. Captured PDF, plain text and HTML originals support normal source import. JSON remains a readable quarantined candidate with source_import_supported=false / SOURCE_FORMAT_NOT_SUPPORTED; bytes are never relabeled. POST `/api/web-candidates/{id}/source-import` with a real existing SourceUpload Create declaration. The original URL, captured timestamp, acquired_by, `APPROVED_WEB_CAPTURE` method, original hash/size/media and project must match capture exactly. User supplies real owner/publisher/rights evidence/retention; no license is inferred. Import uses normal upload declaration, exact captured part transfer, normal completion queue and source governance. Rights declaration is NOT a rights decision. Upload completion, parser/index completion and the normal source-rights/source-approval APIs remain authoritative. Source read/upload status APIs provide downstream lifecycle, not a second web-specific approval system.

All public routes require the existing authenticated session plus single X-Schema-Version/X-Context-Revision headers; mutations also require Idempotency-Key. Bodies are bounded and duplicate JSON fields rejected. No list/search/provider proxy endpoint is exposed.

## Deployment

`medical.approved-web.enabled=true`, private absolute journalDirectory (0700), approvedCredentialManifest and routes are required. Each route fixes tenant, credentialReference, credentialRevision and a private keyFile (0600). Credential directory must approve provider BRAVE_SEARCH and endpoint exactly `https://api.search.brave.com/res/v1/web/search`. No credentials, approvals, default domains or deployment receipts are fabricated by code. Missing inputs return DEPENDENCY_UNAVAILABLE.

Transport uses Brave's web search endpoint and server-side `site:` filters. Only actual result URLs whose HTTPS host exactly matches approved domains can be captured. HTTPS certificate/hostname validation is retained; DNS is checked for private/reserved addresses and pinned into Apache HttpClient, avoiding validation/connect rebinding. HTTP, userinfo, alternate ports, fragments, wildcard/subdomain escapes, redirects, automatic retries, inherited proxy settings, cookies and decompression are not used. Header count/line lengths, response size, socket/connect/deadline duration and result count are bounded.

## Original recovery and failure

Each search/capture has an fsynced CREATE_NEW started marker before external I/O, immutable identity, atomic original-response journal and process lock. An uncertain send is never repeated. Same acquisition/call ID reuses originals; a started marker without original returns UNKNOWN. `/acquisition-recovery` uses the original Idempotency-Key and reads originals only, never sends new requests. Readable failed/non-200/partial responses are retained; partial capture never yields an AT-09 success receipt. A new acquisition ID is a new explicitly authorized operation, not recovery. Normal successful AT-09 history replays only candidate receipt metadata, not raw external text into a model or source evidence. Current history transmission rechecks query approval; historical integrity verification does not grant new execution.

## Deferred verification (not run)

- Compile production Java and execute ApprovedWebGatewayBoundaryTest.
- MySQL migration/tenant-FK/immutable-trigger checks; all insert metadata and SQL parameter count checks.
- Authenticated author/read/approve/revoke, approver grant/member/binding revocation, expired approval, disabled project and tenant-crossing rejection.
- Actual configured Brave response contract, TLS/DNS pinning with mixed/public/private DNS, redirect no-follow, oversized/chunked/truncated body, slow response and token secrecy. Use isolated authorized fixtures, not unsolicited public requests.
- Kill after started marker, after original fsync, after DB commit, after part write, after completion enqueue; verify exact recovery without resend or duplicate import.
- Genuine model tool-call/session/role/action consumer, immutable registered-tool receipt and both ordered/current and closed-original history consumers.
- PDF/text/HTML imports through real storage/scan/parser/rights/approval. Search summaries, incomplete capture and pending-rights candidates must never satisfy SourceReadAuthority or formal evidence.

## HTML producer and publication

The HTML path preserves exact raw bytes and their SHA-256 through ordinary upload/storage/scan. `htmlEnabled=true` on the source-processing binding explicitly adds HTML and `HTML-STATIC-TEXT-1` to the published tool scope and artifact digest; old seven-format scopes remain unchanged. Republish the exact processing configuration and pin every source file, including html_native.py and shared v2 contracts, before use. No deployment configuration was fabricated.

The bounded offline stdlib parser produces deterministic static text, excluding script/style/template and statically hidden content, without executing scripts/CSS or fetching assets. Locators use the additive v2 evidence contract, raw-original SHA, absolute structural tag/index paths and Unicode-codepoint offsets in each element's retained descendant text. This is a versioned static projection, not a rendered-browser DOM claim. Unsupported encodings, oversized structure/text and text outside elements fail closed. Plain HTML is not mislabeled TXT. The original remains available alongside the derived nodes and parser provenance. Published parser and downstream v2 schema reference/hash checks remain mandatory.

AT-09 alone has a 360-second private bridge/executor budget for bounded sequential search plus captures; other tool timeouts remain unchanged. A committed aggregate is replayed before any network adapter call, including failed/partial originals. Fsynced pending original files are verified and promoted without a resend. Completion requests are frozen before normal upload queue submission.

Deferred HTML tests are in document/tests/test_html_native_deferred.py; they have not been run. Compilation, integration and real provider validation remain outstanding.

## Saved-document N07 evidence publication boundary

N07 selects its evidence contract only from the current approved role's exact registered ReviewFindings output schema and digest. The same selection fixes all three physically read N07 package publications, the actual saved DocumentBody registration, model output normalization and the complete findings artifact publication. A v1 role cannot accept v2 packages, bodies or output; a v2 role requires document-review-package-v2, document-body-v2 and artifacts-v2 with their registered hashes. No evidence-content sniffing upgrades a stored v1 object.

Private invocation compilation pins and immutable result provenance retain the chosen v2 refs/hashes. Current model/terminal checks and original factual recovery recheck those pins. The object ledger registers/replays the matching output binding. ProfessionalCheckService reads and validates the actual registered findings ref/digest and reports that same ref, rather than labeling v2 bytes as v1. Technical delivery and professional findings still do not create source rights or human approval.

DocumentReviewEvidenceContractDeferredTest was added but not run. End-to-end review still requires genuinely approved v2 role/package/body artifacts and deployment publication; none were fabricated.
