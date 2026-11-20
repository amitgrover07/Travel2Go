package com.travel2go.agent.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent API keys and their least-privilege scopes, configured outside code
 * (environment / secret manager). Each entry binds a stable key id (for auditing)
 * to a secret value and the scopes it is allowed to exercise.
 */
@ConfigurationProperties(prefix = "agent.api")
public class ApiKeyProperties {

    /** Registered agent keys. */
    private List<KeyEntry> keys = new ArrayList<>();

    public List<KeyEntry> getKeys() {
        return keys;
    }

    public void setKeys(List<KeyEntry> keys) {
        this.keys = keys;
    }

    public static class KeyEntry {
        /** Stable identifier used in audit logs - never the secret itself. */
        private String id;
        /** The secret key value an agent presents in the X-Agent-Key header. */
        private String key;
        /** Granted scopes, e.g. read, book. */
        private List<String> scopes = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }
    }
}
