package parser;

import exception.BadGatewayException;
import model.Task;
import model.TaskRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Asks the model for subtasks, one per line as title|description. */
public class LlmBreakdownStrategy implements BreakdownStrategy {
    private final LlmClient client;

    public LlmBreakdownStrategy(LlmClient client) {
        this.client = client;
    }

    static String buildPrompt(Task task) {
        return "Break the following task into 2 to 6 concrete subtasks.\n"
                + "Reply with ONLY the subtasks, one per line, in exactly this format:\n"
                + "title|description\n"
                + "No numbering, no bullets, no markdown, no extra text.\n"
                + "Task title: " + task.getTitle() + "\n"
                + "Task description: " + task.getDescription();
    }

    @Override
    public List<TaskRequest> breakDown(Task task) {
        String reply;
        try {
            reply = client.send(buildPrompt(task));
        } catch (IOException e) {
            throw new BadGatewayException("Model call failed: " + e.getMessage());
        }
        if (reply == null || reply.isBlank()) {
            throw new BadGatewayException("Model returned an empty reply");
        }
        List<TaskRequest> result = new ArrayList<>();
        for (String line : reply.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            int bar = line.indexOf('|');
            if (bar < 0) {
                throw new BadGatewayException("Model reply line is not in title|description format: " + line.trim());
            }
            String title = line.substring(0, bar).trim();
            String description = line.substring(bar + 1).trim();
            result.add(new TaskRequest(title, description, "false", null, task.getPriority().name()));
        }
        if (result.isEmpty()) {
            throw new BadGatewayException("Model returned no subtasks");
        }
        return result;
    }
}
