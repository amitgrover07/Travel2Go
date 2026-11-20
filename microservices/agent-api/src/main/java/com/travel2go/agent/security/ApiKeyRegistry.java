package com.travel2go.agent.security;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Constant-time-ish lookup of a presented API key to its id + scopes. Built once
 * from configuration. Unknown keys resolve to empty (-> 401 at the filter).
 */
@Component
public class ApiKeyRegistry {

    private final Map<String, AgentKey> byKeyValue = new HashMap<>();

    public ApiKeyRegistry(ApiKeyProperties properties) {
        for (ApiKeyProperties.KeyEntry entry : properties.getKeys()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            Set<String> scopes = new LinkedHashSet<>(entry.getScopes());
            byKeyValue.put(entry.getKey(), new AgentKey(entry.getId(), scopes));
        }
    }

    public Optional<AgentKey> resolve(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKeyValue.get(presentedKey));
    }

    /** A resolved agent identity. */
    public record AgentKey(String id, Set<String> scopes) {
    }
}
