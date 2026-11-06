# AI agent tool expansion

## Scope

Expose existing Course, Submission and Result service operations to the AI assistant for lecturers and students. Class creation is excluded. In the UI, a “câu kiểm tra” is a `TestPlan`; its checks are `TestStep` records. It is not a multiple-choice quiz.

## Trust and execution

- Capture the authenticated principal's ID, email and exact authorities when a run is queued. The asynchronous worker must never invent roles. A missing role snapshot fails closed.
- Read tools use the caller's ID and the role-specific public API. Never use internal unscoped result APIs for a student.
- A model can propose a domain write, but cannot execute it. Store the tool name, exact JSON arguments, owner (through the run's conversation), run and expiration as a pending action. Return the pending action ID to the UI. A separate authenticated confirmation endpoint verifies ownership, role, expiration and the saved payload, reserves the action as `EXECUTING`, then executes once. Rejection cancels the action. Conversation text is not confirmation.
- Write results are saved with the pending action and must contain evidence from the owning service. A timeout or ambiguous response is recorded as `UNKNOWN`, never as success; it is not automatically retried. Destructive operations require a fresh confirmation. Keep requests bounded and audit the action.
- A full question with multiple steps uses `POST /plans/with-steps`, implemented as one Course Service transaction. Separate plan and step tools remain available for editing.

## Tool inventory

| Audience | Read | Write (confirmation required) |
| --- | --- | --- |
| Lecturer | list/get plans and steps, assignment results, class transcript, student scores, score components | create question with steps; create/update/delete plan; create/update/delete step; update/publish assignment; archive class; set score components; set student scores |
| Student | list own submissions, visible plans of a published assignment, own class scores, own class details and roster | none |

Existing `createAssignment` joins the same confirmation policy. `createClass` is intentionally omitted.

## API mapping

- Plans and steps: `/api/v1/assignments/{assignmentId}/plans` and nested `/steps` in Course Service. The student reads `/api/v1/student/assignments/{id}/plans`, which removes hidden fields.
- Lecturer results: `/api/v1/assignments/{id}/results`; scores and transcript: `/api/v1/classes/{id}/...`.
- Student submissions: `/api/v1/submissions`; own scores and roster: `/api/v1/student/classes/{id}/...`.
- Assignment update/publish and class archive use their owner-scoped Course Service routes.

## Follow-up API improvement

The aggregate endpoint validates each step through `StepConfigValidator` and rolls back the plan if a step is invalid. A stable operation key across service boundaries remains useful for ambiguous network failures; pending actions are single-use and a failed request is not automatically retried.
