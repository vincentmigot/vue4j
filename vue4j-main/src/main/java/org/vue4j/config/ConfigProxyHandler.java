package org.vue4j.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.vue4j.Vue4J;
import org.vue4j.utils.ClassUtils;

/**
 * Proxy class to transform configuration interfaces into real objects, reading directly from loaded configuration.
 *
 * @author Vincent Migot
 */
public class ConfigProxyHandler implements InvocationHandler {

    /**
     * Class Logger.
     */
    private final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    /**
     * Base JSON key path.
     */
    private final String baseKey;

    /**
     * Global configuration JSON node.
     */
    private final JsonNode rootNode;

    /**
     * YAML object mapper.
     */
    private final ObjectMapper yamlMapper;

    /**
     * Constructor.
     *
     * @param key base key
     * @param rootNode Global configuration
     * @param yamlMapper Taml mapper
     */
    public ConfigProxyHandler(String key, JsonNode rootNode, ObjectMapper yamlMapper) {
        if (key.startsWith("/") || key.isEmpty()) {
            this.baseKey = key + "/";
        } else {
            this.baseKey = "/" + key + "/";
        }
        this.rootNode = rootNode;
        this.yamlMapper = yamlMapper;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws InvalidConfigException {
        String key = baseKey + method.getName();

        return nodeToObject(method.getGenericReturnType(), key, rootNode, method);
    }

    /**
     * Return object corresponding to given key.
     *
     * @param type Type to return
     * @param key configuration key
     * @param node Configuration node
     * @param method Interface method
     * @return Loaded object corresponding to given key
     * @throws InvalidConfigException
     */
    @SuppressWarnings("unchecked")
    private Object nodeToObject(Type type, String key, JsonNode node, Method method) throws InvalidConfigException {
        Object result;

        if (ClassUtils.isGenericType(type)) {
            ParameterizedType genericReturnType = (ParameterizedType) type;
            Type genericParameter = genericReturnType.getActualTypeArguments()[0];

            try {
                Class<?> returnTypeClass = (Class<?>) genericReturnType.getRawType();
                if (ClassUtils.isList(returnTypeClass)) {
                    result = getList(genericParameter, node.at(key), method);
                } else if (ClassUtils.isMap(returnTypeClass)) {
                    Type genericValueParameter = genericReturnType.getActualTypeArguments()[1];
                    result = getMap(genericValueParameter, node.at(key), method);
                } else if (ClassUtils.isClass(returnTypeClass)) {
                    result = getClassDefinition(node.at(key), method);
                } else {
                    throw new InvalidConfigException(
                            "Can't get configuration property: " + key
                            + " with given generic type: " + returnTypeClass.getTypeName()
                            + " only List and Map can be used as parametrized configuration class"
                    );
                }
            } catch (ClassNotFoundException ex) {
                throw new InvalidConfigException(
                        "Can't get configuration property: " + key
                        + " class not found: " + node.at(key).asText()
                );
            } catch (IOException ex) {
                throw new InvalidConfigException(
                        "Can't get configuration property: " + key
                        + " with given generic type: " + genericReturnType.getTypeName()
                        + ex.getMessage()
                );
            }
        } else {
            Class<?> returnTypeClass = (Class<?>) type;
            if (ClassUtils.isPrimitive(returnTypeClass)) {
                result = getPrimitive(returnTypeClass.getCanonicalName(), node.at(key), method);
            } else if (JsonNode.class.isAssignableFrom(returnTypeClass)) {
                result = node.at(key);
            } else if (ClassUtils.isInterface(returnTypeClass)) {
                result = getInterface(returnTypeClass, key, node);
            } else {

                throw new InvalidConfigException(
                        "Can't get configuration property: " + key
                        + " with given type: " + returnTypeClass.getCanonicalName()
                        + "Configuration field must be a primitive type, a string, a list,"
                        + "a map or an interface composed of the same kind of fields recursively"
                );
            }
        }
        return result;
    }

    /**
     * Return a primitive object (or default).
     *
     * @param type primitive type
     * @param node corresponding node
     * @param method correponding interface method
     * @return primitive value
     */
    public static Object getPrimitive(String type, JsonNode node, Method method) {
        Object result = null;

        switch (type) {
            case "boolean":
            case "java.lang.Boolean":
                result = getBoolean(node, method);
                break;
            case "int":
            case "java.lang.Integer":
                result = getInt(node, method);
                break;
            case "long":
            case "java.lang.Long":
                result = getLong(node, method);
                break;
            case "float":
            case "java.lang.Float":
                result = getFloat(node, method);
                break;
            case "double":
            case "java.lang.Double":
                result = getDouble(node, method);
                break;
            case "char":
            case "java.lang.Character":
                result = getChar(node, method);
                break;
            case "short":
            case "java.lang.Short":
                result = getShort(node, method);
                break;
            case "byte":
            case "java.lang.Byte":
                result = getByte(node, method);
                break;
            case "java.lang.String":
                result = getString(node, method);
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Return boolean value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return boolean value
     */
    private static boolean getBoolean(JsonNode node, Method method) {
        boolean result = false;
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultBoolean();
                }
            }
        } else {
            result = node.asBoolean();
        }

        return result;
    }

