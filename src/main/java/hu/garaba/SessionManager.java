package hu.garaba;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface SessionManager {
    Set<Map.Entry<Long, Session>> sessions();
    Session get(long id);
    Session getOrCreate(long id, Function<Long, Session> f);
}
