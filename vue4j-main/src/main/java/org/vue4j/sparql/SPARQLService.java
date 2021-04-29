package org.vue4j.sparql;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.arq.querybuilder.SelectBuilder;

public class SPARQLService {
    
    public List<SPARQLResult> executeSelectQuery(SelectBuilder query) {
        return new ArrayList<>();
    }
    
    public URI formatURI(URI uri) {
        return uri;
    }
    
    public URI formatURI(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            return formatURI(new URI(uri.trim()));
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Invalid URI: " + uri.trim(), ex);
        }
    }
}
