# Task Manager Service

A task manager in plain Java 17 (no frameworks, no libraries). Each operation is a Java method on `TaskApi` that returns an `ApiResponse` (status code + JSON-style body). An LLM can turn plain English such as *"Remind me to pay rent on the 1st of every month"* into a task and break big tasks into subtasks.

## Package structure (`src/`)

| Package | Contents |
|---|---|
| `model` | `Task` (immutable, Builder), `TaskRequest` (raw String input), `Priority` enum |
| `repository` | `TaskRepository` interface, `InMemoryTaskRepository` (Singleton), `IdempotencyStore` (Singleton) |
| `service` | `TaskService` (logic + validation + Observer subject), `TaskQuery` (Builder), `TaskFilter`/`Filters`, `SortStrategy`/`Sorts`, `DateHelper` |
| `parser` | `TaskParser`, `LlmTaskParser`, `RuleBasedTaskParser`, `BreakdownStrategy`, `LlmBreakdownStrategy`, `RuleBasedBreakdownStrategy`, `ParserFactory`, `LlmClient`, `HttpLlmClient`, `SimpleJson`, `ParsedTask` |
| `event` | `TaskEventListener`, `ConsoleLogListener`, `AuditTrailListener` |
| `exception` | `ApiException` + `ValidationException` (400), `NotFoundException` (404), `ConflictException` (422), `BadGatewayException` (502), `ServiceUnavailableException` (503), `FieldIssue` |
| `api` | `TaskApi`, `ApiResponse`, `ErrorResponseFactory` |
| `app` | `Main` (demo), `TestRunner`, `FakeLlmClient`, `FaultyLlmClient` |

## Setup

Requires **JDK 17+**. From the `task-manager` folder:

```powershell
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out app.Main
java -cp out app.TestRunner
```

`Main` uses the real model when `LLM_API_KEY` is set, otherwise the offline rule-based parser/breakdown. `TestRunner` always uses fakes (no key, no internet).

**Option A: `.env` file (recommended).** Copy the template and put your key in it:

```powershell
Copy-Item .env.example .env
notepad .env        # set LLM_API_KEY=... (and optionally LLM_PROVIDER, LLM_MODEL)
```

`.env` is git-ignored, so the key is never committed. `.env.example` is the tracked template. The loader (`config.EnvConfig`) reads `.env` from the folder you run Java in. A real environment variable overrides the file, and the placeholder `your-key-here` is treated as "no key".

**Option B: environment variables** (for this terminal session only):

```powershell
$env:LLM_API_KEY = "your-key"        # never commit a key
$env:LLM_PROVIDER = "anthropic"      # optional: anthropic | openai | gemini
$env:LLM_MODEL = "model-name"        # optional override
```

Run `java -cp out app.Main` from the `task-manager` folder so the `.env` file is found.

If a `TaskApi` is built with no key and without the rule-based fallback (`ParserFactory.createParser(false)`), AI operations return **503**.

## TaskApi operations

All errors use one shape (see `API_CONTRACT.md`). `idempotencyKey` may be `null`.

| Operation | Parameters | Success | Errors |
|---|---|---|---|
| `createTask` | `TaskRequest`, `idempotencyKey` | 201 | 400, 422, 500 |
| `getTask` | `int id` | 200 | 404 |
| `listTasks` | `TaskQuery` (nullable) | 200 | 400 |
| `updateTask` | `int id`, `TaskRequest` | 200 | 400, 404 |
| `deleteTask` | `int id` (also deletes subtasks) | 200 | 404 |
| `createTaskFromText` | `String text`, `idempotencyKey` | 201 | 400, 422, 502, 503 |
| `createSubtask` | `int parentId`, `TaskRequest` | 201 | 400, 404 |
| `listSubtasks` | `int parentId` | 200 | 404 |
| `breakDownTask` | `int id`, `idempotencyKey` | 201 | 400, 404, 422, 502, 503 |
| `listByPriority` | `String level` | 200 | 400 |

### Example calls and printed responses (real output of `Main`)

**createTask**
```java
api.createTask(new TaskRequest("Pay rent", "Monthly rent", "false", "2026-12-01", "HIGH"), null);
```
```json
{"status":201,"body":{"id":1,"title":"Pay rent","description":"Monthly rent","completed":false,"dueDate":"2026-12-01","priority":"HIGH","parentId":null,"createdAt":1795787490670,"recurrence":null}}
```

