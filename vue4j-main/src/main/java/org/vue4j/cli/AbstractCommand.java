package org.vue4j.cli;

import org.vue4j.Vue4J;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;

/**
 * Utility class used as super class for commands which are only a regroup of other commands and which only has purpose of displaying help for them.
 *
 * @author Vincent Migot
 */
@Command(
        description = "Calling this command will simply display help message with all subcommands",
        subcommands = {
            HelpCommand.class
        },
        headerHeading = HelpFactory.CLI_HEADER_HEADING,
        synopsisHeading = HelpFactory.CLI_SYNOPSYS_HEADING,
        descriptionHeading = HelpFactory.CLI_DESCRIPTION_HEADING,
        parameterListHeading = HelpFactory.CLI_PARAMETER_LIST_HEADING,
        optionListHeading = HelpFactory.CLI_OPTION_LIST_HEADING,
        commandListHeading = HelpFactory.CLI_COMMAND_LIST_HEADING,
        footer = HelpFactory.CLI_FOOTER,
        versionProvider = MainCommand.class
)
public abstract class AbstractCommand implements Runnable, Vue4JCommand {

    /**
     * Generic help option.
     */
    @Mixin
    private HelpOption help = new HelpOption();

    /**
     * Display help if called with no arguments.
     */
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * Application instance.
     */
    private Vue4J vue4j;

    public Vue4J getVue4J() {
        return vue4j;
    }

    public void setVue4J(Vue4J vue4j) {
        this.vue4j = vue4j;
    }

   

}
