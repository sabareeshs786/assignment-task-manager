package parser;

import model.Task;
import model.TaskRequest;

import java.util.ArrayList;
import java.util.List;

/** Offline: splits the title on commas and the word "and". */
public class RuleBasedBreakdownStrategy implements BreakdownStrategy {
    @Override
    public List<TaskRequest> breakDown(Task task) {
        String normalized = task.getTitle().replace(",", " and ");
        List<TaskRequest> result = new ArrayList<>();
        for (String part : normalized.split("(?i)\\s+and\\s+")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                result.add(new TaskRequest(p, "Part of: " + task.getTitle(), "false", null,
                        task.getPriority().name()));
            }
        }
        return result;
    }
}
