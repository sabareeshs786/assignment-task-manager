package api;

import exception.ApiException;
import exception.BadGatewayException;
import exception.FieldIssue;
import exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Factory: the one place where the error shape is built. */
public class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    /** Known failure (400 / 404 / 422 / 502 / 503). */
    public static ApiResponse from(ApiException e) {
        List<FieldIssue> issues = new ArrayList<>();
        if (e instanceof ValidationException ve) {
            issues = ve.getIssues();
        } else if (e instanceof BadGatewayException be) {
            issues = be.getIssues();
        }
        return new ApiResponse(e.getStatus(), body(e.getCode(), e.getMessage(), issues));
    }

    /** Anything unexpected: 500, INTERNAL_ERROR, same shape. Never leaks a stack trace. */
    public static ApiResponse fromUnexpected(Exception e) {
        return new ApiResponse(500, body("INTERNAL_ERROR", "Unexpected internal error", new ArrayList<>()));
    }

    private static Map<String, Object> body(String code, String message, List<FieldIssue> issues) {
        List<Object> details = new ArrayList<>();
        for (FieldIssue i : issues) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("field", i.getField());
            d.put("issue", i.getIssue());
            details.add(d);
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("error", error);
        return root;
    }
}
