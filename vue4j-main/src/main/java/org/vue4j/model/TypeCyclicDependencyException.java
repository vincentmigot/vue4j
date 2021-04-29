/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.model;

import java.net.URI;
import java.util.ArrayDeque;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author vince
 */
public class TypeCyclicDependencyException extends RuntimeException {

    private final ArrayDeque<URI> dependencies = new ArrayDeque<>();

    private String baseMessage;

    public TypeCyclicDependencyException(URI type) {
        dependencies.add(type);
    }

    public TypeCyclicDependencyException getParentTypeCyclicDependencyException(URI rdfType) {
        dependencies.addFirst(rdfType);
        baseMessage = "Parent cyclic dependency: ";
        return this;
    }

    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder(baseMessage);
        builder.append(StringUtils.join(dependencies, " -> "));
        return builder.toString();
    }

}
