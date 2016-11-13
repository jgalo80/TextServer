package net.jgn.cliptext.cmdline;

import java.util.List;

/**
 * Created by jose on 12/11/16.
 */
public interface CommandProcessor {

    /**
     * Checks if input match with this command
     * @param input
     * @return
     */
    boolean matchCommand(String input);

    /**
     * Executes the command. It returns false if we want to quit the input loop.
     * @param commandArgs
     * @return
     */
    boolean execCommand(List<String> commandArgs);
}
