package app;

import parser.LlmClient;

/** Ready-made bad model behaviours, for hardening tests. */
public final class FaultyLlmClient {
    private FaultyLlmClient() {
    }

    public static FakeLlmClient emptyTitle() {
        return new FakeLlmClient("{\"title\":\"  \",\"description\":\"d\",\"dueDate\":null,\"recurrence\":null}");
    }

    public static FakeLlmClient impossibleDate() {
        return new FakeLlmClient("{\"title\":\"t\",\"description\":\"d\",\"dueDate\":\"2026-02-30\",\"recurrence\":null}");
    }

    public static FakeLlmClient malformed() {
        return new FakeLlmClient("Sure! Here is your task: pay rent");
    }

    public static FakeLlmClient throwing() {
        return FakeLlmClient.throwing();
    }

    /** Breakdown reply where one line is invalid (empty title). */
    public static FakeLlmClient badBreakdownItem() {
        return new FakeLlmClient("Good step|Fine\n |no title here");
    }
}
