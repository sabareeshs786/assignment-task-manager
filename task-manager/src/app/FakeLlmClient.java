package app;

import parser.LlmClient;

import java.io.IOException;

/** Test double: returns a fixed reply (or throws), and counts calls. */
public class FakeLlmClient implements LlmClient {
    private final String reply;
    private final boolean fail;
    private int calls = 0;

    public FakeLlmClient(String reply) {
        this.reply = reply;
        this.fail = false;
    }

    private FakeLlmClient(boolean fail) {
        this.reply = null;
        this.fail = fail;
    }

    /** A client whose call always fails (like a timeout). */
    public static FakeLlmClient throwing() {
        return new FakeLlmClient(true);
    }

    @Override
    public String send(String prompt) throws IOException {
        calls++;
        if (fail) {
            throw new IOException("simulated timeout");
        }
        return reply;
    }

    public int getCalls() { return calls; }
}
