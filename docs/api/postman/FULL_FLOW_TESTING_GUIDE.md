# Postman — Full-Flow API Testing Guide

Step-by-step Postman instructions covering **every flow** of the system:
Classes → Students → Scores → Assignments → Plans & Steps → Student Submissions,
plus negative tests and optional DB verification.

> Canonical flows live in `docs/design/usecase-flows.md` (UC-01…03).
> Automated runner alternative: `docs/api/scenarios/*.sh`.

---

## 0. Environment setup (one time)

Create a Postman Environment (`⚙ Environments → Create`) with:

| Variable | Initial value | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:18081` | service port when booted locally; use gateway host if testing through it |
| `ownerLecturer1` | any UUID, e.g. `2d93941a-4221-458b-a03d-43bd6315d02e` | main lecturer identity |
| `ownerLecturer2` | any other UUID | used to prove 404 ownership isolation |
| `classId`, `assignmentId`, `planId`, `stepId`, `studentCode`, `submissionId` | empty | captured during the flow |

**Auto-capture ids** — in each create-request's *Tests* tab add:

```js
const j = pm.response.json();
if (j.data && j.data.id) pm.environment.set("classId", j.data.id);
```

(adjust the variable + json path per request).

**Identity rule:** use `{{ownerLecturer1}}` in `X-User-Id` for the whole happy flow.
A different UUID on a later request = ownership miss → indistinguishable `404`.
Missing header defaults to `anonymous` which is not a UUID → `400`.

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
              "expected_body_contains": "Dế Mèn Phiêu Lưu Ký" } }
```

**Order 3 — search**, **order 4 — DB_SCHEMA_CHECK**, **order 5 — DB_QUERY**
(copy configs verbatim from `docs/db/README.md` §8.1).

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

⚠️ Pre-Keycloak caveats: server generates a **random `studentId`** per upload;
Kafka→executor pipeline is not wired yet, so status stays `PENDING` after confirm.

### 5.1 Request presigned upload URL

```
POST {{baseUrl}}/api/v1/submissions/presigned-url?assignmentId={{assignmentId}}&zipFileName=lab01.zip
```

Expected `200`: `uploadUrl` + `submissionId` (**save both**).

### 5.2 Upload the zip

New request: **PUT** `{{uploadUrl}}` → Body → **binary** → select any `.zip`.
No auth headers (signature authenticates). Expected `200` from RustFS.

### 5.3 Confirm

```
POST {{baseUrl}}/api/v1/submissions/{{submissionId}}/confirm
```

Expected `200` "Upload confirmed". (RustFS webhook does this automatically too.)

### 5.4 Verify

```
GET {{baseUrl}}/api/v1/submissions                      (mine)
GET {{baseUrl}}/api/v1/submissions/{{submissionId}}
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
| missing X-User-Id (defaults anonymous) | 400 invalid UUID |
| other lecturer's resource | 404 (no information leak) |
| duplicate unique field | 400 with descriptive message |
| oversized CSV upload | 413 `Uploaded file is too large` |
