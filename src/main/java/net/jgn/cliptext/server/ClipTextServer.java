package net.jgn.cliptext.server;

import io.netty.channel.Channel;

/**
 * Created by jose on 5/11/16.
 */
public interface ClipTextServer {

    /**
     * Starts the server
     * @param bindingAddress
     * @param port
     */
    Channel start(String bindingAddress, int port);

    void stop();
}