**getTask (unknown id)** — `api.getTask(99)`
```json
{"status":404,"body":{"error":{"code":"NOT_FOUND","message":"Task 99 not found","details":[]}}}
```

**Validation error** — `api.createTask(new TaskRequest("", " ", "maybe", "2026-02-30", "URGENT"), null)`
```json
{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"title","issue":"must not be empty"},{"field":"description","issue":"must not be empty"},{"field":"completed","issue":"must be \"true\" or \"false\""},{"field":"dueDate","issue":"must be a real date in YYYY-MM-DD format"},{"field":"priority","issue":"must be LOW, MEDIUM or HIGH"}]}}}
```

**updateTask** — `api.updateTask(2, new TaskRequest("Buy milk", "Two litres", "true", "2026-11-30", "LOW"))` → 200 with the updated task (completed `true`).

**deleteTask** — `api.deleteTask(4)`
```json
{"status":200,"body":{"deleted":true,"id":4,"tasksRemoved":4}}
```
(`Main` prints the repository before and after: the parent and its 3 subtasks disappear.)

**createTaskFromText** (offline rule-based parser) — `api.createTaskFromText("Remind me to pay rent on the 1st of every month", null)`
```json
{"status":201,"body":{"id":3,"title":"pay rent on the 1st of every month","description":"Remind me to pay rent on the 1st of every month","completed":false,"dueDate":"2026-12-01","priority":"MEDIUM","parentId":null,"createdAt":1795787490697,"recurrence":"monthly"}}
```
The model's `recurrence` must be `daily`, `weekly`, `monthly` or `yearly` (case-insensitive, stored lower-case) or null. Anything else gives 502 and nothing is saved, e.g. `{"status":502,"body":{"error":{"code":"BAD_GATEWAY","message":"Model output failed validation","details":[{"field":"recurrence","issue":"must be daily, weekly, monthly or yearly"}]}}}`.

With a model that returns an empty title: `{"status":502,"body":{"error":{"code":"BAD_GATEWAY","message":"Model output failed validation","details":[{"field":"title","issue":"must not be empty"}]}}}` and nothing is saved.

**createSubtask / listSubtasks** — `api.createSubtask(4, new TaskRequest("Pick dates", "Check calendar", "false", null, null))` → 201 with `"parentId":4`; `api.listSubtasks(4)` → 200 with a list. Giving a subtask a subtask:
```json
{"status":400,"body":{"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[{"field":"id","issue":"task 5 is a subtask and cannot have subtasks"}]}}}
```

**breakDownTask** (offline rule-based strategy) — `api.breakDownTask(4, null)` on "Plan trip: book flights and pack bags". The rule splits the title on commas and the word "and", so this gives two subtasks.
```json
{"status":201,"body":[{"id":6,"title":"Plan trip: book flights","description":"Part of: Plan trip: book flights and pack bags","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490698,"recurrence":null},{"id":7,"title":"pack bags","description":"Part of: Plan trip: book flights and pack bags","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":4,"createdAt":1795787490698,"recurrence":null}]}
```
All-or-nothing: one invalid item or a failed model call gives 502 and saves none. Breaking down a subtask (`api.breakDownTask(5, null)`) gives 400 with `"issue":"task 5 is a subtask and cannot be broken down"`.

**Idempotency** — `createTask(rent, "key-1")` twice returns the identical 201 response and the repository holds one task; a different request with `"key-1"`:
```json
{"status":422,"body":{"error":{"code":"CONFLICT","message":"Idempotency key reused with a different request","details":[]}}}
```

**listTasks** — filter + search + sort + page in one call:
```java
api.listTasks(TaskQuery.builder().completed("false").priority("HIGH").search("report")
        .sortBy("dueDate").order("asc").page(1).pageSize(1).build());
```
```json
{"status":200,"body":{"items":[{"id":10,"title":"Read report","description":"review the REPORT","completed":false,"dueDate":"2026-12-05","priority":"HIGH","parentId":null,"createdAt":1795787490706,"recurrence":null}],"page":1,"pageSize":1,"totalItems":2,"totalPages":2}}
```
A page past the end returns 200 with `"items":[]`.

**listByPriority** — `api.listByPriority("LOW")` → 200 list; `api.listByPriority("URGENT")` → 400 (`field: level`).

