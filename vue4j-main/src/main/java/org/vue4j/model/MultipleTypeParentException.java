/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.model;

import java.util.Collection;

/**
 *
 * @author vince
 */
public class MultipleTypeParentException extends RuntimeException {

    private final Collection<String> parents;

    private final String type;

    public MultipleTypeParentException(String type, Collection<String> parents) {
        this.type = type;
        this.parents = parents;
    }

    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder("Multiple parents found for type: " + type);
        for (String parent : parents) {
            builder.append("\n - " + parent);
        }

        return builder.toString();
    }

}
