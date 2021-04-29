package org.vue4j.cli;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vue4j.Vue4J;
import org.vue4j.Vue4JOptions;
import org.vue4j.modules.Vue4JModule;
import org.vue4j.utils.ClassUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * This class is the main entry point for the CLI application.
 *
 * It uses the Picocli library to automatically generate help messages and argument parsing, see: https://picocli.info/
 *
 * @author Vincent Migot
 */
@Command(
        name = "vue4j",
        header = "Vue4J Command Line Interface",
        description = "Vue4J is a fast development tool for Web applications in Java & Vue.js"
)
public class MainCommand extends AbstractCommand implements IVersionProvider {

    private final static Logger LOGGER = LoggerFactory.getLogger(MainCommand.class);
    /**
     * <pre>
     * Version flag option (automatically handled by the picocli library).
     * For details see: https://picocli.info/#_version_help
     * </pre>
     */
    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Print version information and exit.")
    private boolean versionRequested;

    /**
     * Static main to launch commands.
     *
     * @param args Command line arguments array
     * @throws Exception In case of any error during command execution
     */
    public static void main(String[] args) throws Exception {
        boolean forceDebug = false;
//        forceDebug = true;

        LOGGER.debug("Create instance from command line");
        Vue4JOptions options = Vue4JOptions.fromArgs(args, forceDebug);
        Vue4J instance = Vue4J.createInstance(options);

        // If no arguments assume help is requested
        List<String> remainingArgs = options.getArgs();
        for (String s : remainingArgs) {
            LOGGER.debug("CLI input parameters", s);
        }

        if (remainingArgs.size() == 0) {
            remainingArgs.add("git-commit");
        }

        args = remainingArgs.toArray(new String[]{});

        CommandLine cli = getCLI(args, null);

        try {
            // Avoid to start instance if only help is required
            CommandLine.ParseResult parsedArgs = cli.parseArgs(args);

            boolean isHelp = false;
            List<CommandLine> foundCommands = parsedArgs.asCommandLineList();

            if (foundCommands.size() > 0) {
                CommandLine commandToExcute = foundCommands.get(foundCommands.size() - 1);
                isHelp = isHelp || commandToExcute.getCommandName().equals("help");
                isHelp = isHelp || commandToExcute.getSubcommands().size() > 0;
            }

            LOGGER.debug("Configure instance");
            instance.configure();
            LOGGER.debug("Instance configured");
            if (!isHelp) {
                LOGGER.debug("Start instance");
                instance.start();
                LOGGER.debug("Instance started");
                commands.forEach((Vue4JCommand cmd) -> {
                    cmd.setVue4J(instance);
                });
            }
        } catch (CommandLine.ParameterException ex) {
            // Silently ignore parameter exceptions meaning help will be printed
        }

        cli.execute(args);
    }

    /**
     * Loader for commands.
     */
    private static ServiceLoader<Vue4JCommand> commands;

    /**
     * Return command line instance.
     *
     * @param args Command line arguments
     * @param instance Vue4J instance
     *
     * @return loaded command
     */
    public static CommandLine getCLI(String[] args, Vue4J instance) {

        // Initialize picocli library
        CommandLine cli = new CommandLine(new MainCommand()) {

        };

        // Register all commands contained in modules
        LOGGER.debug("Load commands");
        commands = ServiceLoader.load(Vue4JCommand.class, Vue4J.getClassLoader());
        commands.forEach((Vue4JCommand cmd) -> {
            if (instance != null) {
                cmd.setVue4J(instance);
            }
            Command cmdDef = cmd.getClass().getAnnotation(CommandLine.Command.class);
            cli.addSubcommand(cmdDef.name(), cmd);
            LOGGER.debug("Add command: " + cmdDef.name());
        });
        LOGGER.debug("Commands loaded");

        // Define the help factory class
        cli.setHelpFactory(new HelpFactory());

        return cli;
    }

    /**
     * Implementation of picocli.CommandLine.IVersionProvider to display the list of known modules when using the -V command line flag.
     *
     * @return List of all module with their version
     * @throws Exception Propagate any exception that could occurs
     */
    @Override
    public String[] getVersion() throws Exception {
        List<String> versionList = new ArrayList<>();

        // Add version in list for all modules
        getVue4J().forEachModules((Vue4JModule module) -> {
            versionList.add(module.getID() + ": " + module.getVersion());
        });
        String[] versionListArray = new String[versionList.size()];
        return versionList.toArray(versionListArray);
    }

    /**
     * Display git commit number used for building this version.
     */
    @Command(
            name = "git-commit",
            header = "Display git commit",
            description = "Display git commit identifier used for building this Vue4J version"
    )
    public void gitCommit(
            @Mixin HelpOption help) {
        try {
            File gitPropertiesFile = ClassUtils.getFileFromClassArtifact(Vue4J.class, "git.properties");

            Properties gitProperties = new Properties();
            gitProperties.load(new FileReader(gitPropertiesFile));

            String gitCommitAbbrev = gitProperties.getProperty("git.commit.id.abbrev", null);
            String gitCommitFull = gitProperties.getProperty("git.commit.id.full", null);
            String gitCommitMessage = gitProperties.getProperty("git.commit.message.full", null);
            String gitCommitUsername = gitProperties.getProperty("git.commit.user.name", null);
            String gitCommitUsermail = gitProperties.getProperty("git.commit.user.email", null);
            if (gitCommitUsername != null && gitCommitUsermail != null) {
                gitCommitUsername = gitCommitUsername + " <" + gitCommitUsermail + ">";
            }

            if (gitCommitAbbrev == null || gitCommitFull == null) {
                System.out.println("No git commit information found");
            } else {
                System.out.println("Git commit id: " + gitCommitAbbrev + " (" + gitCommitFull + ")");
                if (gitCommitMessage != null) {
                    System.out.println("Git commit message: " + gitCommitMessage);
                }
                if (gitCommitUsername != null) {
                    System.out.println("Git commit user: " + gitCommitUsername);
                }
            }
        } catch (Exception ex) {
            System.out.println("No git commit information found");
            LOGGER.debug("Exception raised:", ex);
        }
    }
}
