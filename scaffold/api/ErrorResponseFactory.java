package assignment_task_manager.scaffold.api;

import assignment_task_manager.scaffold.exception.ApiException;

/**
 * INSTRUCTOR-PROVIDED SIGNATURES ONLY (Step 3) - students write the bodies.
 *
 * The one place where the error shape is built. Target shape:
 *
 * {
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "Validation failed",
 *     "details": [ { "field": "title", "issue": "must not be empty" } ]
 *   }
 * }
 *
 * Hints:
 *  - Build the body with LinkedHashMap / ArrayList (see ListMapEnumPrimer).
 *  - "code" and "message" are ALWAYS Strings.
 *  - "details" is only filled for ValidationException (use instanceof);
 *    for every other exception send an empty list.
 *  - The ApiResponse status comes from e.getStatus().
 *
 * When you copy this into your own project, change the package line to: package api;
 */
public class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    /** Known failure (400 / 404 / 422 / 502 / 503). */
    public static ApiResponse from(ApiException e) {
        // TODO (student): build the error body and return new ApiResponse(e.getStatus(), body)
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Anything unexpected: 500, code "INTERNAL_ERROR", same shape. Never leak a stack trace. */
    public static ApiResponse fromUnexpected(Exception e) {
        // TODO (student)
        throw new UnsupportedOperationException("not implemented yet");
    }
}
