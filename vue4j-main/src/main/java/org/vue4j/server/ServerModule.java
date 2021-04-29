package org.vue4j.server;

import java.nio.file.Path;
import org.apache.catalina.LifecycleException;
import org.vue4j.modules.ModuleNotFoundException;
import org.vue4j.modules.Vue4JModule;
import org.vue4j.modules.Vue4JModuleConfig;
import org.vue4j.server.tomcat.TomcatServer;

@Vue4JModuleConfig(id = "server", configInterface = ServerModuleConfig.class)
public class ServerModule extends Vue4JModule {

    private TomcatServer server;

    @Override
    public void configure() throws ModuleNotFoundException {
        ServerModuleConfig config = getConfig("server", ServerModuleConfig.class);
        server = new TomcatServer(getVue4J(), config.tomcat());
    }

    @Override
    public void activate() throws LifecycleException {

    }

    @Override
    public void start() throws LifecycleException, ModuleNotFoundException {
        server.start();
    }

    @Override
    public void stop() throws LifecycleException {
        server.stop();
    }

    @Override
    public void deactivate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public Path getServerDirectory() {
        return getVue4J().getGlobalArgument("tomcatDirectory", Path.class);
    }

    public String getHost() {
        return getVue4J().getGlobalArgument("host", String.class);
    }

    public int getPort() {
        return getVue4J().getGlobalArgument("port", Integer.class);
    }

    public int getAdminPort() {
        return getVue4J().getGlobalArgument("adminPort", Integer.class);
    }

}
