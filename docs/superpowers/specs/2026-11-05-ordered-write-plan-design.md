# Ordered write plans

## Intent

An assistant request may contain several writes in a specified order. The runtime must preserve the whole request across chat approvals and execute only the current write. This applies to any authorized domain write tools, not a hard-coded publish/create pair.

## Design

Store a plan in the existing `ai_task` table with type `ORDERED_WRITES`, an ordered list of tool names in `goal_json`, and the current index plus completed results in `state_json`. Create a plan when tool selection identifies at least two write operations. Allow repeated tool names for requests such as creating two records. The model selects the order from the user's request; the runtime validates every tool name and role before persistence. If selection is uncertain, do not invent steps.

Only the active plan step may be offered as a write callback. Read tools remain available for resolving arguments. `search_tools` can discover reads and the active writer, but cannot activate a later writer. The pending action links to the task through `task_id`. A confirmed, successful write advances the index; rejected or failed writes do not. Completion closes the plan. A new unrelated request supersedes the active plan; ordinary clarification and approval replies continue it.

The original request and completed action results are context for preparing later arguments; a later write is never executed with invented IDs. The existing per-write approval mechanism remains authoritative. The runtime stops repeated searches when a found tool is ineligible for the current step.

## Alternatives

- Remove the creation-target filter for mixed requests: small change but loses ordering and approval continuation.
- Persist an ordered plan in `ai_task`: chosen because it survives runs and permits any authorized write sequence.

## Verification

Cover mixed publish/create, another pair of writes, repeated tool names, approval and rejection, failed execution, unrelated new request, and repeated search. Confirm single-write requests retain their current behavior.
