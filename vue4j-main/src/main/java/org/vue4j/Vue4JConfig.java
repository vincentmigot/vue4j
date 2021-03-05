package org.vue4j;

import org.vue4j.config.ConfigDescription;

public interface Vue4JConfig {

    public final static String YAML_KEY = "system";

    @ConfigDescription(
            value = "Default application language",
            defaultString = "en"
    )
    public String defaultLanguage();

}
