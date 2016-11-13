package net.jgn.cliptext.cmdline;

/**
 * Created by jose on 12/11/16.
 */
public abstract class AbstractStartsWithCommandProcessor implements CommandProcessor {

    private String command;

    public AbstractStartsWithCommandProcessor(String command) {
        this.command = command.toLowerCase();
    }

    @Override
    public boolean matchCommand(String input) {
        return input.toLowerCase().startsWith(command);
    }

}
