package org.vue4j.openapi;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.integration.GenericOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.vue4j.Vue4J;
import org.vue4j.Vue4JOptions;
import org.vue4j.config.ConfigProfile;
import org.vue4j.modules.ModuleManager;
import org.vue4j.modules.Vue4JModule;

/**
 * Helper class to generate Open API JSON file.
 *
 * @author Vincent Migot
 */
public final class OpenAPIGenerator {

    /**
     * Private constructor to avoid missuse of SwaggerAPIGenerator.
     */
    private OpenAPIGenerator() {

    }

    /**
     * Return full Swagger API for annotated classes found by Reflections.
     *
     * @param reflection Reflections instances for classes
     * @return Swagger API
     */
    public static synchronized OpenAPI getFullApi(Reflections reflection) throws OpenApiConfigurationException {
        OpenAPI openapi = null;

        OpenApiContext ctx = new GenericOpenApiContextBuilder().buildContext(true);

        openapi = ctx.read();

        Map<String, Class<?>> availableAPI = Vue4J.getAnnotatedClassesMap(OpenAPIDefinition.class, reflection);

        Set<Class<?>> classes = new HashSet<>(availableAPI.values());
        if (classes.size() > 0) {

            Reader reader = new Reader(openapi);
            openapi = reader.read(classes);

            return openapi;
        }

        return null;
    }

    /**
     * Return Swagger API for annotated classes found by Reflections in a specifi module.
     *
     * @param moduleClass Module class to limit API scope
     * @param reflection Reflections instances for classes
     * @return Swagger API
     */
    public static synchronized OpenAPI getModuleApi(Class<? extends Vue4JModule> moduleClass, Reflections reflection) throws OpenApiConfigurationException {
        OpenAPI openapi = null;

        OpenApiContext ctx = new GenericOpenApiContextBuilder().buildContext(true);

        openapi = ctx.read();

        Map<String, Class<?>> availableAPI = Vue4J.getAnnotatedClassesMap(OpenAPIDefinition.class, reflection);

        String moduleID = ModuleManager.getProjectIdFromClass(moduleClass);

        Set<Class<?>> classes = new HashSet<>(availableAPI.values());

        Set<Class<?>> moduleClassesAPI = classes.stream().filter((Class<?> c) -> {
            String classModuleID = ModuleManager.getProjectIdFromClass(c);
            return moduleID.equals(classModuleID);
        }).collect(Collectors.toSet());

        if (moduleClassesAPI.size() > 0) {

            Reader reader = new Reader(openapi);
            openapi = reader.read(moduleClassesAPI);

            return openapi;
        }

        return null;
    }

    /**
     * Generate Swagger API for all java files in a specific folder.
     *
     * This API is filtered from global API using Java classes found in source or ti's sub-folder.
     *
     * @param source Base directory to look in
     * @param reflection Reflections instances for classes
     * @return Swagger API
     * @throws Exception
     */
    private static synchronized OpenAPI generate(String source, Reflections reflection) throws Exception {
        OpenAPI openapi = null;

        OpenApiContext ctx = new GenericOpenApiContextBuilder().buildContext(true);
        openapi = ctx.read();
//        openapi.setHost("${host}");

        Set<Class<?>> classes = new HashSet<>();

        if (source != null) {
            Path sourcePath = Paths.get(source);
            if (sourcePath.toFile().exists()) {
                Map<String, Class<?>> availableAPI = Vue4J.getAnnotatedClassesMap(OpenAPIDefinition.class, reflection);

                try (Stream<Path> walk = Files.walk(sourcePath)) {

                    walk.filter(Files::isRegularFile)
                            .forEach((Path p) -> {
                                String filename = p.getFileName().toString();

                                File filePath = p.toFile();
                                if (filePath.exists()) {
                                    String absoluteDirectory = filePath.getParent();
                                    String packageId = absoluteDirectory.substring(source.length()).replaceAll("\\\\|\\/", ".");

                                    if (filename.endsWith(".java")) {
                                        String className = packageId + "." + filename.substring(0, filename.length() - ".java".length());
                                        if (availableAPI.containsKey(className)) {
                                            classes.add(availableAPI.get(className));
                                        }
                                    }
                                }
                            });

                }
            }
        }

        if (classes.size() > 0) {

            Reader reader = new Reader(openapi);
            openapi = reader.read(classes);

            return openapi;
        }

        return null;
    }

    /**
     * Main entry point for swagger API generation.
     *
     * <pre>
     * Used by maven build to generate specific Swagger.json file for each Vue4J module.
     * - First argument is the source folder
     * - Second argument is the destination swagger.json file produced
     * </pre>
     *
     * @param args command line arguments.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String source = args[0];
        String destination = args[1];

        Vue4J instance = getVue4J(Vue4JOptions.getDefaultBaseDirectory());

        Reflections localRef = new Reflections(ConfigurationBuilder.build("")
                .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner(), new MethodAnnotationsScanner())
                .setExpandSuperTypes(false)).merge(instance.getReflections());

        OpenAPI openapi = generate(source, localRef);

        if (openapi != null) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            File swaggerFile = new File(destination);
            swaggerFile.createNewFile();
            mapper.writeValue(swaggerFile, openapi);
        }

        instance.shutdown();
    }

    /**
     * Return opensilex instance.
     *
     * @param baseDirectory
     * @return opensilex instance
     * @throws Exception
     */
    public static Vue4J getVue4J(Path baseDirectory) throws Exception {
        Map<String, String> args = new HashMap<String, String>() {
            {
                put(Vue4JOptions.CONFIG_PROFILE_ARG_KEY, ConfigProfile.DEV.getProfileID());

                // NOTE: uncomment this line to enable full debug during swagger API generation process
                // put(Vue4J.DEBUG_ARG_KEY, "true");
            }
        };

        if (baseDirectory == null) {
            baseDirectory = Vue4JOptions.getDefaultBaseDirectory();
        }

        args.put(Vue4JOptions.BASE_DIR_ARG_KEY, baseDirectory.toFile().getCanonicalPath());

        return Vue4J.createInstance(args);
    }
}
