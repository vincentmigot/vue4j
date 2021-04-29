package org.vue4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.config.ConfigProfile;

public class Vue4JOptions {

    private final static Logger LOGGER = LoggerFactory.getLogger(Vue4J.class);

    public final static String BASE_DIR_ENV_KEY = "VUE4J_BASEDIR";
    public final static String BASE_DIR_ARG_KEY = "BASEDIR";

    private final Path baseDirectory;

    public final static String CONFIG_FILE_ENV_KEY = "VUE4J_CFG";
    public final static String CONFIG_FILE_ARG_KEY = "CFG";

    private final File configFile;

    public final static String CONFIG_PROFILE_ENV_KEY = "VUE4J_CFG_PROFILE";
    public final static String CONFIG_PROFILE_ARG_KEY = "CFG_PROFILE";

    private final ConfigProfile configProfile;

    public final static String DEBUG_ENV_KEY = "VUE4J_DEBUG";
    public final static String DEBUG_ARG_KEY = "DEBUG";

    private final boolean debug;

    private final List<String> args;

    private Vue4JOptions(
            Path baseDirectory,
            File configFile,
            ConfigProfile configProfile,
            boolean debug,
            List<String> args
    ) {
        this.baseDirectory = baseDirectory;
        this.configFile = configFile;
        this.configProfile = configProfile;
        this.debug = debug;
        this.args = args;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public File getConfigFile() {
        return configFile;
    }

    public ConfigProfile getConfigProfile() {
        return configProfile;
    }

    public boolean hasConfigFile() {
        return configFile != null;
    }

    public boolean isDebug() {
        return debug;
    }

    public List<String> getArgs() {
        return args;
    }

    
    static Vue4JOptions fromMap(Map<String, String> options, boolean forceDebug) {
        return fromArgs(getArgs(options), forceDebug);
    }
    
        /**
     * Transform map to an array of arguments "--key=value".
     *
     * @param args map to transform
     * @return list of transfromed args
     */
    private static String[] getArgs(Map<String, String> args) {
        List<String> argsList = new ArrayList<>(args.size());
        args.forEach((String key, String value) -> {
            argsList.add("--" + key + "=" + value);
        });

        return argsList.toArray(new String[0]);
    }

    
    public static Vue4JOptions fromArgs(String[] args, boolean forceDebug) {
        List<String> cliArgsList = new ArrayList<>(args.length);

        // Initialize with existing environment variables
        String baseDirectory = System.getenv(BASE_DIR_ENV_KEY);
        String configFile = System.getenv(CONFIG_FILE_ENV_KEY);
        String configProfileId = System.getenv(CONFIG_PROFILE_ENV_KEY);

        boolean debug = false;

        // Override with command line arguments values
        for (String arg : args) {
            if (arg.startsWith("--" + BASE_DIR_ARG_KEY + "=")) {
                // For base directory
                baseDirectory = arg.split("=", 2)[1];
            } else if (arg.startsWith("--" + CONFIG_PROFILE_ARG_KEY + "=")) {
                // For profile identifier
                configProfileId = arg.split("=", 2)[1];
            } else if (arg.startsWith("--" + CONFIG_FILE_ARG_KEY + "=")) {
                // For configuration file
                configFile = arg.split("=", 2)[1];
            } else if (arg.startsWith("--" + DEBUG_ARG_KEY) && !arg.equalsIgnoreCase("--" + DEBUG_ARG_KEY + "=false")) {
                // For configuration file
                debug = true;

            } else {
                // Otherwise add argument to the remaining list
                cliArgsList.add(arg);
            }
        }

        debug = debug || forceDebug;

        // Set default value for base directory if not set previously
        if (baseDirectory == null || baseDirectory.equals("")) {
            baseDirectory = getDefaultBaseDirectory().toString();
        }

        // Set default profile identifier if not set previously
        ConfigProfile configProfile;
        if (configProfileId == null) {
            configProfile = ConfigProfile.PROD;
        } else {
            configProfile = ConfigProfile.valueOf(configProfileId.toUpperCase());
        }

        File cfgFile = null;
        if (configFile != null && !configFile.isEmpty()) {
            try {
                cfgFile = Paths.get(configFile).toFile();
                if (!cfgFile.exists() || !cfgFile.isFile()) {
                    LOGGER.warn("Invalid config file (ignored): " + cfgFile.getAbsolutePath());
                    cfgFile = null;
                }
            } catch (Exception ex) {
                LOGGER.warn("Error while loading config file (ignored): " + cfgFile.getAbsolutePath(), ex);
                cfgFile = null;
            }
        }

        return new Vue4JOptions(
                Paths.get(baseDirectory),
                cfgFile,
                configProfile,
                debug,
                cliArgsList
        );
    }

    public static Path getDefaultBaseDirectory() {
        return Paths.get(System.getProperty("user.dir"));
    }

}
