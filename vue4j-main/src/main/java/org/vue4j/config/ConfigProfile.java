package org.vue4j.config;

public enum ConfigProfile {
    PROD,
    DEV,
    TEST;
    
    public String getProfileID() {
        return this.name().toLowerCase();
    }
    
    public boolean match(String id) {
        return getProfileID().equalsIgnoreCase(id);
    }
}
