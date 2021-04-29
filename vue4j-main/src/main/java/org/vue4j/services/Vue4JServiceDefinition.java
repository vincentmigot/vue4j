package org.vue4j.services;

import org.vue4j.modules.*;

public @interface Vue4JServiceDefinition {

    public String id();

    public Class<?> defaultImplementation();

}
