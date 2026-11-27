# API_CONTRACT.md

## Conventions

- Every `TaskApi` method returns an `ApiResponse`: a numeric **status** and a JSON-style **body**. Methods never throw.
- Success bodies are a task object, a list of task objects, a page object, or a small confirmation object.
- Every failure body uses the single error shape in **Errors**. `code` and `message` are always strings.
- `idempotencyKey` is optional (may be `null`). Same key + same request returns the original stored response and does nothing new; same key + different request returns `422`. Only successful responses are stored.
- A task object: `id, title, description, completed, dueDate, priority, parentId, createdAt, recurrence`. `recurrence` is `null` unless the parser set it to `daily`, `weekly`, `monthly` or `yearly`.
- Task fields in `TaskRequest` (all Strings): `title` and `description` (required, not blank), `completed` (required, exactly `"true"`/`"false"`), `dueDate` (optional, real `YYYY-MM-DD`), `priority` (optional, `LOW|MEDIUM|HIGH`, default `MEDIUM`).

## Operations

### createTask
| | |
|---|---|
| **Java method** | `ApiResponse createTask(TaskRequest request, String idempotencyKey)` |
| **Purpose** | Create one task from structured input |
| **Success** | `201` — the created task |
| **Errors** | `400` VALIDATION_ERROR · `422` CONFLICT · `500` INTERNAL_ERROR |

Request: `title="Pay rent" description="Monthly rent" completed="false" dueDate="2026-12-01" priority="HIGH" idempotencyKey="rent-001"`

```json
{"status":201,"body":{"id":1,"title":"Pay rent","description":"Monthly rent","completed":false,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795787490670,"recurrence":null}}
```
Error (`title=""`): `{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"title","issue":"must not be empty"}]}}}`

### getTask
| | |
|---|---|
| **Java method** | `ApiResponse getTask(int id)` |
| **Purpose** | Fetch one task |
| **Success** | `200` — the task |
| **Errors** | `404` NOT_FOUND |

Request: `id=99`
Error: `{"status":404,"body":{"error":{"code":"NOT_FOUND","message":"Task 99 not found","details":[]}}}`

### listTasks
| | |
|---|---|
| **Java method** | `ApiResponse listTasks(TaskQuery query)` (`null` = defaults) |
| **Purpose** | Filter, search, sort and paginate in one call |
| **Success** | `200` — page object `{items, page, pageSize, totalItems, totalPages}` |
| **Errors** | `400` VALIDATION_ERROR |

| Query field | Type | Rules |
|---|---|---|
| `completed` | String | `"true"` or `"false"` |
| `priority` | String | `LOW`, `MEDIUM`, `HIGH` |
| `search` | String | case-insensitive match on title or description |
| `sortBy` | String | `createdAt` or `dueDate` (tasks without a due date last) |
| `order` | String | `asc` (default) or `desc` |
| `page` | int | ≥ 1, default 1; a page past the end returns empty `items` |
| `pageSize` | int | ≥ 1, default 10 |

Request: `priority=HIGH sortBy=dueDate order=asc page=1 pageSize=1`
```json
{"status":200,"body":{"items":[{"id":10,"title":"Read report","description":"Review the REPORT","completed":false,"dueDate":"2026-12-05","priority":"HIGH","parentId":null,"createdAt":1795787490706,"recurrence":null}],"page":1,"pageSize":1,"totalItems":2,"totalPages":2}}
```
Error (`page=0`): `{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"page","issue":"must be 1 or greater"}]}}}`

### updateTask
| | |
|---|---|
| **Java method** | `ApiResponse updateTask(int id, TaskRequest request)` |
| **Purpose** | Replace a task's fields (id, parentId, createdAt are kept) |
| **Success** | `200` — the updated task |
| **Errors** | `400` VALIDATION_ERROR · `404` NOT_FOUND |

Request: `id=2 title="Buy milk" description="Two litres" completed="true"`
```json
{"status":200,"body":{"id":2,"title":"Buy milk","description":"Two litres","completed":true,"dueDate":"2026-11-30","priority":"LOW","parentId":null,"createdAt":1795787490674,"recurrence":null}}
```
Error (`id=999`): `{"status":404,"body":{"error":{"code":"NOT_FOUND","message":"Task 999 not found","details":[]}}}`

### deleteTask
| | |
|---|---|
| **Java method** | `ApiResponse deleteTask(int id)` |
| **Purpose** | Delete a task and all of its subtasks |
| **Success** | `200` — `{deleted, id, tasksRemoved}` |
| **Errors** | `404` NOT_FOUND |

Request: `id=4`
```json
{"status":200,"body":{"deleted":true,"id":4,"tasksRemoved":4}}
```
Error (`id=4` again): `{"status":404,"body":{"error":{"code":"NOT_FOUND","message":"Task 4 not found","details":[]}}}`

