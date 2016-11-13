package net.jgn.cliptext.cmdline;

/**
 * Created by jose on 12/11/16.
 */
public abstract class AbstractMatchCommandProcessor implements CommandProcessor {

    private String command;

    public AbstractMatchCommandProcessor(String command) {
        this.command = command;
    }

    @Override
    public boolean matchCommand(String input) {
        return input.equalsIgnoreCase(command);
    }

}
