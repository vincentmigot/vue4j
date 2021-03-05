package org.vue4j.cli;

import picocli.CommandLine.Option;

/**
 * Helper class used to add easily help functionality to any command.
 * <pre>
 * To add automatic help add:
 * - For a method command, add the following code to the method parameters
 * <code>@Mixin HelpOption help</code>
 * - For a class command, add the following code to the class members
 * <code>
 * &#64;Mixin
 * private HelpOption help = new HelpOption();
 * </code>
 * </pre>
 *
 * @author Vincent Migot
 */
public class HelpOption {

    /**
     * Default help names and description.
     */
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help and exit")
    private boolean help;

    /**
     * Determine if help flag is on.
     *
     * @return true if help is requested and false otherwise
     */
    public boolean isHelp() {
        return help;
    }
}
