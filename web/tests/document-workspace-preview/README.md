# Document workspace UI preview

Development-only entry importing the actual `document-body-reader.vue` and its real node/text editors. No production router entry, authentication, service API, save request, approval, or signature is provided. All content is visibly labeled simulated. Edits are held only in memory; reload discards them.

From `web/`:

```sh
node node_modules/vite/bin/vite.js --config tests/document-workspace-preview/vite.config.mjs
```

Open `/tests/document-workspace-preview/index.html` on port 18085. Review at desktop and narrow viewport sizes. Check continuous reading, right-side outline, keyboard outline focus, edit/read switching, dirty label, read-only switch and collapsed auxiliary sections. This preview exercises the actual reader, not the complete authenticated document editor: saving, evidence access, version comparison and route guards require a live authorized integration environment.

Focused component logic tests, from repository root:

```sh
node --test web/tests/document-workspace/components.test.mjs
```

Tests compile the real reader/editor SFC scripts and templates and run their setup functions with reactive props and mocked lifecycle boundaries. They do not constitute browser, backend, visual, or end-to-end acceptance.

For a standalone HTML of the same actual Vue component (bundled JS/CSS and embedded fonts, no entry asset requests):

```sh
node tests/document-workspace-preview/build-standalone.mjs /workspace/shared/document-workspace-preview.html
```

The build uses a bounded 3072 MB Node heap. Open the resulting file in an existing browser. The HTML contains the full actual Vue application, not a static screenshot redraw. Browser execution and visual acceptance still need to be checked separately.