    /**
     * Return int value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return int value
     */
    private static int getInt(JsonNode node, Method method) {
        int result = 0;
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultInt();
                }
            }
        } else {
            result = node.asInt();
        }

        return result;
    }

    /**
     * Return long value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return long value
     */
    private static long getLong(JsonNode node, Method method) {
        long result = 0L;
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultLong();
                }
            }
        } else {
            result = node.asLong();
        }

        return result;
    }

    /**
     * Return float value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return float value
     */
    private static float getFloat(JsonNode node, Method method) {
        float result = 0;
        String nodeText = node.asText();
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultFloat();
                }
            }
        } else {
            result = Float.valueOf(nodeText);
        }

        return result;
    }

    /**
     * Return double value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return double value
     */
    private static double getDouble(JsonNode node, Method method) {
        double result = 0;
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultDouble();
                }
            }
        } else {
            result = node.asDouble();
        }

        return result;
    }

    /**
     * Return char value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return char value
     */
    private static char getChar(JsonNode node, Method method) {
        char result = Character.MIN_VALUE;
        String nodeText = node.asText();
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultChar();
                }
            }
        } else {
            result = nodeText.charAt(0);
        }

        return result;
    }

    /**
     * Return short value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return short value
     */
    private static short getShort(JsonNode node, Method method) {
        short result = (short) 0;
        String nodeText = node.asText();
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultShort();
                }
            }
        } else {
            result = Short.valueOf(nodeText);
        }

        return result;
    }

    /**
     * Return byte value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return byte value
     */
    private static byte getByte(JsonNode node, Method method) {
        byte result = (byte) 0;
        String nodeText = node.asText();
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultByte();
                }
            }
        } else {
            result = Byte.valueOf(nodeText);
        }

        return result;
    }

    /**
     * Return string value.
     *
     * @param node corresponding node
     * @param method correponding interface method
     * @return string value
     */
    private static String getString(JsonNode node, Method method) {
        String result = "";
        if (node.isMissingNode() || node.isNull()) {
            if (method != null) {
                ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
                if (annotation != null) {
                    result = annotation.defaultString();
                }
            }
        } else {
            result = node.asText();
        }

        return result;
    }

    /**
     * Return map value.
     *
     * @param genericParameter Map generic parameter
     * @param value corresponding node
     * @param method correponding interface method
     * @return map value
     * @throws IOException
     * @throws InvalidConfigException
     */
    private List<?> getList(Type genericParameter, JsonNode value, Method method) throws IOException, InvalidConfigException {
        JsonNode currentValue = value;
        if (value.isMissingNode()) {
            ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
            if (annotation != null) {
                if (annotation.defaultList().length > 0) {
                    currentValue = yamlMapper.readTree("- " + String.join("\n- ", annotation.defaultList()));
                } else {
                    currentValue = yamlMapper.readTree("[]");
                }
            }
        }

        List<Object> list = new ArrayList<>(currentValue.size());

        for (JsonNode node : currentValue) {
            list.add(nodeToObject(genericParameter, "", node, method));
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    private <T> T getInterface(Class<T> interfaceClass, String key, JsonNode node) {
        return (T) Proxy.newProxyInstance(Vue4J.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ConfigProxyHandler(key, node, yamlMapper)
        );
    }

    /**
     * Return list value.
     *
     * @param genericParameter List generic parameter
     * @param value corresponding node
     * @param method correponding interface method
     * @return list value
     * @throws IOException
     * @throws InvalidConfigException
     */
    private Map<?, ?> getMap(Type genericParameter, JsonNode value, Method method) throws InvalidConfigException, IOException {
        Map<String, Object> map = new HashMap<>();

        JsonNode currentValue = value;
        if (value.isMissingNode()) {
            ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
            if (annotation != null) {
                String mapStr = String.join("\n  ", annotation.defaultMap());
                currentValue = yamlMapper.readTree("key:\n  " + mapStr).at("/key");
            }
        }

        Iterator<Map.Entry<String, JsonNode>> fields = currentValue.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> node = fields.next();
            map.put(node.getKey(), nodeToObject(genericParameter, "", node.getValue(), method));
        }

        return map;
    }

    private Class<?> getClassDefinition(JsonNode value, Method method) throws ClassNotFoundException {
        String className = value.asText();
        if (value.isMissingNode() || className.isEmpty()) {
            ConfigDescription annotation = method.getAnnotation(ConfigDescription.class);
            if (annotation != null) {
                return annotation.defaultClass();
            } else {
                return null;
            }
        } else {
            return Class.forName(className, true, Vue4J.getClassLoader());
        }
    }
}
