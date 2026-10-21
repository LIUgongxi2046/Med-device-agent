# Template application compatibility repair

Implementation only; regression assertions are deferred, not executed. No database, migrations, services, providers or network were run.

The published v1 catalogue and template-applicability schema are restored byte-for-byte from the verified original source archive. Their SHA-256 values are respectively `dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915` and `0c304401621f198af342a823fa51b826a101b2e07ff616d09d3a1f190b01f498`. Historical extension pins are not changed.

`contracts/extensions/template-application.catalogue.json` publishes `urn:medical:template-application:1.0.0`, with SHA-256 `3a6235b4d98d13f7156389cf1538ff8cd23e898d1db8aa3fec18c04d72234525`. Extension digest is `dca6a68792e6f93fbcb476faacb55df75acf1a51e67eb55e52478a4c27134a1c`.

The fixed application-view and application-decisions controller paths remain dedicated endpoints. Their service and history reader validate FixedQuery, FixedView, HumanDecisionInput and HumanDecisionReceipt against the additive publication only. OpenAPI points to the new file. Java/Python/TypeScript catalogue loaders pin the extension; the Java resources package, professional executor loader and web validator register it.

The legacy applicable-templates endpoint emits the original five-field projection. Internal TemplateView retains machine_conditions and human_decisions, and task admission/planning/field authority consumers continue to use that complete view. UNKNOWN is never defaulted to applicable. Decision baseline hashing still uses the complete machine view, so moving publication does not rewrite the proof content or revive stale choices.

Existing B27 historical receipts, heads and idempotency records may carry exact former schema digest `06f3f94b5108b33d43b412a86417364d1529ba5d62405a0fa2112e2e68102624`. That digest is an explicit read-only compatibility allowlist alongside the new digest. All receipt structures are validated against the same unchanged decision definitions under their new namespace, and the existing tenant/profile/template/revision/content/baseline/author/classification checks remain required. Existing receipt and audit lookup uses the recorded manifest; new writes use only the new manifest. No history is rehashed or mutated.

Deferred coverage: Python immutable hashes, namespace isolation, pinned extension mutation, explicit decisions/nonapproval; Java legacy wire projection vs retained internal proof, baseline stability, exact historical digest acceptance, existing stale decision/UNKNOWN tests. Java compilation and integration acceptance remain NOT_RUN. This repair is not a new task-count milestone.

The not-yet-deployed evaluation-stage-input extension had been authored against the mistaken B27 base. Only its base pin was corrected to the restored original base, changing its extension digest from `8d1eec7fec1762e797085abd82efde1dbfd7da5285dfe5c067b6f6853331cb70` to `37218c998d05b9f8001b36edfa385dfae41598832cb297ec4e1865a3d6580d8a`; its two schema file bytes and schema digests remain unchanged. Java was its sole registered loader and its pin was updated.
