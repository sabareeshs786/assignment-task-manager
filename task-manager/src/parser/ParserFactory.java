package parser;

import config.EnvConfig;
import exception.ServiceUnavailableException;
import model.Task;
import model.TaskRequest;

import java.util.List;

/** Factory: picks the LLM strategy when a key exists, else the rule-based one (if allowed) or a 503. */
public class ParserFactory {
    private ParserFactory() {
    }

    /** Reads LLM_API_KEY, LLM_PROVIDER, LLM_MODEL from the environment. */
    public static LlmClient realClient() {
        String key = EnvConfig.get("LLM_API_KEY");
        if (key == null) {
            return null;
        }
        return new HttpLlmClient(key, EnvConfig.get("LLM_PROVIDER"), EnvConfig.get("LLM_MODEL"));
    }

    public static TaskParser createParser(boolean allowRuleBasedFallback) {
        return createParser(realClient(), allowRuleBasedFallback);
    }

    /** Plug in any client (e.g. a fake). A null client means "no key". */
    public static TaskParser createParser(LlmClient client, boolean allowRuleBasedFallback) {
        if (client != null) {
            return new LlmTaskParser(client);
        }
        if (allowRuleBasedFallback) {
            return new RuleBasedTaskParser();
        }
        return (text, today) -> {
            throw new ServiceUnavailableException("LLM_API_KEY is not set");
        };
    }

    public static BreakdownStrategy createBreakdown(boolean allowRuleBasedFallback) {
        return createBreakdown(realClient(), allowRuleBasedFallback);
    }

    public static BreakdownStrategy createBreakdown(LlmClient client, boolean allowRuleBasedFallback) {
        if (client != null) {
            return new LlmBreakdownStrategy(client);
        }
        if (allowRuleBasedFallback) {
            return new RuleBasedBreakdownStrategy();
        }
        return new BreakdownStrategy() {
            @Override
            public List<TaskRequest> breakDown(Task task) {
                throw new ServiceUnavailableException("LLM_API_KEY is not set");
            }
        };
    }
}
