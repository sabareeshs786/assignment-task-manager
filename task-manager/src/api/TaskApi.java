package api;

import exception.ApiException;
import exception.ConflictException;
import model.Task;
import model.TaskRequest;
import repository.IdempotencyStore;
import service.DateHelper;
import service.TaskQuery;
import service.TaskService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Public operations. Always returns an ApiResponse; never throws. */
public class TaskApi {
    private final TaskService service;
    private final IdempotencyStore store = IdempotencyStore.getInstance();

    public TaskApi(TaskService service) {
        this.service = service;
    }

    public ApiResponse createTask(TaskRequest request, String idempotencyKey) {
        return idempotent("createTask", fingerprint(request), idempotencyKey,
                () -> new ApiResponse(201, service.create(request).toMap()));
    }

    public ApiResponse createTaskFromText(String text, String idempotencyKey) {
        return idempotent("createTaskFromText", field(text), idempotencyKey,
                () -> new ApiResponse(201, service.createFromText(text, DateHelper.today()).toMap()));
    }

    public ApiResponse breakDownTask(int id, String idempotencyKey) {
        return idempotent("breakDownTask", field(String.valueOf(id)), idempotencyKey, () -> {
            List<Object> items = new ArrayList<>();
            for (Task t : service.breakDown(id)) {
                items.add(t.toMap());
            }
            return new ApiResponse(201, items);
        });
    }

    public ApiResponse getTask(int id) {
        return run(() -> new ApiResponse(200, service.get(id).toMap()));
    }

    public ApiResponse listTasks(TaskQuery query) {
        return run(() -> new ApiResponse(200, service.list(query)));
    }

    public ApiResponse updateTask(int id, TaskRequest request) {
        return run(() -> new ApiResponse(200, service.update(id, request).toMap()));
    }

    public ApiResponse deleteTask(int id) {
        return run(() -> {
            int removed = service.delete(id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deleted", true);
            body.put("id", id);
            body.put("tasksRemoved", removed);
            return new ApiResponse(200, body);
        });
    }

    public ApiResponse createSubtask(int parentId, TaskRequest request) {
        return run(() -> new ApiResponse(201, service.createSubtask(parentId, request).toMap()));
    }

    public ApiResponse listSubtasks(int parentId) {
        return run(() -> new ApiResponse(200, toMaps(service.listSubtasks(parentId))));
    }

    public ApiResponse listByPriority(String level) {
        return run(() -> new ApiResponse(200, toMaps(service.listByPriority(level))));
    }

    // ---------- helpers ----------

    private static List<Object> toMaps(List<Task> tasks) {
        List<Object> out = new ArrayList<>();
        for (Task t : tasks) {
            out.add(t.toMap());
        }
        return out;
    }

    /** Wraps one operation: ApiException -> error shape, anything else -> 500 in the same shape. */
    private ApiResponse run(Supplier<ApiResponse> op) {
        try {
            return op.get();
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.fromUnexpected(e);
        }
    }

    /** check -> work -> store (only successes). Synchronized so check-then-store is atomic. */
    private ApiResponse idempotent(String operation, String fp, String key, Supplier<ApiResponse> work) {
        // Lock the shared store, not this TaskApi: several TaskApi objects can share one store.
        synchronized (store) {
            return idempotentLocked(operation, fp, key, work);
        }
    }

    private ApiResponse idempotentLocked(String operation, String fp, String key, Supplier<ApiResponse> work) {
        return run(() -> {
            String fingerprint = field(operation) + fp;
            if (key != null) {
                IdempotencyStore.Record seen = store.find(key);
                if (seen != null) {
                    if (seen.getFingerprint().equals(fingerprint)) {
                        return seen.getResponse();
                    }
                    throw new ConflictException("Idempotency key reused with a different request");
                }
            }
            ApiResponse response = work.get();
            if (key != null) {
                store.save(key, fingerprint, response);
            }
            return response;
        });
    }

    /** length:value so ("a|b","c") and ("a","b|c") can never look the same. */
    private static String field(String v) {
        return v == null ? "-;" : v.length() + ":" + v + ";";
    }

    private static String fingerprint(TaskRequest r) {
        if (r == null) {
            return "null;";
        }
        return field(r.getTitle()) + field(r.getDescription()) + field(r.getCompleted())
                + field(r.getDueDate()) + field(r.getPriority());
    }
}
