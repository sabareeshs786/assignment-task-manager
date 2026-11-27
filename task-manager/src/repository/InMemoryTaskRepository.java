package repository;

import model.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Singleton in-memory store. Ids start at 1. */
public class InMemoryTaskRepository implements TaskRepository {
    private static final InMemoryTaskRepository INSTANCE = new InMemoryTaskRepository();

    private final Map<Integer, Task> tasks = new LinkedHashMap<>();
    private int nextId = 1;

    private InMemoryTaskRepository() {
    }

    public static InMemoryTaskRepository getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized Task save(Task task) {
        Task toStore = task;
        if (task.getId() == 0) {
            toStore = task.toBuilder().id(nextId++).build();
        }
        tasks.put(toStore.getId(), toStore);
        return toStore;
    }

    @Override
    public synchronized Task findById(int id) {
        return tasks.get(id);
    }

    @Override
    public synchronized List<Task> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public synchronized boolean delete(int id) {
        return tasks.remove(id) != null;
    }

    /** For tests/demos: empties the store and restarts ids at 1. */
    public synchronized void reset() {
        tasks.clear();
        nextId = 1;
    }
}
