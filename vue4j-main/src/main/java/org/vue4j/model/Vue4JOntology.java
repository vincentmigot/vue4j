/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.model;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_ZeroOrMore1;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.vocabulary.RDFS;

/**
 *
 * @author vince
 */
public class Vue4JOntology {

    public static final String NS_TYPE = "https://vue4j.org/type";
    public static final String NS_TYPE_FIELD = "https://vue4j.org/type/field";

    public static final Resource Type = resource(NS_TYPE, "Type");
    public static final Property hasField = property(NS_TYPE, "hasField");
    
    public static final Resource Field = resource(NS_TYPE_FIELD, "Field");
    public static final Resource FieldRestriction = resource(NS_TYPE_FIELD, "FieldRestriction");
    public static final Property hasFieldID = property(NS_TYPE_FIELD, "hasID");
    public static final Property isFieldRequired = property(NS_TYPE_FIELD, "isRequired");
    public static final Property hasFieldMaxCardinality = property(NS_TYPE_FIELD, "hasMaxCardinality");
    public static final Property hasFieldDataType = property(NS_TYPE_FIELD, "hasDataType");
    public static final Property hasFieldRestriction = property(NS_TYPE_FIELD, "hasRestriction");

    public static final Path subClassAny;

    static {
        subClassAny = new P_ZeroOrMore1(new P_Link(RDFS.subClassOf.asNode()));
    }

    public static final Resource resource(String namespace, String local) {
        return ResourceFactory.createResource(namespace + "#" + local);
    }

    public static final Property property(String namespace, String local) {
        return ResourceFactory.createProperty(namespace + "#" + local);
    }
}
