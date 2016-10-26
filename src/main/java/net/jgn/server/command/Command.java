package net.jgn.server.command;

/**
 * @author jose
 */
public class Command {

    public static Command ACK_COMMAND = new Command("ACK", "ACK");
    public static Command NACK_COMMAND = new Command("NACK", "NACK");

    private String command;
    private String payload;

    Command(String command, String payload) {
        this.command = command;
        this.payload = payload;
    }

    public String getCommand() {
        return command;
    }

    public String getPayload() {
        return payload;
    }

}
