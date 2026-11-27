package assignment_task_manager.scaffold.app;

import assignment_task_manager.scaffold.service.DateHelper;

import java.util.Objects;

/**
 * INSTRUCTOR-PROVIDED EXAMPLE (Step 9): one finished check, to copy as a pattern.
 *
 * Pattern for every check:   call  ->  compare  ->  print PASS or FAIL
 *
 * Students extend this with checks against THEIR TaskApi, e.g. (once the API exists):
 *
 *   TaskApi api = new TaskApi(new TaskService(repo, ...));
 *   ApiResponse r = api.getTask(999);
 *   check("getTask unknown id -> status 404", 404, r.getStatus());
 *   check("getTask unknown id -> code NOT_FOUND", true, r.toJson().contains("\"code\":\"NOT_FOUND\""));
 *
 * Tests must run with NO key and NO internet - plug in FakeLlmClient / FaultyLlmClient.
 * Reset shared state (the Singleton repository and idempotency store!) between checks.
 *
 * When you copy this into your own project, change the package line to: package app;
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- the one finished check ----
        check("2026-02-30 is rejected as a date", false, DateHelper.isValidDate("2026-02-30"));

        // ---- students: add the rest from your test checklist ----

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** call -> compare -> print. 'expected' and 'actual' can be any type; equals() decides. */
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
