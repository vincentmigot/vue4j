package org.vue4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.config.ConfigManager;
import org.vue4j.log.LogFilter;
import org.vue4j.modules.Vue4JModuleConfig;
import org.vue4j.modules.ModuleManager;
import org.vue4j.modules.Vue4JModule;
import org.vue4j.server.ServerModule;
import org.vue4j.services.ServiceManager;
import org.vue4j.utils.ClassUtils;
import org.vue4j.utils.bo.BuildOrderUnsolvableException;

public class Vue4J {

    public final static String VERSION = "1.0.0";

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

    public <T> void forEachModulesImplementingExtension(Class<T> moduleExtensionClass, Consumer<T> action) throws BuildOrderUnsolvableException {
        moduleManager.forEachModulesImplementingExtension(moduleExtensionClass, action);
    }

    public void configure() throws BuildOrderUnsolvableException, IOException, Exception {
        LOGGER.debug("Build system configuration");
        systemConfig = configManager.buildSystemConfig(options.getConfigFile());

        LOGGER.debug("Load modules configuration");
        // Iterate over modules
        forEachModules((module) -> {
            module.setVue4J(this);

            // Get module configuration identifier and class
            Vue4JModuleConfig[] declaredConfigs = module.getClass().getAnnotationsByType(Vue4JModuleConfig.class);
            for (Vue4JModuleConfig declaredConfig : declaredConfigs) {
                String configId = declaredConfig.id();
                Class<?> configClass = declaredConfig.configInterface();

                // If module is configurable
                if (configId != null && configClass != null) {
                    // Load configuration with manager
                    Object config = configManager.loadConfig(configId, configClass);
                    // Affect loaded configuration to module

                    module.registerConfig(configId, configClass, config);

                }
            }

            module.initialize();
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Current expanded configuration:" + configManager.getExpandedYAMLConfig(systemConfig, moduleManager));
        }
    }

    public void start() throws BuildOrderUnsolvableException, Exception {
        LOGGER.debug("Register shutdown callback");
        // Add hook to clean modules on shutdown
        if (shutdownHook != null) {
            LOGGER.warn("Vue4J instance already started ! Multiple call to 'start' method");
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

        LOGGER.debug("Enable required modules");
        forEachModules((module) -> {
            module.enable();
        });

        LOGGER.debug("Auto-starting modules");
        forEachModules((module) -> {
            if (module.autoStart()) {
                module.run();
            }
        });
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
            module.disable();
        });

        // Clean all modules
        forEachModulesReverse(module -> {
            module.clean();
        });

    }

    /**
     * Determine if application is in debug mode.
     *
     * @return true if application is in debug mode and false otherwise
     */
    public boolean isDebug() {
        return options.isDebug();
    }

    /**
     * Return raw application configuration files.
     *
     * @return raw configuration file
     */
    public File getConfigFile() {
        return options.getConfigFile();
    }

    /**
     * Internal reflections instance to analyse all application classes and methods.
     */
    private Reflections reflections;

    /**
     * Return reflection instance, building it if needed.
     *
     * @return Reflections instance
     */
    public Reflections getReflections() {
        if (reflections == null) {
            try {
                this.buildReflections();
            } catch (BuildOrderUnsolvableException ex) {
                throw new RuntimeException(ex);
            }
        }
        return reflections;
    }

    /**
     * Build reflection instance by adding all modules JAR to class loader and initialize Reflections library with them.
     */
    private void buildReflections() throws BuildOrderUnsolvableException {
        LOGGER.debug("Initialize JAR URLs to scan by reflection");
        Set<URL> urlsToScan = this.moduleManager.getModulesURLs();

        LOGGER.debug("Exclude ignored modules from reflection");
        Collection<String> ignoredModuleFilePatterns = new ArrayList<>(); //systemConfig.ignoredModules().values();
        urlsToScan = urlsToScan.stream().filter((URL url) -> {
            for (String ignoredModuleFilePattern : ignoredModuleFilePatterns) {
                if (!url.getPath().endsWith(ignoredModuleFilePattern)) {
                    return true;
                }
            }

            return false;
        }).collect(Collectors.toSet());

        LOGGER.debug("Extra module JAR files registring for static instance");
        Set<URL> jarModulesURLs = new HashSet<>();
        forEachModules(m -> {
            File jarFile = ClassUtils.getJarFile(m.getClass());

            if (!m.getClass().equals(ServerModule.class) || !jarFile.isFile()) {

                try {
                    URL jarURL = new URL("file://" + jarFile.getAbsolutePath());
                    LOGGER.debug("Register module JAR URL for" + m.getClass().getSimpleName() + ": " + jarURL.getPath());
                    jarModulesURLs.add(jarURL);
                } catch (MalformedURLException ex) {
                    LOGGER.warn("Invalid module URL for: " + m.getClass().getSimpleName(), ex);
                }
            }
        });

        urlsToScan.addAll(jarModulesURLs);

        ConfigurationBuilder builder;
        if (!urlsToScan.isEmpty()) {

            // Load dependencies through URL Class Loader based on actual class loader
            if (urlsToScan.size() > 0) {
                URLClassLoader classLoader = new URLClassLoader(
                        urlsToScan.toArray(new URL[urlsToScan.size()]),
                        Thread.currentThread().getContextClassLoader()
                );
                LOGGER.debug("Module registred, jar URLs added to classpath");

                // Set the newly created class loader as the main one
                Thread.currentThread().setContextClassLoader(classLoader);
            } else {
                LOGGER.debug("No external module found !");
            }

            builder = ConfigurationBuilder.build("", Vue4J.getClassLoader())
                    .setUrls(urlsToScan)
                    .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner(), new MethodAnnotationsScanner())
                    .setExpandSuperTypes(false);
        } else {
            builder = ConfigurationBuilder.build("", Vue4J.getClassLoader())
                    .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner(), new MethodAnnotationsScanner())
                    .setExpandSuperTypes(false);
        }

        reflections = new Reflections(builder);
    }

    /**
     * Helper method to get annoted class list in application.
     *
     * @param annotation Annotation to look at
     * @return Map of found annotated classes indexed by name
     */
    public Map<String, Class<?>> getAnnotatedClassesMap(Class<? extends Annotation> annotation) {
        return getAnnotatedClassesMap(annotation, getReflections());
    }

    /**
     * Helper method to get annoted class list in application using a specific reflections instance.
     *
     * @param annotation Annotation to look at
     * @param ref Reflections instance
     * @return Map of found annotated classes indexed by name
     */
    public static Map<String, Class<?>> getAnnotatedClassesMap(Class<? extends Annotation> annotation, Reflections ref) {
        Map<String, Class<?>> classMap = new HashMap<>();

        ref.getTypesAnnotatedWith(annotation).forEach((Class<?> c) -> {
            classMap.put(c.getCanonicalName(), c);
        });

        return classMap;
    }

    public static Vue4J createInstance(Map<String, String> options) throws Exception {
        return createInstance(Vue4JOptions.fromMap(options, false));
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
            ServiceManager srvManager = new ServiceManager(cfgManager);

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

    public <T> T getModule(Class<T> moduleClass) {
        return moduleManager.getModule(moduleClass);
    }

    public Path getBaseDirectory() {
        return this.options.getBaseDirectory();
    }

    private final Map<String, Object> globalArguments = new HashMap<>();

    public void setGlobalArgument(String key, Object value) {
        globalArguments.put(key, value);
    }

    public <T> T getGlobalArgument(String key, Class<T> valueClass) {
        Object value = globalArguments.get(key);
        if (value != null) {
            return (T) value;
        } else {
            return null;
        }
    }

}
