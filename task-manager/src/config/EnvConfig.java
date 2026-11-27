package config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads settings (LLM_API_KEY, LLM_PROVIDER, LLM_MODEL) from a .env file, with real
 * environment variables taking priority. The .env file is never printed or logged.
 *
 * Format, one per line:   KEY=value   (blank lines and lines starting with # are ignored;
 * surrounding single or double quotes around the value are removed).
 */
public final class EnvConfig {
    private static final Path DEFAULT_FILE = Path.of(".env");
    private static final Map<String, String> FILE_VALUES = load(DEFAULT_FILE);

    private EnvConfig() {
    }

    /** The placeholder text from .env.example. Treated as "not set" so the shipped file is harmless. */
    static final String PLACEHOLDER_KEY = "your-key-here";

    /** Environment variable if set and non-blank, otherwise the value from .env, otherwise null. */
    public static String get(String name) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = FILE_VALUES.get(name);
        if (fromFile == null || fromFile.isBlank()) {
            return null;
        }
        if ("LLM_API_KEY".equals(name) && PLACEHOLDER_KEY.equals(fromFile.trim())) {
            return null;
        }
        return fromFile;
    }

    /** Parses a .env file. A missing file is not an error: the environment is used instead. */
    public static Map<String, String> load(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            // Unreadable .env: fall back to the environment only. The key is never echoed.
            return new HashMap<>();
        }
        return values;
    }
}
