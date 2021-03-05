/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.modules;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.Vue4J;
import org.vue4j.utils.ClassUtils;
import org.vue4j.utils.bo.BuildOrder;
import org.vue4j.utils.bo.BuildOrderUnsolvableException;

/**
 *
 * @author vince
 */
public class ModuleManager {

    private final static Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);

    /**
     * Subfolder where JAR modules are located.
     */
    private final static String MODULES_JAR_FOLDER = "modules";

    /**
     * Dependencies cache file to avoid unneeded multiple downloads.
     */
    private final static String DEPENDENCIES_LIST_CACHE_FILE = "vue4j.dependencies.cache";

    private final DependencyManager dependencyManager;

    private final Set<URL> modulesURLs;

    private final Path baseDirectory;

    private BuildOrder<Vue4JModule> buildOrder = null;

    public ModuleManager(Path baseDirectory) throws IOException, ModelBuildingException, DependencyResolutionException {
        this.dependencyManager = new DependencyManager(
                ClassUtils.getPomFile(Vue4J.class,
                        "org.vue4j", "vue4j-main")
        );
        this.baseDirectory = baseDirectory;
        this.modulesURLs = loadModulesWithDependencies();
    }

    /**
     * Load modules with their dependencies, downloading them if needed.
     *
     * @return list of loaded JAR URLs
     */
    private Set<URL> loadModulesWithDependencies() {
        // Read existing dependencies from cache file
        Set<URL> readDependencies = ModuleManager.readDependencies(baseDirectory);

        // Get list of modules URL
        Set<URL> urls = ModuleManager.listModulesURLs(baseDirectory);

        // If some modules are not listed in dependencies or no dependencies were read
        if (!readDependencies.containsAll(urls)) {
            // If some modules have not been previously registred
            List<URL> missingModules = new ArrayList<URL>(urls);
            missingModules.removeAll(readDependencies);

            // Get modules dependencies and load them
            Set<URL> dependencies = loadModulesWithDependencies(dependencyManager, urls);

            // Rewrite old & new dependencies in cache file
            dependencies.addAll(readDependencies);
            dependencies.removeAll(urls);
            ModuleManager.writeDependencies(baseDirectory, dependencies);

            registerDependencies(readDependencies);
        } else {
            // Otherwise simply register known dependencies
            registerDependencies(readDependencies);
        }

        registerDependencies(urls);

//        addOptionalModulesOrder(systemConfig.modulesOrder());
//
//        setIgnoredModules(systemConfig.ignoredModules());
        return urls;
    }

    /**
     * Load modules and their dependencies.
     *
     * @param dependencyManager Dependency manager to load dependencies
     * @param modulesJarURLs List of module JAR URLs
     * @return List of all dependencies for modules and the modules themselves
     */
    private Set<URL> loadModulesWithDependencies(DependencyManager dependencyManager, Set<URL> modulesJarURLs) {
        try {
            // Load module dependencies and get the list
            Set<URL> dependenciesURL = dependencyManager.loadModulesDependencies(modulesJarURLs);

            // Register all dependencies and modules
            registerDependencies(dependenciesURL);
            return dependenciesURL;
        } catch (Exception ex) {
            LOGGER.error("Error while loading modules with dependencies", ex);
        }

        return null;
    }

    /**
     * Register all depents JAR URL in list for use with class loaders.
     *
     * @param dependenciesURL List of dependencies to register
     */
    private void registerDependencies(Set<URL> dependenciesURL) {
        if (LOGGER.isDebugEnabled()) {
            dependenciesURL.forEach((dependencyURL) -> {
                LOGGER.debug("Added dependency to classpath: " + dependencyURL.getPath());
            });
        }

        // Load dependencies through URL Class Loader based on actual class loader
        if (dependenciesURL.size() > 0) {
            URLClassLoader classLoader = new URLClassLoader(
                    dependenciesURL.toArray(new URL[dependenciesURL.size()]),
                    Thread.currentThread().getContextClassLoader()
            );
            LOGGER.debug("JAR URLs added to classpath");

            // Set the newly created class loader as the main one
            Thread.currentThread().setContextClassLoader(classLoader);
        } else {
            LOGGER.debug("No URLs added to classpath !");
        }
    }

    /**
     * Return an Iterable of modules to do custom loop logic.
     *
     * @return Iterable of modules
     */
    public BuildOrder<Vue4JModule> getBuildOrder() throws BuildOrderUnsolvableException {
        if (buildOrder == null) {

            Set<Vue4JModule> modules = new HashSet<>();
            Iterator<Vue4JModule> i = ServiceLoader.load(Vue4JModule.class, Vue4J.getClassLoader()).iterator();
            Map<String, Set<Vue4JModule>> modulesByArtifactId = new HashMap<>();
            while (i.hasNext()) {
                Vue4JModule module = i.next();
                modules.add(module);
                String artifactId = ModuleManager.getProjectIdFromClass(module.getClass());
                if (!modulesByArtifactId.containsKey(artifactId)) {
                    modulesByArtifactId.put(artifactId, new HashSet<>());
                }
                modulesByArtifactId.get(artifactId).add(module);
            }

            Map<String, Set<Vue4JModule>> modulesDependenciesByArtifactId = new HashMap<>();
            modulesByArtifactId.keySet().forEach((artifactId) -> {
                Set<String> dependencies = dependencyManager.getModuleDependencies(artifactId);
                Set<Vue4JModule> moduleDepencencies = new HashSet<Vue4JModule>();
                dependencies.forEach((dependencyArtifactId) -> {
                    if (modulesByArtifactId.containsKey(dependencyArtifactId)) {
                        moduleDepencencies.addAll(modulesByArtifactId.get(dependencyArtifactId));
                    }
                });
                modulesDependenciesByArtifactId.put(artifactId, moduleDepencencies);
            });

            buildOrder = BuildOrder.getBuildOrderByDependencies(
                    modules,
                    (module) -> {
                        return module.getID();
                    }, (module) -> {
                        String artifactId = ModuleManager.getProjectIdFromClass(module.getClass());
                        return modulesDependenciesByArtifactId.get(artifactId);
                    });
        }

        return buildOrder;
    }
    
    public void forEachModules(Consumer<Vue4JModule> action) throws BuildOrderUnsolvableException {
        getBuildOrder().execute(action);
    }
    
    public void forEachModulesSync(Consumer<Vue4JModule> action) throws BuildOrderUnsolvableException {
        getBuildOrder().executeSync(action);
    }

    public void forEachModulesReverse(Consumer<Vue4JModule> action) throws BuildOrderUnsolvableException {
        getBuildOrder().executeReverse(action);
    }

    /**
     * Utility method to get modules URL inside MODULES_JAR_FOLDER subdirectory of the given directory parameter.
     *
     * @param baseDirectory Directory to look in
     * @return List of modules JAR URL found
     */
    private static Set<URL> listModulesURLs(Path baseDirectory) {
        // Find the subdirectory
        File modulesDirectory = baseDirectory.resolve(MODULES_JAR_FOLDER).toFile();

        // Get all files within
        File[] modulesList = modulesDirectory.listFiles();

        LOGGER.debug("Start listing jar module files in directory: " + modulesDirectory.getPath());

        // Filter all JAR found
        Set<URL> modulesJarURLs = new HashSet<>();
        if (modulesList != null) {
            for (File moduleFile : modulesList) {
                LOGGER.debug("Module found: " + moduleFile.getName());
                URL jarURL = getModuleURLFromFile(moduleFile);
                if (jarURL != null) {
                    modulesJarURLs.add(jarURL);
                }
            }
        } else {
            LOGGER.debug("Modules directory doesn't exists !");
        }

        // Return the list
        return modulesJarURLs;
    }

    /**
     * Return corresonding JAR URL of the given file or null.
     *
     * @param moduleFile The file to check
     * @return JAR URL or null
     */
    private static URL getModuleURLFromFile(File moduleFile) {
        URL result = null;

        if (moduleFile.isFile() && moduleFile.toString().endsWith(".jar")) {
            try {
                URL jarUrl = moduleFile.toURI().toURL();
                result = jarUrl;
                LOGGER.debug("Registering jar module file: " + moduleFile.getPath());
            } catch (MalformedURLException ex) {
                LOGGER.error("Error while registering module: " + moduleFile.getPath(), ex);
            }
        } else {
            LOGGER.warn("Ignoring module : " + moduleFile.getPath());
        }

        return result;
    }

    private static Map<Class<?>, String> artifactIdByClass = new HashMap<>();

    /**
     * Return project (artifact) identifier from a class.
     *
     * @param classFromProject class to use to find JAR/project/artifact
     * @return project identifier
     */
    public static String getProjectIdFromClass(Class<?> classFromProject) {
        String projectId = classFromProject.getPackage().getImplementationTitle();
        if (projectId == null) {
            try {
                if (artifactIdByClass.containsKey(classFromProject)) {
                    projectId = artifactIdByClass.get(classFromProject);
                } else {
                    File pom = ClassUtils.getPomFile(classFromProject);
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    Model model = reader.read(new FileReader(pom));
                    projectId = model.getArtifactId();
                }
            } catch (Exception ex) {
                return "";
            }
        }

        return projectId;
    }

    /**
     * Utility method to read dependencies from cache file.
     *
     * @param baseDirectory Directory where dependency cache file is.
     *
     * @return Lsit of JAR dependencies URL
     */
    private static Set<URL> readDependencies(Path baseDirectory) {
        try {
            // Check if depndency file exist
            File dependencyFile = baseDirectory.resolve(DEPENDENCIES_LIST_CACHE_FILE).toFile();
            Set<URL> dependencyURLs = new HashSet<>();

            if (dependencyFile.isFile()) {
                // If it's a file read all lines and add in the dependencyURLs list
                for (String dependency : FileUtils.readLines(dependencyFile, StandardCharsets.UTF_8.name())) {
                    dependencyURLs.add(new URL(dependency));
                }
            }

            // Return the list
            return dependencyURLs;
        } catch (IOException ex) {
            LOGGER.error("Error while reading dependency file", ex);
            return null;
        }
    }

    /**
     * Utility method to write dependencies URL to a cache file.
     *
     * @param baseDirectory Directory where dependency cache file is.
     * @param dependencies Depndencies JAR URL list.
     */
    private static void writeDependencies(Path baseDirectory, Set<URL> dependencies) {
        try {
            File dependencyFile = baseDirectory.resolve(DEPENDENCIES_LIST_CACHE_FILE).toFile();
            if (dependencies.size() > 0) {
                FileUtils.writeLines(dependencyFile, dependencies);
            }
        } catch (IOException ex) {
            LOGGER.error("Error while writing dependency file", ex);
        }
    }
}
