package org.vue4j.config;

/**
 * Excption in case of invalid configuration detection.
 *
 * @author Vincent Migot
 */
public class InvalidConfigException extends Exception {

    /**
     * Constructor based on another exception.
     *
     * @param ex Exception causing invalid configuration
     */
    public InvalidConfigException(Exception ex) {
        super(ex);
    }

    /**
     * Constructor based on a direct message.
     *
     * @param message Message of the exception
     */
    public InvalidConfigException(String message) {
        super(message);
    }

}
