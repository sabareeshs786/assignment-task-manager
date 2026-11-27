package parser;

/**
 * Strategy interface: turn free text into a ParsedTask.
 * Implementations throw ApiException subclasses (e.g. BadGatewayException) on failure.
 */
public interface TaskParser {
    /** @param today the current date as YYYY-MM-DD, so "next Friday" can be resolved */
    ParsedTask parse(String text, String today);
}
