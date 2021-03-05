package org.vue4j.cli;

import org.vue4j.Vue4J;

public interface Vue4JCommand {

    /**
     * Define application instance.
     *
     * @param vue4j
     */
    public void setVue4J(Vue4J vue4j);

    /**
     * Get application instance.
     *
     * @return application instance
     */
    public Vue4J getVue4J();
}
