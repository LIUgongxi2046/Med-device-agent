# Review workspace development preview

This isolated entry mounts the actual `document-review-workspace.vue` using visibly labeled simulated document metadata. It does not import a session, backend transport, route guards, review write, signature, or identity receipt. It is not reachable from the production router.

Run from `web/`: `node node_modules/vite/bin/vite.js --config tests/review-workspace-preview/vite.config.mjs`; open port 18086 at `/tests/review-workspace-preview/index.html`.

Exercise known-blocker/unknown filtering, collapsed review/resubmission/signing panels, local opinion reset when the active document version changes, and simulated dirty/stale toggles. All consequential controls stay disabled. Navigation explains its destination without accessing production pages.

Focused tests from repository root: `node --test tests/frontend/document-review-workspace-checks.mjs`.

These are component/presentation tests, not authenticated browser integration or backend acceptance. The model uses existing DocumentSnapshot, ReviewBasis, and ReviewAssignmentView types. RV-07 review-decisions, RV-08 signing-readiness, RV-09 POST signatures and an integrated current-check verifier remain unavailable; no success or authority is inferred from record IDs.

The expanded local worksheet also supports whole-document/actual supplied saved-section selection, proposed CONFIRM/RETURN decisions, opinion/reason validation and an exact-version preview. Section revisions are not invented when absent from the source DTO. Resubmission notes and revision comparisons remain local. Signing preparation includes purpose validation, manifest review and explicitly labeled identity/confirmation/pending/error UI previews; no step selection represents real completion and no success state exists. Any document/version/watermark change clears local confirmation. Focused coverage now includes these interactive states.
