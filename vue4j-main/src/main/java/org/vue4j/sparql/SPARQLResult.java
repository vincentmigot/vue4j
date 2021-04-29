//******************************************************************************
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2019
// Contact: vincent.migot@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.vue4j.sparql;

import java.util.function.BiConsumer;

/**
 *
 * @author Vincent Migot
 */
public interface SPARQLResult {

    public default String getStringValue(String key) {
     return getStringValue(key, "");
    }
    
    public String getStringValue(String key, String defaultValue);
    
    public void forEach(BiConsumer<? super String, ? super String> action);

    public boolean getBoolValue(String varName, boolean defaultValue);

    public int getIntValue(String varName, int defaultValue);
}
