package assignment_task_manager.scaffold.exception;

/**
 * INSTRUCTOR-PROVIDED SKELETON (Step 3). 422 / CONFLICT.
 * Used when an idempotency key is reused with a DIFFERENT request.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(422, "CONFLICT", message);
    }
}
