package org.vue4j.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import static org.apache.jena.arq.querybuilder.Converters.makeVar;
import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.E_OneOf;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.vue4j.sparql.SPARQLResult;
import org.vue4j.sparql.SPARQLService;

public class TypeService {

    @Inject
    SPARQLService sparql;

    private static ExprFactory exprFactory = new ExprFactory();

    private Map<String, TypeModel> loadedTypes = new HashMap<>();
    private Collection<String> loadingTypes = new HashSet<>();

    public TypeModel getType(URI rdfType) {
        URI formatedURI = sparql.formatURI(rdfType);
        String formatedURIString = formatedURI.toString();

        if (!loadedTypes.containsKey(formatedURIString)) {
            loadedTypes.put(formatedURIString, loadType(formatedURI));
        }

        return loadedTypes.get(formatedURIString);
    }

    private TypeModel loadType(URI rdfType) {
        String formatedURIString = rdfType.toString();
        if (loadingTypes.contains(formatedURIString)) {
            throw new TypeCyclicDependencyException(rdfType);
        }
        loadingTypes.add(formatedURIString);
        TypeModel type = new TypeModel();
        type.setUri(rdfType);

        Node typeNode = NodeFactory.createURI(rdfType.toString());
        type.setNames(loadTypeNames(typeNode));
        try {
            type.setParent(loadTypeParent(typeNode));
        } catch (TypeCyclicDependencyException ex) {
            throw ex.getParentTypeCyclicDependencyException(rdfType);
        }
        loadingTypes.remove(formatedURIString);

        type.setFields(loadTypeFields(typeNode, type));

        type.setFieldsOrder(loadTypeFieldsOrder(typeNode, type));
        // TODO load fields order (parent inheritence)
        // TODO load contraints (parent inheritence)
        // TODO load indexes (parent inheritence)
        // TODO load children ?
        return type;
    }

    private Map<String, String> loadTypeNames(Node typeNode) {
        SelectBuilder select = new SelectBuilder();

        Var uriVar = makeVar("uri");
        select.addWhere(uriVar, Vue4JOntology.subClassAny, Vue4JOntology.Type);
        select.addWhereValueVar(uriVar, typeNode);

        Var nameVar = makeVar("name");
        select.addVar(nameVar);
        select.addWhere(uriVar, RDFS.label, nameVar);

        Var langVar = makeVar("lang");
        select.addBind(exprFactory.lang(nameVar), langVar);

        Map<String, String> names = new HashMap<>();
        sparql.executeSelectQuery(select).forEach((row) -> {
            String lang = row.getStringValue(langVar.getVarName());
            String name = row.getStringValue(nameVar.getVarName());
            names.put(lang, name);
        });

        return names;
    }

    private TypeModel loadTypeParent(Node typeNode) {
        SelectBuilder select = new SelectBuilder();

        Var uriVar = makeVar("uri");
        select.addVar(uriVar);
        select.addWhere(uriVar, Vue4JOntology.subClassAny, Vue4JOntology.Type);
        select.addWhereValueVar(uriVar, typeNode);

        Var parentVar = makeVar("parent");
        select.addVar(parentVar);
        select.addWhere(uriVar, RDFS.subClassOf, parentVar);

        List<SPARQLResult> parentList = sparql.executeSelectQuery(select);
        if (parentList.size() > 1) {
            Set<String> parents = parentList.stream().map((result) -> {
                return result.getStringValue(parentVar.getVarName());
            }).collect(Collectors.toSet());
            throw new MultipleTypeParentException(typeNode.getURI(), parents);
        }

        if (parentList.size() == 0) {
            return null;
        }

        SPARQLResult row = parentList.get(0);
        URI uri = sparql.formatURI(row.getStringValue(uriVar.getVarName()));
        URI parentURI = sparql.formatURI(row.getStringValue(parentVar.getVarName()));
        if (!uri.equals(parentURI)) {
            return getType(parentURI);
        }

        return null;
    }

