/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.model;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author vince
 */
public class TypeFieldConflictsException extends RuntimeException {

    private final Map<String, Set<String>> fieldsConflicts = new HashMap<>();
    private final URI type;

    public TypeFieldConflictsException(URI type) {
        this.type = type;
    }

    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder("Conflict between inherited field for type: " + type.toString());
        fieldsConflicts.forEach((field, conflicts) -> {
            conflicts.forEach(conflict -> {
                builder.append("\n - " + field + ": " + conflict);
            });
        });
        return builder.toString();
    }

    public void addConflict(String fieldID, String message) {
        if (!fieldsConflicts.containsKey(fieldID)) {
            fieldsConflicts.put(fieldID, new HashSet<>());
        }
        fieldsConflicts.get(fieldID).add(message);
    }
    
    public boolean hasConflicts() {
        return fieldsConflicts.size() > 0;
    }

}
