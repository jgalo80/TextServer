package net.jgn.cliptext.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jose
 */
public class Session {

    private Map<String, String> data;
    private String id;

    public Session() {
        id = UUID.randomUUID().toString();
        data = new ConcurrentHashMap<>();
    }

    public String getId() {
        return id;
    }

    public void put(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }
}
