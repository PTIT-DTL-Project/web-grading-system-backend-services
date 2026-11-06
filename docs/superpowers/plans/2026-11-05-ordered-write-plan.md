# Ordered Write Plan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Preserve and execute arbitrary authorized write sequences across chat approvals.

**Architecture:** Persist ordered writer names and current index in `ai_task`, link each pending action to the task, and gate callbacks to the active writer. Continue after each successful confirmation.

**Tech Stack:** Java, Spring Boot, JPA, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-05-ordered-write-plan-design.md`

## Global Constraints

- One pending write at a time; every write requires its existing approval.
- Preserve user changes already in the working tree.
- Plan steps are role-authorized tool names in user-request order.

## Review Focus

- A repeated writer must remain a separate step.
- A failed or rejected approval must not advance the plan.
- A discovered later writer must not bypass the active step.
- An unrelated new request must not inherit an old plan.
- A single-write request must retain current behavior.

---

### Task 1: Plan persistence

**Files:** Create `service/OrderedWritePlanService.java`; modify `repository/TaskRepository.java`; test `service/OrderedWritePlanServiceTest.java`.

- [x] Write failing tests for ordered steps, repeated names, advancement, and completion.
- [x] Run targeted tests and confirm expected failures.
- [x] Implement plan storage and role-scoped retrieval with the existing `Task` entity.
- [x] Run targeted tests and confirm passes.

### Task 2: Approval linkage

**Files:** Modify `service/WriteApprovalService.java`; test `service/WriteApprovalServiceTest.java`.

- [x] Write failing tests that a proposal links to the active plan and successful confirmation advances exactly once.
- [x] Run tests and confirm expected failures.
- [x] Implement task linkage and advancement; leave failures and rejections at the same index.
- [x] Run tests and confirm passes.

### Task 3: Runtime gating and continuation

**Files:** Modify `orchestrator/AgentRuntime.java`, `service/LlmClient.java`, `tools/ToolSelector.java`; test `orchestrator/AgentRuntimeTest.java`.

- [x] Write failing tests for mixed writes, non-publish pair, repeated writer, active-step callback gating, and no search loop.
- [x] Run tests and confirm expected failures.
- [x] Gate writers by active plan step and continue after a confirmed write.
- [x] Run targeted tests and the AI assistant service test suite.

## Verification result

Targeted ordered-plan tests and package build passed. The full service suite ran 247 tests with two existing configuration expectation failures: `AgentPropertiesTest.testPropertiesBoundSuccessfully` and `ContextPreflightTest.messageEvictedAfterSummaryGrowthIsAlsoSummarized`. The suite excluding those two classes passed.
