package org.vue4j.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.Vue4J;
import org.vue4j.Vue4JConfig;
import org.vue4j.Vue4JOptions;
import org.vue4j.modules.ModuleManager;
import org.vue4j.modules.Vue4JModule;
import org.vue4j.utils.ClassUtils;
import org.vue4j.utils.bo.BuildOrderUnsolvableException;

/**
 * Configuration manager based on YAML files.
 *
 * @author Vincent Migot
 */
public class ConfigManager {

    /**
     * Class Logger.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    /**
     * Jackson object mapper.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Root configuration node.
     */
    private ObjectNode root = mapper.createObjectNode();

    /**
     * YAML factory.
     */
    private final YAMLFactory yamlFactory = new YAMLFactory();

    /**
     * YAML object mapper.
     */
    private final ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);

    /**
     * Constructor.
     */
    public ConfigManager() {
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Add a yaml string to configuration.
     *
     * @param yamlString string to merge
     * @throws IOException
     */
    public void addSource(String yamlString) throws IOException {
        addSource(yamlMapper.readTree(yamlString));
    }

    /**
     * Add an array of yaml string lines to configuration.
     *
     * @param yamlLines strings to merge
     * @throws IOException
     */
    public void addLines(String... yamlLines) throws IOException {
        addSource(yamlMapper.readTree(String.join("\n", yamlLines)));
    }

    /**
     * Add a yaml input stream to configuration.
     *
     * @param yamlInput yaml stream to merge
     * @throws IOException
     */
    public void addSource(InputStream yamlInput) throws IOException {
        addSource(yamlMapper.readTree(yamlInput));
    }

    /**
     * Add a yaml file to configuration.
     *
     * @param yamlFile yaml file to merge
     * @throws IOException
     */
    public void addSource(File yamlFile) throws IOException {
        addSource(yamlMapper.readTree(yamlFile));
    }

    /**
     * Add a Jackson JSON node to configuration.
     *
     * @param tree JSON node tree to merge
     * @throws IOException
     */
    public void addSource(JsonNode tree) throws IOException {
        root = mapper.updateValue(root, tree);
    }

    /**
     * Load a configuration root key into the specified config interface.
     *
     * @param <T> Interface to map
     * @param key yaml root key to map
     * @param configClass Interface to map
     * @return Proxy instance of mapped interface
     */
    @SuppressWarnings("unchecked")
    public <T> T loadConfig(String key, Class<T> configClass) {
        T config;

        if (ClassUtils.isPrimitive(configClass)) {
            config = (T) ConfigProxyHandler.getPrimitive(key, root, null);
        } else {
            config = (T) Proxy.newProxyInstance(
                    Vue4J.getClassLoader(),
                    new Class<?>[]{configClass},
                    new ConfigProxyHandler(key, root.deepCopy(), yamlMapper)
            );
        }
        return config;
    }

    /**
     * Load a configuration path into the specified config interface.
     *
     * @param <T> Interface to map
     * @param path yaml path to map (list of keys separated by ".")
     * @param configClass Interface to map
     * @return Proxy instance of mapped interface
     */
    @SuppressWarnings("unchecked")
    public <T> T loadConfigPath(String path, Class<T> configClass) {

        JsonNode baseNode = root;
        String finalKey = path;

        String[] pathParts = path.split("\\.");
        if (pathParts.length > 0) {
            String jsonPointer = "";

            for (int i = 0; i < pathParts.length - 1; i++) {
                jsonPointer += "/" + pathParts[i];
            }
            baseNode = root.at(jsonPointer);
            finalKey = pathParts[pathParts.length - 1];
        }

        T config;

        if (ClassUtils.isPrimitive(configClass)) {
            config = (T) ConfigProxyHandler.getPrimitive(configClass.getCanonicalName(), baseNode.at("/" + finalKey), null);
        } else {
            config = (T) Proxy.newProxyInstance(
                    Vue4J.getClassLoader(),
                    new Class<?>[]{configClass},
                    new ConfigProxyHandler(finalKey, baseNode.deepCopy(), yamlMapper)
            );
        }
        return config;
    }

    /**
     * Print current configuration into output stream.
     *
     * @param os output stream to print into.
     * @throws IOException
     */
    public void toYaml(OutputStream os) throws IOException {
        yamlFactory.createGenerator(os).writeObject(root);
    }

    /**
     * Build system configuration.
     *
     * @param baseConfigFile Initial config file
     * @return Loaded configuration
     * @throws IOException
     */
    public Vue4JConfig buildSystemConfig(File baseConfigFile) throws IOException {
        ObjectMapper systemMapper = new ObjectMapper(yamlFactory);
        ObjectNode systemRoot = systemMapper.createObjectNode();

        if (baseConfigFile != null) {
            if (baseConfigFile.exists() && baseConfigFile.isFile()) {
                systemRoot = systemMapper.updateValue(systemRoot, systemMapper.readTree(baseConfigFile));
            }
        }

        return (Vue4JConfig) Proxy.newProxyInstance(
                Vue4J.getClassLoader(),
                new Class<?>[]{Vue4JConfig.class},
                new ConfigProxyHandler(Vue4JConfig.YAML_KEY, systemRoot.deepCopy(), systemMapper)
        );
    }

    /**
     * Build configuration for all modules.
     *
     * @param baseDirectory Base system directory
     * @param modules List of module
     * @param id Configuration profile identifier
     * @param baseConfigFile Initial config file
     * @param systemConfig System configuration
     * @throws IOException
     */
    public void build(
            ModuleManager moduleManager,
            Vue4JOptions options
    ) throws IOException, BuildOrderUnsolvableException {
        Path baseDirectory = options.getBaseDirectory();
        File baseConfigFile = options.getConfigFile();
        ConfigProfile profile = options.getConfigProfile();

        try {
            String extensionId = profile.getProfileID();
            boolean buildExtension = false;
            if (baseConfigFile != null) {
                if (baseConfigFile.exists() && baseConfigFile.isFile()) {
                    addSource(baseConfigFile);
                    extensionId = loadConfig("extend", String.class);
                    buildExtension = true;
                    if (!ConfigProfile.PROD.match(extensionId)
                            && !ConfigProfile.DEV.match(extensionId)
                            && !ConfigProfile.TEST.match(extensionId)) {
                        extensionId = ConfigProfile.PROD.getProfileID();
                    }
                }

            }

            ConfigProfile extensionProfil = ConfigProfile.valueOf(extensionId.toUpperCase());

            moduleManager.getBuildOrder().execute((Vue4JModule module) -> {
                InputStream prodFile = module.getConfigFile(ConfigProfile.PROD.getProfileID());
                if (prodFile != null) {
                    try {
                        addSource(prodFile);
                    } catch (IOException ex) {
                        LOGGER.error("Unexpected I/O error while reading configuration file for module: " + module.getID(), ex);
                    }
                }

                if (ConfigProfile.DEV.equals(extensionProfil)) {
                    InputStream devFile = module.getConfigFile(ConfigProfile.DEV.getProfileID());
                    if (devFile != null) {
                        try {
                            addSource(devFile);
                        } catch (IOException ex) {
                            LOGGER.error("Unexpected I/O error while reading configuration file for module: " + module.getID(), ex);
                        }
                    }
                } else if (ConfigProfile.TEST.equals(extensionProfil)) {
                    InputStream testFile = module.getConfigFile(ConfigProfile.TEST.getProfileID());
                    if (testFile != null) {
                        try {
                            addSource(testFile);
                        } catch (IOException ex) {
                            LOGGER.error("Unexpected I/O error while reading configuration file for module: " + module.getID(), ex);
                        }
                    }
                }
            });

            File mainConfig = baseDirectory.resolve("vue4j.yml").toFile();
            if (mainConfig.exists() && mainConfig.isFile()) {
                addSource(mainConfig);
            }

            if (buildExtension) {
                addSource(baseConfigFile);
            }

            if (LOGGER.isDebugEnabled()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                toYaml(output);
                String yaml = new String(output.toByteArray(), StandardCharsets.UTF_8.name());
                LOGGER.debug("Loaded configuration");
                LOGGER.debug(yaml);
            }

        } catch (IOException ex) {
            LOGGER.error("Unexpected I/O error while reading configuration file", ex);
            throw ex;
        }
    }

    /**
     * Yaml configuration separator for getExpandedYAMLConfig method.
     */
    private final static String YAML_CONFIG_SEPARATOR = StringUtils.repeat("-", 78);

    private void getModuleExpandedYAMLConfig(Vue4JModule module, StringBuilder yml) throws Exception {
        for (String configId : module.getConfigs().keySet()) {
            Class<?> configClass = module.getConfigClass(configId);
            Object config = module.getConfig(configId, configClass);
            if (configId != null && configClass != null && !configId.isEmpty()) {
                yml.append("\n# " + YAML_CONFIG_SEPARATOR + "\n");
                yml.append("# Configuration for module: " + module.getClass().getSimpleName() + " (" + configClass.getSimpleName() + ")\n");
                addConfigInterface(yml, configId, config, configClass, 0);
            }
        }
    }

    /**
     * Return expanded yaml configuration.
     *
     * @param systemConfig system configuration
     * @param modules list of modules
     * @return Full YAML config
     * @throws Exception
     */
    public String getExpandedYAMLConfig(Vue4JConfig systemConfig, ModuleManager moduleManager) throws Exception {
        StringBuilder yml = new StringBuilder();

        yml.append("\n# " + YAML_CONFIG_SEPARATOR + "\n");
        yml.append("# Base system configuration " + Vue4J.class.getSimpleName() + " (" + Vue4JConfig.class.getSimpleName() + ")\n");
        addConfigInterface(yml, "system", systemConfig, Vue4JConfig.class, 0);

        moduleManager.forEachModulesSync(module -> {
            try {
                getModuleExpandedYAMLConfig(module, yml);
            } catch (Exception ex) {
                LOGGER.error("Unexpected error while printing configuration for : " + module.getID());
            }
        });

        return yml.toString();
    }

    /**
     * YAML space level separator.
     */
    private final static String YAML_SEPARATOR = "  ";

    /**
     * Add a config interface to global generated yml config.
     *
     * @param yml global yml string
     * @param key interface config key
     * @param value interface instance
     * @param objectClass interface class
     * @param depth depth in yml config
     * @throws Exception
     */
    private void addConfigInterface(StringBuilder yml, String key, Object value, Class<?> objectClass, int depth) throws Exception {
        int elementDepth = depth;
        if (key != null && !key.isEmpty()) {
            yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + key + ":\n");
            elementDepth = depth + 1;
        }
        for (Method method : objectClass.getMethods()) {
            ConfigDescription cfgDescription = method.getAnnotation(ConfigDescription.class);
            if (cfgDescription != null) {
                Object itemValue = method.invoke(value);
                addConfigPropertyValue(yml, method.getName(), itemValue, method.getGenericReturnType(), cfgDescription, elementDepth);
            }
        }
    }

    /**
     * Add a config property to global generated yml config.
     *
     * @param yml global yml string
     * @param key interface config key
     * @param value interface instance
     * @param returnType property type
     * @param cfgDescription property description
     * @param depth depth in yml config
     * @throws Exception
     */
    private void addConfigPropertyValue(
            StringBuilder yml,
            String key,
            Object value,
            Type returnType,
            ConfigDescription cfgDescription,
            int depth
    ) throws Exception {
        if (cfgDescription != null) {
            yml.append(StringUtils.repeat(YAML_SEPARATOR, depth)
                    + "# " + cfgDescription.value()
                    + " (" + getShortType(returnType) + ")\n");
        }

        if (ClassUtils.isGenericType(returnType)) {
            ParameterizedType genericReturnType = (ParameterizedType) returnType;
            Type genericParameter = genericReturnType.getActualTypeArguments()[0];

            Class<?> returnTypeClass = (Class<?>) genericReturnType.getRawType();
            if (ClassUtils.isList(returnTypeClass)) {
                if (key != null) {
                    yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + key + ":\n");
                }
                Type genericValueParameter = genericReturnType.getActualTypeArguments()[0];
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    addConfigPropertyListValue(yml, item, genericValueParameter, depth + 1);
                }
            } else if (ClassUtils.isMap(returnTypeClass)) {
                Type genericValueParameter = genericReturnType.getActualTypeArguments()[1];
                if (key != null) {
                    yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + key + ":\n");
                }
                Map<String, ?> map = (Map<String, ?>) value;
                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    addConfigPropertyValue(yml, entry.getKey(), entry.getValue(), genericValueParameter, null, depth + 1);
                }
            } else if (ClassUtils.isClass(returnTypeClass)) {
                yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + ((Class<?>) value).getCanonicalName() + "\n");
            } else {
                LOGGER.warn("Unexpected generic parameter for configuration: "
                        + key + " - " + returnTypeClass.getSimpleName()
                        + "<" + genericParameter.getTypeName() + ">");
            }
        } else {
            Class<?> returnTypeClass = (Class<?>) returnType;
            if (ClassUtils.isPrimitive(returnTypeClass)) {
                if (key != null) {
                    yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + key + ": " + value.toString() + "\n");
                } else {
                    yml.append(value.toString() + "\n");
                }
            } else if (ClassUtils.isInterface(returnTypeClass)) {
                addConfigInterface(yml, key, value, returnTypeClass, depth);
            } else {
                LOGGER.warn("Unexpected parameter for configuration: " + key + " - " + returnTypeClass.getCanonicalName());
            }
        }
    }

    /**
     * Add a config property list to global generated yml config.
     *
     * @param yml global yml string
     * @param value property list value
     * @param type property list type
     * @param depth depth in yml config
     * @throws Exception
     */
    private void addConfigPropertyListValue(StringBuilder yml, Object value, Type type, int depth) throws Exception {
        yml.append(StringUtils.repeat(YAML_SEPARATOR, depth) + "- ");
        addConfigPropertyValue(yml, null, value, type, null, depth + 1);
    }

    /**
     * Return short type to display.
     *
     * @param returnType type to shorten
     * @return shorten type
     */
    private static String getShortType(Type returnType) {
        return returnType.getTypeName().replaceAll("([^.,<]+\\.)+", "");
    }
}
