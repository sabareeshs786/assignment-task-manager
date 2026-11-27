package parser;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Offline parser using only String methods: handles "tomorrow", "every month" and "1st". */
public class RuleBasedTaskParser implements TaskParser {
    /** Whole word "1st" only, so "21st", "31st" and "11st" do not match. */
    private static final Pattern FIRST_OF_MONTH = Pattern.compile("\\b1st\\b");

    @Override
    public ParsedTask parse(String text, String today) {
        String clean = text == null ? "" : text.trim();
        String lower = clean.toLowerCase();
        LocalDate base = LocalDate.parse(today);

        String recurrence = null;
        if (lower.contains("every month")) {
            recurrence = "monthly";
        } else if (lower.contains("every week")) {
            recurrence = "weekly";
        } else if (lower.contains("every day")) {
            recurrence = "daily";
        }

        String dueDate = null;
        if (lower.contains("tomorrow")) {
            dueDate = base.plusDays(1).toString();
        } else if (FIRST_OF_MONTH.matcher(lower).find()) {
            // the next 1st of a month, strictly after today
            dueDate = base.withDayOfMonth(1).plusMonths(1).toString();
        }

        String title = clean;
        if (title.toLowerCase().startsWith("remind me to ")) {
            title = title.substring("remind me to ".length());
        }
        return new ParsedTask(title, clean, dueDate, recurrence);
    }
}
