# Postman — Full-Flow API Testing Guide

Step-by-step Postman instructions covering **every flow** of the system:
Classes → Students → Scores → Assignments → Plans & Steps → Student Submissions,
plus negative tests and optional DB verification.

> Canonical flows live in `docs/design/usecase-flows.md` (UC-01…03).
> Automated runner alternative: `docs/api/scenarios/*.sh`.

---

## 0. Environment setup (one time)

> **⚠️ Authentication changed (2026-09-30, Keycloak integration).** Every endpoint here that
> is not on a `permitAll` path now requires a gateway-issued identity, so this guide runs in
> one of two modes:
>
> | Mode | `baseUrl` | What you must send |
> |---|---|---|
> | **A — direct to a service** (default, needs no Keycloak) | `http://localhost:18081` port-forward of course-service — use 18082 for submission, 18084 for result; or the dev hostnames `https://web-dev1-course…`, `https://web-dev1-submission…`, `https://web-dev1-result…` | `X-User-Id` **and** `X-Gateway-Secret: {{serviceSecret}}`, plus `X-User-Roles: {{roles}}` wherever the endpoint is lecturer-only |
> | **B — through the api-gateway** (production-like) | `http://localhost:8080` or `https://web-dev1-api.vucongtuanduong.dpdns.org` | `Authorization: Bearer {{token}}` — the gateway injects `X-User-Id` from the token `sub`, allowlists `realm_access.roles` into `X-User-Roles`, and **strips** anything you send |
>
> A request reaching a service **without** the secret, or the gateway **without** a token, is
> answered `401` by the security entry point *before* it reaches a controller. Unauthenticated
> paths are unchanged: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`,
> `/api/v1/internal/**` and the `/api/v1/submissions/webhook/**` RustFS callback.
>
> **UI note:** the frontend still sends no bearer token, so every screen returns `401` until the
> Keycloak login flow ships. Use this guide for regression testing meanwhile.

Create a Postman Environment (`⚙ Environments → Create`) with:

| Variable | Initial value | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:18081` | mode A: service port when booted locally (or `https://web-dev1-course.vucongtuanduong.dpdns.org` — submission/result likewise); switch to the gateway host for mode B |
| `serviceSecret` | value of `GATEWAY_TRUSTED_SECRET` in the repo `.env` | mode A only — sent as `X-Gateway-Secret`; leave empty and everything returns `401` |
| `token` | empty | mode B only — Keycloak access token for `ptit-wgs`; enable the collection's `Authorization` header to use it |
| `roles` | `LECTURER` | sent as `X-User-Roles` by every request in the collection (mode A). Set to `STUDENT`, or delete the collection header, to exercise the student side |
| `ownerLecturer1` | any UUID, e.g. `2d93941a-4221-458b-a03d-43bd6315d02e` | main lecturer identity |
| `ownerLecturer2` | any other UUID | used to prove 404 ownership isolation |
| `fake_student_id`  | any UUID, e.g. `2d93941a-4221-458b-a03d-43bd6315d02e` | student identity (matches CSV import `student_user_id`) |
| `classId`, `assignmentId`, `planId`, `stepId`, `studentCode`, `submissionId` | empty | captured during the flow |

**Auto-capture ids** — in each create-request's *Tests* tab add:

```js
const j = pm.response.json();
if (j.data && j.data.id) pm.environment.set("classId", j.data.id);
```

(adjust the variable + json path per request).

**Identity rule (mode A — direct):** use `{{ownerLecturer1}}` in `X-User-Id` for the lecturer steps (§1–§4); §5 student steps use `{{fake_student_id}}`.
A different UUID on a later request = ownership miss → indistinguishable `404`.
Most identity endpoints default a missing header to `anonymous` → `400 invalid UUID`. The two submission endpoints have no default → `Missing required header: X-User-Id`. These `400`s describe the controller layer and only appear on the direct path — through the gateway a missing identity surfaces as `401` first.

**Identity rule (mode B — gateway):** identity is the token subject. `{{ownerLecturer1}}` / `{{fake_student_id}}` must equal the `sub` of the tokens you use, and any `X-User-Id` header you send is stripped and replaced by the gateway, so sending it changes nothing.

**Role rule (2026-09-30, slice 2):** everything that creates or edits grading data — creating/listing/archiving
classes, importing students, score components, entering scores, transcripts, assignments, plans & steps,
docker images, and `GET /api/v1/submissions/assignment/{assignmentId}` — now requires `LECTURER`. Any other
role, **or a request carrying no `X-User-Roles` at all**, gets `403` from method security; a request that
still has no trust secret gets `401` first. In mode A you send the role yourself, so it is worth exactly as
much as the secret next to it; in mode B the gateway derives it from the token's `realm_access.roles` and
forwards only what `gateway.security.allowed-roles` (default `LECTURER,STUDENT`) keeps.

Ownership, not role, still decides two reads: `GET /api/v1/submissions/{id}` answers `404` for a non-owner
indistinguishably from a missing id (a `LECTURER` reads any), and `GET /api/v1/results/{submissionId}`
answers `403` when the rows belong to someone else — unless the caller holds `LECTURER`, which is what lets
a lecturer grade.

---

## 1. Classes

### 1.1 Create class

```
POST {{baseUrl}}/api/v1/classes
X-User-Id: {{ownerLecturer1}}
{ "name": "PTIT CNTT-K68", "semester": "20261" }
```

Expected `201`: `data.id` (**saved as `classId`**), `published` n/a, `status:"ACTIVE"`.

### 1.2 Negative — duplicate title same class+semester

Same request again → `400`
`"Class 'PTIT CNTT-K68' already exists in semester 20261"`.

### 1.3 Import students from CSV

```
POST {{baseUrl}}/api/v1/classes/{{classId}}/students/import
X-User-Id: {{ownerLecturer1}}
Body → form-data → key: file (type: File) → docs/samples/students-import.csv
```

Expected `200`: `data:{imported:6, skipped:0}`, message "Students imported".
Re-run → all skipped. No Content-Type header manually (Postman sets multipart boundary).

Negatives: raw body instead of form-data → 400 "Malformed multipart request";
empty file → 400 "CSV file is empty"; non-.csv name → 400 "Only CSV files are accepted".

### 1.4 List students

```
GET {{baseUrl}}/api/v1/classes/{{classId}}/students?page=0&size=20
```

Expected: paged envelope, 6 students. Pick one `studentCode` (e.g. `B22DCCN001`)
→ save as `{{studentCode}}`.

---

## 2. Scores

### 2.1 Configure score components (must precede score entry)

```
PUT {{baseUrl}}/api/v1/classes/{{classId}}/score-components
[
  { "type": "ATTENDANCE", "weight": 0.10 },
  { "type": "EXERCISE",   "weight": 0.20 },
  { "type": "FINAL_EXAM", "weight": 0.70 }
]
```

Expected `200` echoing normalized components.
Negative set (each → 400):
weights summing to `0.9` / `1.1` · no FINAL_EXAM · FINAL_EXAM weight `0.3` (< 0.40) ·
duplicate type.

### 2.2 Enter manual scores per student

```
PUT {{baseUrl}}/api/v1/classes/{{classId}}/students/{{studentCode}}/scores
[
  { "componentType": "ATTENDANCE", "score": 8.5 },
  { "componentType": "FINAL_EXAM", "score": 7 }
]
```

Expected `200`. Negatives: `EXERCISE` → 400 (auto-graded); `score: -1` → 400
`[i].score: must be greater than or equal to 0.00`; unknown studentCode → 404
(trailing spaces trimmed).

### 2.3 Read scores / transcript

```
GET {{baseUrl}}/api/v1/classes/{{classId}}/students/{{studentCode}}/scores
GET {{baseUrl}}/api/v1/classes/{{classId}}/transcript
```

Expected: entries per component; `letterGrade/gpa` and usually `total` are `null`
until EXERCISE exists (needs grading results — see Chapter 5 note).

### 2.4 Archive class

```
PATCH-free zone: PUT {{baseUrl}}/api/v1/classes/{{classId}}/archive
```

Expected `200`, `status:"ARCHIVED"`.

---

## 3. Assignments (exercises)

### 3.1 Create

```
POST {{baseUrl}}/api/v1/assignments
{
  "title": "Lab 01 — REST basics",
  "description": "Build a small REST API",
  "classId": "{{classId}}",
  "gradingStrategy": "STUDENT_DOCKER_COMPOSE"
}
```

Numeric fields are optional (defaults: port 8080, startup 60000ms, execution
300000ms, memory 256MB, cpu 0.5). Expected `201` (**save `assignmentId`**),
`published:false`.

Negatives (each → 400): missing/blank title · LECTURER strategy without
`dockerComposeTemplate` · duplicate title in same class · `classId` owned by
another lecturer → 404 · `maxMemoryMb: 32` → validation detail names the field.

### 3.2 List + filters

```
GET {{baseUrl}}/api/v1/assignments?page=0&size=10
GET …?classId={{classId}}
GET …?published=true
GET …?search=lab
```

Paged envelope: `data.meta {page,pageSize,pages,total}` + `result[]`.
Page beyond last → empty `result`, correct `meta.total`.

### 3.3 Detail / Update

```
GET    {{baseUrl}}/api/v1/assignments/{{assignmentId}}
PUT    {{baseUrl}}/api/v1/assignments/{{assignmentId}}
{ "title": "Lab 01 — REST basics v2", "maxMemoryMb": 512 }
```

Update is partial; moving `classId` → 400 "cannot be changed after creation".

### 3.4 Publish (idempotent)

```
POST {{baseUrl}}/api/v1/assignments/{{assignmentId}}/publish
```

`200` twice OK, `published:true`. Required before students may submit.

### 3.5 Soft delete

```
DELETE {{baseUrl}}/api/v1/assignments/{{assignmentId}}
```

`200` then GET → `404`. Row remains in DB (`deleted_at` set) — see appendix.

---

## 4. Test plans & steps (the grading exercise)

### 4.1 Create plan

```
POST {{baseUrl}}/api/v1/assignments/{{assignmentId}}/plans
{ "name": "CRUD Book API — Basic", "sequenceOrder": 1, "weight": 10 }
```

Expected `201` (**save `planId`**). Duplicate `sequenceOrder` → 400 descriptive.

### 4.2 Add steps — the chained CRUD-Book example

Each step:

```
POST {{baseUrl}}/api/v1/assignments/{{assignmentId}}/plans/{{planId}}/steps
X-User-Id: {{ownerLecturer1}}
```

**Step order 1 — create book, extract id**

```json
{ "stepOrder": 1, "name": "Create a book", "stepType": "HTTP_REQUEST",
  "config": {
    "method": "POST", "path": "/api/v1/books",
    "headers": {"Content-Type": "application/json"},
    "body": {"title": "Dế Mèn Phiêu Lưu Ký", "author": "Tô Hoài", "year": 1941},
    "expected_status": 201,
    "extract": [{"name":"bookId","from":"response_body","expression":"$.id"}] } }
```

**Order 2 — verify using `${bookId}`**

```json
{ "stepOrder": 2, "name": "Verify book details", "stepType": "HTTP_REQUEST",
  "config": { "method": "GET",
              "path": "/api/v1/books/${bookId}",
              "expected_status": 200,
              "assertions": [{"kind": "contains", "text": "Dế Mèn Phiêu Lưu Ký"}] } }
```

> Do NOT use legacy `expected_body_contains` — the assertion engine only evaluates
> `assertions[]` (`contains` kind above).

**Order 3 — search**, **order 4 — DB_SCHEMA_CHECK**, **order 5 — DB_QUERY**
(copy configs verbatim from [`web-grading-system-deploy` `docs/db/README.md` §8.1](https://github.com/PTIT-DTL-Project/web-grading-system-deploy/blob/main/docs/db/README.md)).

All → `201`; response echoes parsed `config` object.

### 4.3 Step negatives (each → 400)

unknown method `"TELEPORT"` · path without leading `/` · `expected_status: 42` ·
extract missing expression · SCHEMA check missing `column_name` · MIGRATION empty
statements · DELAY `-5` · EXTRACT var without value/from · `config: []` ·
duplicate `stepOrder` (message names it).

### 4.4 List nested / update / reorder / delete

```
GET  …/plans                                → both plans, steps nested & sorted
PUT  …/plans/{{planId}}                     → rename/reorder/description
PUT  …/plans/{{planId}}/steps/{stepId}      → partial update
DELETE …/plans/{{planId}}/steps/{stepId}    → soft delete step
DELETE …/plans/{{planId}}                    → soft delete plan (+ its steps)
```

Reorder onto occupied slot → 400 naming the conflict; move to free slot → 200.
Steps stay editable after publish (executor reads config at job time).
Other lecturer accessing your plans → 404.

---

## 5. Student submissions

⚠️ Identity: `X-User-Id` **stamps `submissions.student_id` and gates ownership at result read**, so it must be the real student id. It is **no longer self-asserted**: since 2026-09-30 the api-gateway copies it from the validated Keycloak token subject and strips any value the client supplied, and on the direct path a request without `X-Gateway-Secret` is rejected with `401` before reaching this controller. The gateway injection designed in `system-design-v1.0.md` §3.1 is now implemented — see §0 for both modes.
Grading is webhook-triggered: after the PUT, RustFS fires `ObjectCreated:Put` →
submission-service publishes `GRADE_SUBMISSION` → executor grades (`FETCHING →
BUILDING → RUNNING → DONE/FAILED`). There is no confirm endpoint.

### 5.1 Request presigned upload URL

```
POST {{baseUrl}}/api/v1/submissions/presigned-url?assignmentId={{assignmentId}}&zipFileName=lab01.zip
X-User-Id: {{fake_student_id}}
```

Expected `201`: `uploadUrl` + `submissionId` (**save both**).
`planId` is an optional extra query param (`&planId={id}`) — omitted grades all plans.

### 5.2 Upload the zip

New request: **PUT** `{{uploadUrl}}` → Body → **binary** → select any `.zip`.
No auth headers (signature authenticates). Expected `200` from RustFS.
The upload itself triggers grading via the RustFS webhook — no further call needed.

### 5.3 Verify

```
GET {{baseUrl}}/api/v1/submissions                      (mine) ← needs X-User-Id
X-User-Id: {{fake_student_id}}
GET {{baseUrl}}/api/v1/submissions/{{submissionId}}      (no ownership check today — any caller can read by id)
X-User-Id: {{fake_student_id}}
GET {{baseUrl}}/api/v1/submissions/assignment/{{assignmentId}}
```

Wrong method anywhere (e.g. PUT on a GET-only path) → 405 envelope;
unknown path → 404 envelope; required query param omitted → 400 naming it.

---

## 6. Optional — verify rows directly in Neon

```bash
psql "postgresql://neondb_owner:npg_Vmfuxhe1WPO5@ep-frosty-hill-ayd5wchg-pooler.c-5.us-east-2.aws.neon.tech/assignment_db"
```

| Check | Query |
|---|---|
| assignment soft-deleted | `select title, deleted_at from assignments where id='…'` |
| published flag | `select published from assignments where id='…'` |
| plans kept after step delete | `select count(*) from test_steps where plan_id='…' and deleted_at is null` |
| score row stored | `select * from student_scores where student_code='B22DCCN001'` |

---

## 7. Consolidated negative checklist

| Endpoint hit wrongly | Expected |
|---|---|
| wrong HTTP verb on any endpoint | 405 `Method not allowed on this endpoint` |
| unknown path | 404 `No handler for this path` |
| broken JSON body | 400 `Malformed request body` |
| wrong Content-Type on JSON POST | 415 `Unsupported Content-Type` |
| missing required query param | 400 `Missing required parameter: <name>` |
| no bearer token at the gateway | 401 from the gateway entry point — never reaches a controller |
| missing/invalid `X-Gateway-Secret` on the direct path | 401 from the service entry point — a role header sent without the secret is ignored, not honoured |
| missing `X-User-Id` (direct path) | 400 — anonymous default → `invalid UUID`; submission endpoints → `Missing required header: X-User-Id`; non-canonical UUID → `X-User-Id must be a canonical UUID` |
| `roles=STUDENT` (or no `X-User-Roles`) on a lecturer-only endpoint | 403 from method security — see §0 *Role rule*; `GET /api/v1/classes/{id}` is the one class endpoint that stays 200 (owner-scoped) |
| `X-User-Roles` on a non-own submission / non-own result | `GET /api/v1/submissions/{id}` → 404 · `GET /api/v1/results/{submissionId}` → 403, unless `LECTURER` |
| other lecturer's resource | 404 (no information leak) |
| duplicate unique field | 400 with descriptive message |
| oversized CSV upload | 413 `Uploaded file is too large` |
