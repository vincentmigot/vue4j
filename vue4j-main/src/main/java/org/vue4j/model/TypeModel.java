package org.vue4j.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TypeModel {

    private URI uri;

    private Map<String, String> names;

    private TypeModel parent;

    private Collection<TypeModel> children;

    private Map<String, FieldModel> fields;

    private Collection<TypeRestrictionModel> restrictions;

    private Collection<TypeIndexModel> indexes;
    
    private List<String> fieldsOrder;

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public TypeModel getParent() {
        return parent;
    }

    public void setParent(TypeModel parent) {
        this.parent = parent;
    }

    public Collection<TypeModel> getChildren() {
        return children;
    }

    public void setChildren(Collection<TypeModel> children) {
        this.children = children;
    }

    public Map<String, FieldModel> getFields() {
        return fields;
    }

    public void setFields(Map<String, FieldModel> fields) {
        this.fields = fields;
    }

    public Collection<TypeRestrictionModel> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Collection<TypeRestrictionModel> restrictions) {
        this.restrictions = restrictions;
    }

    public Collection<TypeIndexModel> getIndexes() {
        return indexes;
    }

    public void setIndexes(Collection<TypeIndexModel> indexes) {
        this.indexes = indexes;
    }

    public List<String> getFieldsOrder() {
        return fieldsOrder;
    }

    public void setFieldsOrder(List<String> fieldsOrder) {
        this.fieldsOrder = fieldsOrder;
    }
    
    

}
