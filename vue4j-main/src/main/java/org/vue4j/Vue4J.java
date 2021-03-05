package org.vue4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.config.ConfigManager;
import org.vue4j.log.LogFilter;
import org.vue4j.modules.ModuleConfig;
import org.vue4j.modules.ModuleManager;
import org.vue4j.modules.Vue4JModule;
import org.vue4j.services.ServiceManager;
import org.vue4j.utils.bo.BuildOrderUnsolvableException;

public class Vue4J {

    private final static Logger LOGGER = LoggerFactory.getLogger(Vue4J.class);

    private final ConfigManager configManager;
    private final ModuleManager moduleManager;
    private final ServiceManager serviceManager;
    private final Vue4JOptions options;
    private Vue4JConfig systemConfig;

    /**
     * Store reference to shutdown hook to avoid duplication on reset.
     */
    private Thread shutdownHook;

    private Vue4J(
            ConfigManager configManager,
            ModuleManager moduleManager,
            ServiceManager serviceManager,
            Vue4JOptions options
    ) throws IOException, BuildOrderUnsolvableException {
        LOGGER.debug("Create Vue4J instance");

        this.configManager = configManager;
        this.moduleManager = moduleManager;
        this.serviceManager = serviceManager;
        this.options = options;

        LOGGER.debug("Build global configuration");
        configManager.build(moduleManager, options);

    }

    public void forEachModules(Consumer<Vue4JModule> action) throws BuildOrderUnsolvableException {
        moduleManager.forEachModules(action);
    }

    public void forEachModulesReverse(Consumer<Vue4JModule> action) throws BuildOrderUnsolvableException {
        moduleManager.forEachModulesReverse(action);
    }

    public void setup() throws BuildOrderUnsolvableException, IOException, Exception {
        systemConfig = configManager.buildSystemConfig(options.getConfigFile());

        LOGGER.debug("Load modules configuration");

        // Iterate over modules
        forEachModules((module) -> {
            // Get module configuration identifier and class
            ModuleConfig[] declaredConfigs = module.getClass().getAnnotationsByType(ModuleConfig.class);
            for (ModuleConfig declaredConfig : declaredConfigs) {
                String configId = declaredConfig.id();
                Class<?> configClass = declaredConfig.cfg();

                // If module is configurable
                if (configId != null && configClass != null) {
                    // Load configuration with manager
                    Object config = configManager.loadConfig(configId, configClass);
                    // Affect loaded configuration to module
                    module.setVue4J(this);
                    module.registerConfig(configId, configClass, config);
                    module.configure();
                }
            }
        });

        LOGGER.debug("Current expanded configuration:" + configManager.getExpandedYAMLConfig(systemConfig, moduleManager));
    }

    public void start() throws BuildOrderUnsolvableException, Exception {
        LOGGER.debug("Starting instance");
        // Add hook to clean modules on shutdown
        if (shutdownHook != null) {
            LOGGER.error("Vue4J instance already started ! Multiple call to 'start' method");
            return;
        }
        shutdownHook = new Thread() {
            @Override
            public void run() {
                try {
                    shutdown();
                } catch (Exception ex) {
                    LOGGER.error("Error while shutting down Vue4J", ex);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        forEachModules((module) -> {
            if (!module.isEnabled()) {
                module.enable();
            }
        });
        LOGGER.debug("Instance started");
    }

    /**
     * Shutdown application.
     *
     * @throws Exception
     */
    public void shutdown() throws Exception {
        LOGGER.debug("Stop instance");

        // Stop all modules
        forEachModulesReverse(module -> {
            if (module.isEnabled()) {
                module.disable();
            }
        });

        // Clean all modules
        forEachModulesReverse(module -> {
            module.clean();
        });

    }

    public static Vue4J createInstance(Vue4JOptions options) throws Exception {
        try {
            // Try to find logback.xml file in base directory to initialize logger configuration
            File logConfigFile = options.getBaseDirectory().resolve("logback.xml").toFile();
            loadLoggerConfig(logConfigFile, true);

            // If debug flag, set default log level output to DEBUG
            if (options.isDebug()) {
                ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                root.setLevel(Level.DEBUG);
                LogFilter.forceDebug();
            }

            LOGGER.debug("Base directory:" + options.getBaseDirectory().toFile().getAbsolutePath());

            if (!options.hasConfigFile()) {
                LOGGER.debug("No main config file defined");
            } else {
                LOGGER.debug("Main config file: " + options.getConfigFile().getAbsolutePath());
            }

            LOGGER.debug("Create configuration manager");
            ConfigManager cfgManager = new ConfigManager();

            LOGGER.debug("Create modules manager");
            ModuleManager modManager = new ModuleManager(options.getBaseDirectory());

            LOGGER.debug("Create service manager");
            ServiceManager srvManager = new ServiceManager();

            Vue4J instance = new Vue4J(
                    cfgManager,
                    modManager,
                    srvManager,
                    options
            );

            return instance;

        } catch (Exception ex) {
            LOGGER.error("Fail to create and initialize Vue4J instance", ex);
            throw ex;
        }
    }

    public static boolean loadLoggerConfig(File logConfigFile, boolean reset) {
        // Check if config file exists
        if (logConfigFile.isFile()) {
            try {
                // Reset logger configuration if needed
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                if (reset) {
                    loggerContext.reset();
                }

                // Load new logger configuration file
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(loggerContext);
                try (InputStream configStream = FileUtils.openInputStream(logConfigFile)) {
                    configurator.doConfigure(configStream);
                    return true;
                }
            } catch (JoranException | IOException ex) {
                LOGGER.warn("Error while trying to load logback configuration file: " + logConfigFile.getAbsolutePath(), ex);
            }
        } else {
            LOGGER.debug("Logger configuration file does not exists: " + logConfigFile.getAbsolutePath());
        }

        return false;
    }

    public static ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
