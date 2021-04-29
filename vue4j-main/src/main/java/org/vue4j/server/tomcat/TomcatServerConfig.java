/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.server.tomcat;

import java.util.List;
import java.util.Map;
import org.vue4j.config.ConfigDescription;

/**
 *
 * @author vince
 */
public interface TomcatServerConfig {

    @ConfigDescription(
            value = "Server public URI",
            defaultString = "http://localhost:8666/"
    )
    public String publicURI();

    @ConfigDescription(
            value = "Available application language list",
            defaultList = {"en", "fr"}
    )
    public List<String> availableLanguages();

    @ConfigDescription(
            value = "Tomcat system properties"
    )
    public Map<String, String> tomcatSystemProperties();

    @ConfigDescription(
            value = "Enable Tomcat anti-thread lock mechanism with StuckThreadDetectionValve",
            defaultBoolean = true
    )
    public boolean enableAntiThreadLock();

    @ConfigDescription(
            value = "Application path prefix, must start with '/' and do not end with '/' or be an empty string",
            defaultString = ""
    )
    String pathPrefix();
}
