# Strict field verification preview

This isolated preview mounts the production `document-field-verification.vue` component with clearly labelled simulated data. It has no backend/API client, save action, professional result receipt, or approval operation. Search, filtering and selection are real Vue interactions. Conflict is an existing document evidence relation, not an N03 result. Support does not imply verification success. Empty fields and absent evidence remain visible; formal confirmation is always unknown and disabled.

From `web/`:

- `node --test tests/field-verification/check.mjs`
- `node_modules/.bin/vue-tsc --noEmit -p tests/field-verification/tsconfig.json`
- `node tests/field-verification-preview/build-standalone.mjs`
- `node_modules/.bin/vite --config tests/field-verification-preview/vite.config.mjs`

The standalone builder outputs `/workspace/shared/field-verification-preview.html`; it inlines compiled Vue JavaScript and CSS rather than replacing the component with a static mockup. No browser screenshot or real service acceptance is implied by compilation.

Integration props: `body: DocumentBody`, optional `preparation: Preparation | null`, optional `canOpenEvidence: boolean`. Events: `select-field(fieldId)` and `open-evidence(EvidenceLink)`. Keep evidence reading disabled unless wired to the existing authenticated fixed-source reader. Do not use selection as a mutation. Body editing remains in the separate existing document field editor.

## Local handling draft

The inline draft supports confirm-intent, evidence-request intent, conflict notes and not-applicable intent without writing or asserting any business decision. A valid actual field or current proposal target and a nonblank reason are required; not-applicable intent also requires a local basis explanation. Input checks only validate form completeness, never professional correctness or authority. Preview includes the provided template/source identities and explicitly marks unavailable document revision, rules and approvals unknown. Editing invalidates preview; target/field/context changes clear the draft. Cancel and Escape close it and return keyboard focus. No persistence, API call or decision event is emitted.