### createTaskFromText
| | |
|---|---|
| **Java method** | `ApiResponse createTaskFromText(String text, String idempotencyKey)` |
| **Purpose** | Turn plain English into a task via the configured parser (LLM or rule-based) |
| **Success** | `201` — the created task |
| **Errors** | `400` blank text · `422` CONFLICT · `502` BAD_GATEWAY (model failed/timed out, or its output failed validation; nothing saved) · `503` SERVICE_UNAVAILABLE (no `LLM_API_KEY`, no fallback) |

Request: `text="Remind me to pay rent on the 1st of every month"`
```json
{"status":201,"body":{"id":3,"title":"pay rent on the 1st of every month","description":"Remind me to pay rent on the 1st of every month","completed":false,"dueDate":"2026-12-01","priority":"MEDIUM","parentId":null,"createdAt":1795787490697,"recurrence":"monthly"}}
```
Error (model returned an empty title): `{"status":502,"body":{"error":{"code":"BAD_GATEWAY","message":"Model output failed validation","details":[{"field":"title","issue":"must not be empty"}]}}}`

### createSubtask
| | |
|---|---|
| **Java method** | `ApiResponse createSubtask(int parentId, TaskRequest request)` |
| **Purpose** | Create a task whose `parentId` is set |
| **Success** | `201` — the created subtask |
| **Errors** | `400` invalid input, or parent is itself a subtask · `404` parent not found |

Request: `parentId=4 title="Pick dates" description="Check calendar" completed="false"`
```json
{"status":201,"body":{"id":5,"title":"Pick dates","description":"Check calendar","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490697,"recurrence":null}}
```
Error (`parentId=5`, itself a subtask): `{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"id","issue":"task 5 is a subtask and cannot have subtasks"}]}}}`

### listSubtasks
| | |
|---|---|
| **Java method** | `ApiResponse listSubtasks(int parentId)` |
| **Purpose** | List the subtasks of a task |
| **Success** | `200` — list of tasks (may be empty) |
| **Errors** | `404` NOT_FOUND |

Request: `parentId=4`
```json
{"status":200,"body":[{"id":5,"title":"Pick dates","description":"Check calendar","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490697,"recurrence":null}]}
```
Error (`parentId=99`): `{"status":404,"body":{"error":{"code":"NOT_FOUND","message":"Task 99 not found","details":[]}}}`

### breakDownTask
| | |
|---|---|
| **Java method** | `ApiResponse breakDownTask(int id, String idempotencyKey)` |
| **Purpose** | Ask the breakdown strategy for subtasks and save them all-or-nothing |
| **Success** | `201` — list of the created subtasks |
| **Errors** | `400` task is a subtask · `404` NOT_FOUND · `422` CONFLICT · `502` BAD_GATEWAY (model failed or any item invalid; none saved) · `503` SERVICE_UNAVAILABLE |

Request: `id=4 idempotencyKey="bd-1"` on "Plan trip: book flights and pack bags" (offline rule-based strategy; splits on commas and "and")
```json
{"status":201,"body":[{"id":6,"title":"Plan trip: book flights","description":"Part of: Plan trip: book flights and pack bags","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490698,"recurrence":null},{"id":7,"title":"pack bags","description":"Part of: Plan trip: book flights and pack bags","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490698,"recurrence":null}]}
```
Error (`id=5`, a subtask): `{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"id","issue":"task 5 is a subtask and cannot be broken down"}]}}}`

### listByPriority
| | |
|---|---|
| **Java method** | `ApiResponse listByPriority(String level)` |
| **Purpose** | Return tasks of one priority |
| **Success** | `200` — list of tasks |
| **Errors** | `400` VALIDATION_ERROR (level not LOW/MEDIUM/HIGH) |

Request: `level="LOW"`
```json
{"status":200,"body":[{"id":2,"title":"Buy milk","description":"Two litres","completed":true,"dueDate":"2026-11-30","priority":"LOW","parentId":null,"createdAt":1795787490674,"recurrence":null}]}
```
Error (`level="URGENT"`): `{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"level","issue":"must be LOW, MEDIUM or HIGH"}]}}}`

---

## Errors

### Error shape

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [{ "field": "title", "issue": "must not be empty" }]
  }
}
```

| Field | Type | Meaning |
|---|---|---|
| `error.code` | string | Stable, machine-readable identifier |
| `error.message` | string | Short human-readable summary |
| `error.details` | array | Zero or more `{ "field", "issue" }`; empty when nothing is field-specific. Filled for 400, and for 502 when model output failed validation |

### Error codes

| Status | `code` | When it happens |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Input broke a rule; breaking down a subtask or giving a subtask a subtask; invalid query values; invalid level in `listByPriority` |
| 404 | `NOT_FOUND` | The task id (or parent id) does not exist |
| 422 | `CONFLICT` | An idempotency key was reused with a different request |
| 502 | `BAD_GATEWAY` | The model call failed or timed out, or its output failed validation. Nothing was saved |
| 503 | `SERVICE_UNAVAILABLE` | `LLM_API_KEY` is not set (and no offline fallback is configured) |
| 500 | `INTERNAL_ERROR` | Any unexpected failure. No stack trace in the body |
