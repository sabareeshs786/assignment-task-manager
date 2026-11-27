package app;

import api.ApiResponse;
import api.TaskApi;
import event.AuditTrailListener;
import event.TaskEventListener;
import model.TaskRequest;
import config.EnvConfig;
import parser.LlmClient;
import parser.RuleBasedTaskParser;
import parser.ParserFactory;
import repository.IdempotencyStore;
import repository.InMemoryTaskRepository;
import service.DateHelper;
import service.TaskQuery;
import service.TaskService;

import java.util.Objects;

/** No key, no internet: uses fakes. Pattern for every check: call -> compare -> print PASS/FAIL. */
public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static final InMemoryTaskRepository REPO = InMemoryTaskRepository.getInstance();

    public static void main(String[] args) {
        dates();
        crud();
        validation();
        idempotency();
        aiCreation();
        subtasksAndBreakdown();
        queries();
        observer();
        regressions();
        configChecks();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---- setup: reset the Singletons between groups ----
    private static TaskApi fresh(LlmClient client, boolean allowRules, TaskEventListener... ls) {
        REPO.reset();
        IdempotencyStore.getInstance().clear();
        TaskService s = new TaskService(REPO,
                ParserFactory.createParser(client, allowRules), ParserFactory.createBreakdown(client, allowRules));
        for (TaskEventListener l : ls) {
            s.addListener(l);
        }
        return new TaskApi(s);
    }

    private static TaskRequest ok(String title) {
        return new TaskRequest(title, "desc", "false", null, null);
    }

    private static boolean has(ApiResponse r, String text) {
        return r.toJson().contains(text);
    }

    private static void dates() {
        System.out.println("-- dates");
        check("2026-02-30 rejected", false, DateHelper.isValidDate("2026-02-30"));
        check("2024-02-29 (leap) accepted", true, DateHelper.isValidDate("2024-02-29"));
        check("2026-02-29 (non-leap) rejected", false, DateHelper.isValidDate("2026-02-29"));
        check("2026-13-01 rejected", false, DateHelper.isValidDate("2026-13-01"));
        check("wrong format rejected", false, DateHelper.isValidDate("01/12/2026"));
    }

    private static void crud() {
        System.out.println("-- CRUD");
        TaskApi api = fresh(null, true);
        ApiResponse r = api.createTask(new TaskRequest("Pay rent", "Monthly", "false", "2026-12-01", "HIGH"), null);
        check("create -> 201", 201, r.getStatus());
        check("first id is 1", true, has(r, "\"id\":1"));
        check("default priority MEDIUM", true, has(api.createTask(ok("x"), null), "\"priority\":\"MEDIUM\""));
        check("get existing -> 200", 200, api.getTask(1).getStatus());
        ApiResponse nf = api.getTask(999);
        check("get unknown -> 404", 404, nf.getStatus());
        check("404 code NOT_FOUND", true, has(nf, "\"code\":\"NOT_FOUND\""));
        check("update -> 200", 200, api.updateTask(1, ok("New title")).getStatus());
        check("update unknown -> 404", 404, api.updateTask(999, ok("x")).getStatus());
        check("delete -> 200", 200, api.deleteTask(1).getStatus());
        check("get after delete -> 404", 404, api.getTask(1).getStatus());
        check("delete unknown -> 404", 404, api.deleteTask(1).getStatus());
    }

    private static void validation() {
        System.out.println("-- validation");
        TaskApi api = fresh(null, true);
        ApiResponse r = api.createTask(new TaskRequest("", "  ", "maybe", "2026-02-30", "URGENT"), null);
        check("bad input -> 400", 400, r.getStatus());
        check("code VALIDATION_ERROR", true, has(r, "\"code\":\"VALIDATION_ERROR\""));
        check("message is string", true, has(r, "\"message\":\"Validation failed\""));
        check("reports title", true, has(r, "\"field\":\"title\""));
        check("reports description", true, has(r, "\"field\":\"description\""));
        check("reports completed", true, has(r, "\"field\":\"completed\""));
        check("reports dueDate", true, has(r, "\"field\":\"dueDate\""));
        check("reports priority", true, has(r, "\"field\":\"priority\""));
        check("null title -> 400", 400, api.createTask(new TaskRequest(null, "d", "false", null, null), null).getStatus());
        check("completed 'TRUE' -> 400", 400, api.createTask(new TaskRequest("t", "d", "TRUE", null, null), null).getStatus());
        check("null request -> 400", 400, api.createTask(null, null).getStatus());
        check("nothing saved", 0, REPO.findAll().size());
        TaskApi api2 = fresh(null, true);
        api2.createTask(ok("keep"), null);
        check("update invalid -> 400", 400, api2.updateTask(1, ok("")).getStatus());
    }

    private static void idempotency() {
        System.out.println("-- idempotency");
        TaskApi api = fresh(null, true);
        ApiResponse a = api.createTask(ok("A"), "k");
        ApiResponse b = api.createTask(ok("A"), "k");
        check("same key+request -> same response", a.toJson(), b.toJson());
        check("only one task", 1, REPO.findAll().size());
        ApiResponse c = api.createTask(ok("B"), "k");
        check("same key, different request -> 422", 422, c.getStatus());
        check("422 code CONFLICT", true, has(c, "\"code\":\"CONFLICT\""));
        check("still one task", 1, REPO.findAll().size());
        check("failed request not stored", 400, api.createTask(ok(""), "k2").getStatus());
        check("retry same key after fixing -> 201", 201, api.createTask(ok("fixed"), "k2").getStatus());
        check("null key creates duplicates", 2, countAfter(api, ok("D"), 2));
        check("key shared across operations conflicts", 422, api.createTaskFromText("hello", "k").getStatus());
        TaskRequest p1 = new TaskRequest("a|b", "c", "false", null, null);
        TaskRequest p2 = new TaskRequest("a", "b|c", "false", null, null);
        api.createTask(p1, "k3");
        check("separator-ambiguous requests differ", 422, api.createTask(p2, "k3").getStatus());
    }

    private static int countAfter(TaskApi api, TaskRequest r, int times) {
        int before = REPO.findAll().size();
        for (int i = 0; i < times; i++) {
            api.createTask(r, null);
        }
        return REPO.findAll().size() - before;
    }

    private static void aiCreation() {
        System.out.println("-- AI creation");
        FakeLlmClient good = new FakeLlmClient(
                "```json\n{\"title\":\"Pay rent\",\"description\":\"Monthly\",\"dueDate\":\"2026-12-01\",\"recurrence\":\"monthly\"}\n```");
        TaskApi api = fresh(good, false);
        check("good reply -> 201", 201, api.createTaskFromText("pay rent monthly", "t1").getStatus());
        check("replay -> same, no 2nd model call", 1, callsAfterReplay(api, good));
        check("one task saved", 1, REPO.findAll().size());
        check("same key, different text -> 422", 422, api.createTaskFromText("other", "t1").getStatus());

        check("empty title -> 502", 502, runBad(FaultyLlmClient.emptyTitle()));
        check("nothing saved (empty title)", 0, REPO.findAll().size());
        check("impossible date -> 502", 502, runBad(FaultyLlmClient.impossibleDate()));
        check("nothing saved (date)", 0, REPO.findAll().size());
        check("malformed reply -> 502", 502, runBad(FaultyLlmClient.malformed()));
        check("model throws -> 502", 502, runBad(FaultyLlmClient.throwing()));
        TaskApi bad = fresh(FaultyLlmClient.emptyTitle(), false);
        ApiResponse r = bad.createTaskFromText("x", null);
        check("502 code BAD_GATEWAY", true, has(r, "\"code\":\"BAD_GATEWAY\""));
        check("502 carries field details", true, has(r, "\"field\":\"title\""));
        check("failure not stored: retry hits model again", 2, retryCalls());
        check("blank text -> 400", 400, bad.createTaskFromText("  ", null).getStatus());

        TaskApi nokey = fresh(null, false);
        check("no key -> 503", 503, nokey.createTaskFromText("x", null).getStatus());
        check("503 code", true, has(nokey.createTaskFromText("x", null), "\"code\":\"SERVICE_UNAVAILABLE\""));
        TaskApi offline = fresh(null, true);
        ApiResponse rb = offline.createTaskFromText("Remind me to call mom tomorrow", null);
        check("rule-based -> 201", 201, rb.getStatus());
        check("rule-based resolves tomorrow", true, has(rb, "\"dueDate\":\"" + java.time.LocalDate.now().plusDays(1) + "\""));
    }

    private static int callsAfterReplay(TaskApi api, FakeLlmClient c) {
        api.createTaskFromText("pay rent monthly", "t1");
        return c.getCalls();
    }

    private static int runBad(FakeLlmClient c) {
        return fresh(c, false).createTaskFromText("anything", null).getStatus();
    }

    private static int retryCalls() {
        FakeLlmClient c = FaultyLlmClient.emptyTitle();
        TaskApi api = fresh(c, false);
        api.createTaskFromText("x", "same");
        api.createTaskFromText("x", "same");
        return c.getCalls();
    }

    private static void subtasksAndBreakdown() {
        System.out.println("-- subtasks / breakdown");
        TaskApi api = fresh(null, true);
        api.createTask(ok("Plan trip: book flights and pack bags"), null);
        check("createSubtask -> 201", 201, api.createSubtask(1, ok("Sub")).getStatus());
        check("subtask has parentId", true, has(api.getTask(2), "\"parentId\":1"));
        check("listSubtasks -> 200", 200, api.listSubtasks(1).getStatus());
        check("subtask of unknown parent -> 404", 404, api.createSubtask(99, ok("x")).getStatus());
        check("subtask of subtask -> 400", 400, api.createSubtask(2, ok("x")).getStatus());
        check("break down subtask -> 400", 400, api.breakDownTask(2, null).getStatus());
        check("break down unknown -> 404", 404, api.breakDownTask(99, null).getStatus());
        check("list subtasks unknown -> 404", 404, api.listSubtasks(99).getStatus());
        ApiResponse bd = api.breakDownTask(1, "bd");
        check("rule-based breakdown -> 201", 201, bd.getStatus());
        int n = REPO.findAll().size();
        check("breakdown replay creates nothing", n, countAfterBreakdown(api, "bd"));
        api.deleteTask(1);
        check("deleting parent deletes subtasks", 0, REPO.findAll().size());

        FakeLlmClient llm = new FakeLlmClient("Book flights|Compare prices\nPack bags|Check weather");
        TaskApi lapi = fresh(llm, false);
        lapi.createTask(ok("Trip"), null);
        check("LLM breakdown -> 201", 201, lapi.breakDownTask(1, null).getStatus());
        check("3 tasks (parent + 2)", 3, REPO.findAll().size());

        TaskApi badApi = fresh(FaultyLlmClient.badBreakdownItem(), false);
        badApi.createTask(ok("Trip"), null);
        check("invalid item -> 502", 502, badApi.breakDownTask(1, null).getStatus());
        check("none saved when one item invalid", 1, REPO.findAll().size());
        TaskApi downApi = fresh(FaultyLlmClient.throwing(), false);
        downApi.createTask(ok("Trip"), null);
        check("model throws -> 502", 502, downApi.breakDownTask(1, null).getStatus());
        TaskApi malformed = fresh(FaultyLlmClient.malformed(), false);
        malformed.createTask(ok("Trip"), null);
        check("reply without '|' -> 502", 502, malformed.breakDownTask(1, null).getStatus());
        TaskApi nokey = fresh(null, false);
        nokey.createTask(ok("Trip"), null);
        check("no key -> 503", 503, nokey.breakDownTask(1, null).getStatus());
    }

    private static int countAfterBreakdown(TaskApi api, String key) {
        api.breakDownTask(1, key);
        return REPO.findAll().size();
    }

    private static void queries() {
        System.out.println("-- query");
        TaskApi api = fresh(null, true);
        api.createTask(new TaskRequest("Write report", "quarterly", "false", "2026-12-15", "HIGH"), null);
        api.createTask(new TaskRequest("Read REPORT", "x", "false", "2026-12-05", "HIGH"), null);
        api.createTask(new TaskRequest("Report bug", "x", "true", "2026-12-01", "HIGH"), null);
        api.createTask(new TaskRequest("Milk", "x", "false", null, "LOW"), null);
        check("list all -> 200", 200, api.listTasks(null).getStatus());
        check("completed=true -> 1", true, has(api.listTasks(TaskQuery.builder().completed("true").build()), "\"totalItems\":1"));
        check("priority LOW -> 1", true, has(api.listTasks(TaskQuery.builder().priority("LOW").build()), "\"totalItems\":1"));
        check("search is case-insensitive -> 3", true, has(api.listTasks(TaskQuery.builder().search("rEpOrT").build()), "\"totalItems\":3"));
        ApiResponse combo = api.listTasks(TaskQuery.builder().completed("false").priority("HIGH").search("report")
                .sortBy("dueDate").order("asc").page(1).pageSize(1).build());
        check("combined: first is 'Read REPORT'", true, has(combo, "Read REPORT") && !has(combo, "Write report"));
        check("combined: totalItems 2, totalPages 2", true, has(combo, "\"totalItems\":2") && has(combo, "\"totalPages\":2"));
        ApiResponse p2 = api.listTasks(TaskQuery.builder().sortBy("dueDate").order("desc").pageSize(2).page(2).build());
        check("desc dueDate page 2 ends with undated last", true, has(p2, "Milk"));
        ApiResponse beyond = api.listTasks(TaskQuery.builder().page(99).build());
        check("page beyond last -> 200 empty items", true, beyond.getStatus() == 200 && has(beyond, "\"items\":[]"));
        check("default pageSize 10", true, has(api.listTasks(null), "\"pageSize\":10"));
        check("page 0 -> 400", 400, api.listTasks(TaskQuery.builder().page(0).build()).getStatus());
        check("pageSize 0 -> 400", 400, api.listTasks(TaskQuery.builder().pageSize(0).build()).getStatus());
        check("bad sortBy -> 400", 400, api.listTasks(TaskQuery.builder().sortBy("title").build()).getStatus());
        check("bad order -> 400", 400, api.listTasks(TaskQuery.builder().order("up").build()).getStatus());
        check("bad priority filter -> 400", 400, api.listTasks(TaskQuery.builder().priority("x").build()).getStatus());
        check("bad completed filter -> 400", 400, api.listTasks(TaskQuery.builder().completed("yes").build()).getStatus());
        check("listByPriority HIGH -> 200", 200, api.listByPriority("HIGH").getStatus());
        check("listByPriority invalid -> 400", 400, api.listByPriority("URGENT").getStatus());
    }

    private static void observer() {
        System.out.println("-- observer");
        AuditTrailListener audit = new AuditTrailListener();
        TaskApi api = fresh(null, true, audit);
        api.createTask(ok("a"), null);
        api.updateTask(1, new TaskRequest("a", "d", "true", null, null));
        api.deleteTask(1);
        check("created counted", 1, audit.count(TaskEventListener.CREATED));
        check("updated counted", 1, audit.count(TaskEventListener.UPDATED));
        check("completed counted", 1, audit.count(TaskEventListener.COMPLETED));
        check("deleted counted", 1, audit.count(TaskEventListener.DELETED));

        AuditTrailListener a2 = new AuditTrailListener();
        REPO.reset();
        IdempotencyStore.getInstance().clear();
        TaskService s = new TaskService(REPO, ParserFactory.createParser(null, true), ParserFactory.createBreakdown(null, true));
        s.addListener(a2);
        s.removeListener(a2);
        new TaskApi(s).createTask(ok("b"), null);
        check("removed listener not notified", 0, a2.count(TaskEventListener.CREATED));
    }

    /** .env loading: parsing, quotes, comments, blanks, and the placeholder rule. Uses temp files. */
    private static void configChecks() {
        System.out.println("-- config (.env)");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("envtest");
            java.nio.file.Path f = dir.resolve(".env");
            java.nio.file.Files.writeString(f,
                    "# a comment\n\nLLM_API_KEY=\"abc=123\"\nLLM_PROVIDER='openai'\nLLM_MODEL=\nJUNKLINE\n");
            java.util.Map<String, String> v = EnvConfig.load(f);
            check("quoted value kept, '=' inside value kept", "abc=123", v.get("LLM_API_KEY"));
            check("single quotes stripped", "openai", v.get("LLM_PROVIDER"));
            check("blank value present but empty", "", v.get("LLM_MODEL"));
            check("comment and junk lines ignored", 3, v.size());
            check("missing file -> empty map, no crash", 0, EnvConfig.load(dir.resolve("nope.env")).size());
            java.nio.file.Files.deleteIfExists(f);
            java.nio.file.Files.deleteIfExists(dir);
        } catch (java.io.IOException e) {
            check("temp .env test setup", "ok", "IOException: " + e.getMessage());
        }
        check("placeholder not used as a key",
                true, EnvConfig.get("LLM_API_KEY") == null || !EnvConfig.get("LLM_API_KEY").equals("your-key-here"));
    }

    private static void regressions() {
        System.out.println("-- regressions");
        // 1) "1st" must match only the day-of-month 1st, not "21st" / "31st" or inside words.
        check("'21st' is not the 1st", null, parseDue("pay bills on the 21st"));
        check("'31st' is not the 1st", null, parseDue("renew on the 31st"));
        check("'first' word is not '1st'", null, parseDue("the first draft"));
        check("'on the 1st' still works", true, parseDue("pay on the 1st") != null
                && parseDue("pay on the 1st").endsWith("-01"));
        check("'1st' inside a longer number", null, parseDue("order 11st batch"));

        // 2) recurrence is stored on the task and returned by the API.
        TaskApi api = fresh(null, true);
        ApiResponse rec = api.createTaskFromText("Remind me to pay rent on the 1st of every month", null);
        check("recurrence stored -> monthly", true, has(rec, "\"recurrence\":\"monthly\""));
        check("recurrence present on plain task as null", true,
                has(fresh(null, true).createTask(ok("plain"), null), "\"recurrence\":null"));

        // 3) idempotency lock is shared across TaskApi instances over the same store.
        final int[] racers = {0};
        SlowLlmClient slow = new SlowLlmClient(120);
        TaskService s = new TaskService(REPO, ParserFactory.createParser(slow, false),
                ParserFactory.createBreakdown(slow, false));
        REPO.reset();
        IdempotencyStore.getInstance().clear();
        TaskApi a1 = new TaskApi(s);
        TaskApi a2 = new TaskApi(s);
        Thread t1 = new Thread(() -> a1.createTaskFromText("pay rent", "race"));
        Thread t2 = new Thread(() -> a2.createTaskFromText("pay rent", "race"));
        t1.start();
        t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        check("two APIs racing on one key -> one model call", 1, slow.getCalls());
        check("two APIs racing on one key -> one task", 1, REPO.findAll().size());
    }

    private static String parseDue(String text) {
        return new RuleBasedTaskParser().parse(text, "2026-11-27").getDueDate();
    }

    /** Model stand-in that waits so two concurrent calls overlap. */
    static class SlowLlmClient implements LlmClient {
        private final long delayMs;
        private int calls = 0;
        SlowLlmClient(long delayMs) { this.delayMs = delayMs; }
        @Override
        public synchronized String send(String prompt) throws java.io.IOException {
            calls++;
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "{\"title\":\"Pay rent\",\"description\":\"d\",\"dueDate\":null,\"recurrence\":null}";
        }
        synchronized int getCalls() { return calls; }
    }

    static void check(String name, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  (expected: " + expected + ", actual: " + actual + ")");
        }
    }
}
