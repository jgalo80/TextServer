package net.jgn.cliptext.server.command;

import java.util.Date;

/**
 * @author jose
 */
public class Command {

    public static final String ACK_CMD_NAME = "ACK";
    public static final String STATUS_CMD_NAME = "STATUS";
    public static final String BROADCAST_CMD_NAME = "BROADCAST";
    public static final String MSG_CMD_NAME = "MSG";

    public static final Command ACK_COMMAND = Command.create().command(ACK_CMD_NAME).build();
    public static final Command STATUS_COMMAND = Command.create().command(STATUS_CMD_NAME).build();

    private String command;
    private String user;
    private String device;
    private String payload;
    private Date date;

    private Command() {
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {
        private Command c;
        private Builder() {
            c = new Command();
        }

        public Builder command(String command) {
            c.command = command;
            return this;
        }
        public Builder user(String user) {
            c.user = user;
            return this;
        }
        public Builder device(String device) {
            c.device = device;
            return this;
        }
        public Builder payload(String payload) {
            c.payload = payload;
            return this;
        }
        public Builder date(Date date) {
            c.date = date;
            return this;
        }
        public Command build() {
            return c;
        }
    }

    public String getCommand() {
        return command;
    }

    public String getUser() {
        return user;
    }

    public String getDevice() {
        return device;
    }

    public String getPayload() {
        return payload;
    }

    public Date getDate() {
        return date;
    }

}
