package org.vue4j.services;

import org.vue4j.config.ConfigManager;

public class ServiceManager {
    
    private final ConfigManager configManager;
    
    public ServiceManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public void registerService(String ID, Class<? extends Vue4JService> iService) {
        
    }
    
}