## Design patterns

| Pattern | Where |
|---|---|
| Builder | `Task.Builder` (3 required fields, rest optional), `TaskQuery.Builder` |
| Singleton | `InMemoryTaskRepository`, `IdempotencyStore` (synchronized methods). `TaskApi` takes the store's lock around each idempotent operation, so instances that share a store cannot race. |
| Factory | `ErrorResponseFactory` (error shape in one place), `ParserFactory` (chooses LLM or rule-based) |
| Strategy | `TaskParser`, `BreakdownStrategy`, `TaskFilter`, `SortStrategy`, `LlmClient` (real vs fake) |
| Observer | `TaskService` (subject) + `TaskEventListener` (`ConsoleLogListener`, `AuditTrailListener`) |

Dependency Inversion: `TaskService` gets a `TaskRepository`, `TaskParser` and `BreakdownStrategy` through its constructor. Open/Closed: a new filter or sort is a new class implementing `TaskFilter`/`SortStrategy`.

### Plugging in another parser

```java
public interface TaskParser {
    ParsedTask parse(String text, String today);   // today = YYYY-MM-DD
}
```
Implement it (throw `BadGatewayException` if your source fails), then pass it to `new TaskService(repo, myParser, breakdown)`. The service never needs to know which parser it has; whatever it returns is validated like user input.

## Prompts and why

**Task creation** (`LlmTaskParser.buildPrompt`):
```
You convert a user's request into one task.
Today's date is <today> (format YYYY-MM-DD). Use it to resolve relative dates such as "tomorrow" or "next Friday".
Reply with ONLY a single flat JSON object, no markdown, no commentary, with exactly these keys:
  "title": short imperative summary (string),
  "description": one sentence of detail (string),
  "dueDate": date as YYYY-MM-DD, or null if none,
  "recurrence": e.g. "monthly", "weekly", or null if it does not repeat.
User request: <text>
```
Why: the model cannot know today's date, so it is injected; "ONLY JSON" with fixed keys makes `SimpleJson.parseFlat` reliable; an explicit date format and `null` for "none" avoid free-text dates. The reply is still treated as untrusted and validated (never trust the model).

**Breakdown** (`LlmBreakdownStrategy.buildPrompt`):
```
Break the following task into 2 to 6 concrete subtasks.
Reply with ONLY the subtasks, one per line, in exactly this format:
title|description
No numbering, no bullets, no markdown, no extra text.
Task title: <title>
Task description: <description>
```
Why: a line-per-item `title|description` format is trivial to parse with String methods; the 2-6 limit stops runaway lists; banning numbering/bullets keeps titles clean. A line without `|` is treated as malformed (502).

## What the AI-generated tests missed, and what I changed

The first checklist covered the happy paths (create, get, update, delete, one valid model reply). Gaps found while critiquing it, and what was added:

- **Several validation errors at once**: only one bad field was checked; now one request with five bad fields must report all five.
- **Leap years**: added 2024-02-29 (valid) vs 2026-02-29 (invalid), plus wrong formats.
- **Idempotency edge cases**: errors must not be stored (fix and retry with the same key succeeds); a key shared across different operations conflicts; fingerprints use `length:value` so `("a|b","c")` and `("a","b|c")` are different requests; replays must not call the model again (fake call counter).
- **Bad model output**: empty title, impossible date, malformed text, and an exception all give 502 with nothing saved; one bad breakdown item saves none of the items; missing key gives 503.
- **Hierarchy rules**: subtask of a subtask, breaking down a subtask, unknown parent, cascade delete.
- **Pagination**: page beyond the last page returns an empty list (not an error); page 0, pageSize 0, bad sort/order/priority/completed values return 400; items with no due date sort last.
- **Observer**: removing a listener really stops notifications.
- **Date-word false positives**: the offline parser matched "1st" inside "21st" and "31st", so "pay on the 21st" got a 1st-of-month due date. It now matches the whole word only.
- **Recurrence dropped**: the model's `recurrence` was parsed but never stored. It is now a `recurrence` field on the task (`null` when none) and is validated.
- **Lock per instance**: idempotency was synchronized on each `TaskApi`, so two `TaskApi` objects sharing one store could both call the model with the same key. The lock is now on the shared store.
- **Singleton state**: the repository and idempotency store are shared, so `TestRunner` resets both before each group; without that, tests affected each other.
