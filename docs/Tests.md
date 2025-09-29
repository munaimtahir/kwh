# Tests.md — Test Plan

## Unit
- **DAO tests:** Insert/update/delete meters; insert readings; cascade delete; latest reading query.
- **ViewModel:** Add meter validation; add reading; reminder toggle; error flows.
- **Reminder timing utils:** Next-run calculation across DST/timezone changes.

## UI (Compose)
- Add meter dialog flow; reading capture flow; delete with undo; empty state rendering; history list render.

## Integration
- WorkManager enqueue/cancel per meter; verify unique work and reschedule.
- Snooze broadcast schedules WorkManager delay respecting settings.

## Acceptance Checklist
- [x] First-run: no meters → empty state visible.
- [x] Create meter, add reading → latest shows on card.
- [x] Toggle reminder → permission ask on API 33+; schedule respected.
- [x] Delete meter → card disappears; readings removed.