    private Map<String, FieldModel> loadTypeFields(Node typeNode, TypeModel type) {
        Map<String, FieldModel> fields = new HashMap<>();

        TypeModel parent = type.getParent();
        if (parent != null) {
            parent.getFields().forEach((key, field) -> {
                fields.put(key, field.getCopy());
            });
        }

        // Load type fields
        SelectBuilder select = new SelectBuilder();

        Var uriVar = makeVar("uri");
        select.addWhere(uriVar, Vue4JOntology.subClassAny, Vue4JOntology.Type);
        select.addWhereValueVar(uriVar, typeNode);

        Var fieldVar = makeVar("field");
        select.addVar(fieldVar);
        select.addWhere(uriVar, Vue4JOntology.hasField, fieldVar);
        select.addWhere(fieldVar, RDF.type, Vue4JOntology.Field);

        Var idVar = makeVar("id");
        select.addVar(idVar);
        select.addWhere(fieldVar, Vue4JOntology.hasFieldID, idVar);

        Var requiredVar = makeVar("required");
        select.addVar(requiredVar);
        select.addOptional(fieldVar, Vue4JOntology.isFieldRequired, requiredVar);

        Var maxCardinalityVar = makeVar("maxCardinality");
        select.addVar(maxCardinalityVar);
        select.addOptional(fieldVar, Vue4JOntology.hasFieldMaxCardinality, maxCardinalityVar);

        Var datatypeVar = makeVar("dataType");
        select.addVar(datatypeVar);
        select.addOptional(fieldVar, Vue4JOntology.hasFieldDataType, datatypeVar);

        Set<String> fieldsURI = new HashSet<>();
        
        TypeFieldConflictsException conflicts = new TypeFieldConflictsException(type.getUri());
        
        sparql.executeSelectQuery(select).forEach((row) -> {
            String fieldID = row.getStringValue(idVar.getVarName());
            FieldModel field = new FieldModel();
            field.setId(fieldID);

            fieldsURI.add(row.getStringValue(fieldVar.getVarName()));

            if (fields.containsKey(fieldID)) {
                FieldModel inheritedField = fields.get(fieldID);
                field.setInherited(true);
                field.setRequired(row.getBoolValue(requiredVar.getVarName(), inheritedField.isRequired()));
                field.setMaxCardinality(row.getIntValue(maxCardinalityVar.getVarName(), inheritedField.getMaxCardinality()));
                field.setDataType(sparql.formatURI(row.getStringValue(datatypeVar.getVarName(), inheritedField.getDataType().toString())));
                field.setNames(inheritedField.getNames());
                field.setRestrictions(inheritedField.getRestrictions());
                field.setRestrictions(new HashSet<>());
                if (inheritedField.isRequired() && !field.isRequired()) {
                    conflicts.addConflict(fieldID, "field is optional but parent defined it as required");
                }
                if (inheritedField.getMaxCardinality() < field.getMaxCardinality()) {
                    conflicts.addConflict(fieldID, "field has a max cardinality of " + field.getMaxCardinality() + " but parent defined it as " + inheritedField.getMaxCardinality() + " (must be lower or equals)");
                }
                if (!isChildDatatype(field.getDataType(), inheritedField.getDataType())) {
                    conflicts.addConflict(fieldID, "field as for data type " + field.getDataType() + " but parent defined it as " + inheritedField.getDataType() + " (must be the same or a subtype of)");
                }
            } else {
                field.setInherited(false);
                field.setRequired(row.getBoolValue(requiredVar.getVarName(), false));
                field.setMaxCardinality(row.getIntValue(maxCardinalityVar.getVarName(), 0));
                field.setDataType(sparql.formatURI(row.getStringValue(datatypeVar.getVarName(), XSD.xstring.toString())));
                field.setNames(new HashMap<>());
                field.setRestrictions(new HashSet<>());
            }

            fields.put(field.getId(), field);
        });
        
        if (conflicts.hasConflicts()) {
            throw conflicts;
        }

        E_OneOf fieldFilter = exprFactory.in(fieldVar, fieldsURI.toArray());

        // Load fields names
        select = new SelectBuilder();
        select.addWhere(fieldVar, RDF.type, Vue4JOntology.Field);
        select.addFilter(fieldFilter);

        select.addVar(idVar);
        select.addWhere(fieldVar, Vue4JOntology.hasFieldID, idVar);

        Var nameVar = makeVar("name");
        select.addVar(nameVar);
        select.addWhere(fieldVar, RDFS.label, nameVar);

        Var langVar = makeVar("lang");
        select.addBind(exprFactory.lang(nameVar), langVar);

        sparql.executeSelectQuery(select).forEach((row) -> {
            String fieldID = row.getStringValue(idVar.getVarName());
            String lang = row.getStringValue(langVar.getVarName());
            String name = row.getStringValue(nameVar.getVarName());
            fields.get(fieldID).getNames().put(lang, name);
        });

        // Load fields restrictions
        select = new SelectBuilder();
        select.addWhere(fieldVar, RDF.type, Vue4JOntology.Field);
        select.addFilter(fieldFilter);

        select.addVar(idVar);
        select.addWhere(fieldVar, Vue4JOntology.hasFieldID, idVar);

        Var restrictionVar = makeVar("restriction");
        select.addVar(restrictionVar);
        select.addWhere(fieldVar, Vue4JOntology.hasFieldRestriction, restrictionVar);

        Var restrictionTypeVar = makeVar("restrictionType");
        select.addWhere(restrictionVar, RDF.type, restrictionTypeVar);
        select.addWhere(restrictionTypeVar, Vue4JOntology.subClassAny, Vue4JOntology.FieldRestriction);

        sparql.executeSelectQuery(select).forEach((row) -> {
            String fieldID = row.getStringValue(idVar.getVarName());
            String restriction = row.getStringValue(restrictionVar.getVarName());
            URI restrictionType = sparql.formatURI(row.getStringValue(restrictionTypeVar.getVarName()));

            // Load restriction specific class
        });

        return fields;
    }

    private List<String> loadTypeFieldsOrder(Node typeNode, TypeModel type) {
        List<String> fieldsOrder = new ArrayList<>();
        TypeModel parent = type.getParent();
        if (parent != null) {
            fieldsOrder.addAll(parent.getFieldsOrder());
        }
        
        return fieldsOrder;
    }
    
    private boolean isChildDatatype(URI childDataType, URI parentDataType) {
        return childDataType.equals(parentDataType);
    }
}
