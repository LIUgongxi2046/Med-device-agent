# Extension scenario frontend preview

This isolated preview mounts the actual `ScenarioPlanningWorkspace.vue` component with Element Plus. It makes no service calls and has no authenticated business data. The access switch is explicitly a preview-only simulation.

The component is also embedded in the existing production scenario catalogue page. Its five scenario names and purposes match PRD §8.7 and the existing `ScenarioCatalogueService.plannedRequirements()` labels. These are planning requirements, not registered configuration counts or executable contracts.

## Implemented interaction

- Five distinct planning cards, detail navigation and a three-step preparation flow.
- Eight PRD specification dimensions, all visibly awaiting confirmation.
- Empty records, local validation, independent per-scenario drafts, review/edit/reset.
- Local-only memory; no upload, model request, external send, storage, generated task ID or business output.
- Unavailable access clears drafts. The integrated parent also resets drafts on context changes and page hiding; ordinary project/document catalogue filters and repeated searches preserve these independent planning notes.
- Always-disabled formal task button with an explicit explanation.
- Responsive grid and Element Plus forms, steps, cards, descriptions and alerts.

## Verify

From `web`:

```
node --test tests/scenario-workspace-preview/scenario-workspace.test.mjs tests/scenario-project-scope/scenarios.test.mjs
./node_modules/.bin/vue-tsc --noEmit -p tests/scenario-workspace-preview/tsconfig.json
node tests/scenario-workspace-preview/build-standalone.mjs /workspace/shared/scenario-workspace-preview.html
```

The build emits `../dist-scenario-preview` outside the main application build, then inlines its actual compiled JavaScript and CSS into the requested standalone HTML file. Open that HTML file without a development server. No external entry assets remain; no backend or model is invoked. Tests cover actual SFC compilation and reactive interactions; browser visual verification is a separate check, not claimed by these tests.

## Preserved limitations

The existing read-only catalogue schema declares `definition_status: UNVERIFIED` and `task_entry: null`. The service does not supply an execution contract. No local input authorizes publishing, running, approving, or creating a formal scenario task. Input and review rules particular to each scenario remain unknown per PRD; preparation hints are not approved rules. Only the existing service determines authorized configuration versions and counts.
