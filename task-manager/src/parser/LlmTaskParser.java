package parser;

import exception.BadGatewayException;

import java.io.IOException;
import java.util.Map;

/** Asks an LLM to turn text into a task. The reply is untrusted and only parsed here. */
public class LlmTaskParser implements TaskParser {
    private final LlmClient client;

    public LlmTaskParser(LlmClient client) {
        this.client = client;
    }

    static String buildPrompt(String text, String today) {
        return "You convert a user's request into one task.\n"
                + "Today's date is " + today + " (format YYYY-MM-DD). Use it to resolve relative dates "
                + "such as \"tomorrow\" or \"next Friday\".\n"
                + "Reply with ONLY a single flat JSON object, no markdown, no commentary, with exactly these keys:\n"
                + "  \"title\": short imperative summary (string),\n"
                + "  \"description\": one sentence of detail (string),\n"
                + "  \"dueDate\": date as YYYY-MM-DD, or null if none,\n"
                + "  \"recurrence\": e.g. \"monthly\", \"weekly\", or null if it does not repeat.\n"
                + "User request: " + text;
    }

    @Override
    public ParsedTask parse(String text, String today) {
        String reply;
        try {
            reply = client.send(buildPrompt(text, today));
        } catch (IOException e) {
            throw new BadGatewayException("Model call failed: " + e.getMessage());
        }
        Map<String, String> fields;
        try {
            fields = SimpleJson.parseFlat(reply);
        } catch (IllegalArgumentException e) {
            throw new BadGatewayException("Model returned malformed JSON: " + e.getMessage());
        }
        return new ParsedTask(fields.get("title"), fields.get("description"),
                fields.get("dueDate"), fields.get("recurrence"));
    }
}
