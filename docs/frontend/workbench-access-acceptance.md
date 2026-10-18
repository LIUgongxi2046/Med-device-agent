# S01 workbench / S17 access-denied frontend acceptance

## Implemented

- S01 uses the existing `/api/projects` response directly, sorts the returned first batch and previews at most six projects. It does not claim this is a global ranking or fetch every project detail.
- Waiting items use `/api/work-items` with `assignee_id` set to the actual session user, `WAITING_INPUT`, and a limit of 50. Older waiting items remain visible. Next-page and reset controls reuse the actual returned cursor; counts are explicitly the same actor/status scope.
- Pending-item links carry waiting-item and run identifiers as query context into the authorized project task list. The current returned page is matched against `TaskDetail.waiting_item_ids` and `active_run_ids`, with direct waiting-item associations distinguished from active-run associations. Missing matches describe only the current page, never global absence; no automatic scans or navigation occur. No task identifier is inferred from `run_id` and no resolution is invented.
- Project/task links check the current session action scope. The task picker contains only returned projects with a current task-read action and a matching server `allowed_actions` entry. Selection fetches one page of five actual tasks, linking to existing task configuration/status routes.
- Independent project, waiting-item and scenario failures are surfaced without discarding successful sections. Unread totals show “未读取”; restricted child counts produce “部分不可读取”, never zero or a misleading partial aggregate.
- Session changes, page visibility loss and newer reads abort/retire old reads. Task preview results require the same session, selection and controller before rendering.
- S17 is separate from `/access` login. Integrated route (verified in the shared router): `/access/denied`, name `MedicalAccessDenied`, component `views/medical/access-denied.vue`, meta `{ medical: true, public: true, noCache: true, title: '访问受限' }`.
- Denial explanation has no automatic session/resource prefetch and ignores URL parameters. Its explicit controls call existing read-session and enterprise-selection actions. Only authenticated option entries can be selected; enterprise UUIDs are collapsed for verification because the current options API has no enterprise names. No protected resource names, counts, bodies or server error text are shown.
- The page does not clear pending business writes, grant access, or automatically redirect on API errors. The coordinating change has integrated the safe denial route and framework-navigation rejection routing. A denial must not be interpreted as an unauthenticated account or trigger a blanket redirect that drops in-progress forms.

## Focused verification

Run: `node --test web/tests/workbench-access/*.test.mjs`.

Twenty-one focused tests cover SFC script/template compilation, bounded stable project source handling, bigint/null count semantics, current-assignee query and bounded task request structure, denial-page safe-recovery boundaries, and executable component-setup race tests using deferred API promises. Race cases include immediate session revocation, new-actor cursor rejection, duplicate next-page events, partial failure/retry, rapid project selection, hidden-page clearing, and cached off-route inactivity. These are compilation/helper/component-setup tests, not mounted browser interaction evidence.

Browser preview remained unavailable (`ERR_CONNECTION_REFUSED`) at implementation time. The coordinating agent reported the complete medical frontend typecheck passing before this independent review. This review then fixed the return link to the actual `/index` route, added the separate `task.read` detail-link guard, blocked cached off-route reads, and bound queue cursors to their originating session and displayed page. The coordinator must rerun typecheck for these final changes; no production build is claimed here. Still requires browser acceptance against a running authorized backend for: switching session while reads are pending, partial failure and retry, next-page queue navigation, two quick project selections, access-denied recovery with and without a valid session, and enterprise selection while an earlier write is unresolved.

## Waiting-item task association increment (2026-10-18)

- `medical-task-context.ts` validates scalar query identifiers, rejects duplicated/invalid values, and never infers a task ID. Home navigation uses only the existing project-task route.
- `tasks.vue` annotates matching current-page records; direct waiting-item linkage takes precedence over active-run linkage. Task detail navigation still requires the independent `task.read` permission. No query parameter is sent as an unsupported task API filter.
- Explicit task pagination uses the existing authorization/filter watermark. Failed pages are cleared and show a read error; retry starts at page one. New session, route/query or visibility state discards old results. Leaving context removes only the two association query fields.
- Added eight focused tests, including compiled Vue setup runtime tests for matching, no-match, page changes, revocation, stale responses and failed-page retry. No browser or backend integration acceptance is claimed.
