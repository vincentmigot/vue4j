//******************************************************************************
//                        ServerModuleConfig.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2019
// Contact: vincent.migot@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.vue4j.server;

import org.vue4j.config.ConfigDescription;
import org.vue4j.server.tomcat.TomcatServerConfig;

/**
 * Default configuration for OpenSilex base module.
 *
 * @author Vincent Migot
 */
public interface ServerModuleConfig {

    @ConfigDescription(
            value = "Tomcat server configuration"
    )
    TomcatServerConfig tomcat();
}
