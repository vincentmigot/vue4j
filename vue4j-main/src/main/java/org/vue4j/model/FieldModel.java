package org.vue4j.model;

import com.github.jsonldjava.shaded.com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class FieldModel {

    private String id;

    private URI dataType;

    private boolean inherited;

    private boolean required;

    private int maxCardinality;

    private Map<String, String> names;

    private Collection<FieldRestrictionModel> restrictions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public URI getDataType() {
        return dataType;
    }

    public void setDataType(URI dataType) {
        this.dataType = dataType;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getMaxCardinality() {
        return maxCardinality;
    }

    public void setMaxCardinality(int maxCardinality) {
        this.maxCardinality = maxCardinality;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public Collection<FieldRestrictionModel> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Collection<FieldRestrictionModel> restrictions) {
        this.restrictions = restrictions;
    }

    public FieldModel getCopy() {
        FieldModel inheritedCopy = new FieldModel();
        inheritedCopy.setId(this.getId());
        inheritedCopy.setDataType(this.getDataType());
        inheritedCopy.setInherited(this.isInherited());
        inheritedCopy.setRequired(this.isRequired());
        inheritedCopy.setMaxCardinality(this.getMaxCardinality());
        inheritedCopy.setNames(deepCopyMap(this.getNames()));
        inheritedCopy.setRestrictions(deepCopyCollection(this.getRestrictions()));

        return inheritedCopy;
    }

    private static <T> Map<String, T> deepCopyMap(Map<String, T> map) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(map);

        Type type = new TypeToken<HashMap<String, T>>() {
        }.getType();

        return gson.fromJson(jsonString, type);
    }

    private static <T> Collection<T> deepCopyCollection(Collection<T> collection) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(collection);

        Type type = new TypeToken<HashSet<T>>() {
        }.getType();

        return gson.fromJson(jsonString, type);
    }

}
