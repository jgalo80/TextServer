package net.jgn.cliptext.server;

import io.netty.channel.Channel;
import net.jgn.cliptext.cfg.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * ServerMain. Spring bootstraping. Starts the server.
 */
public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        ApplicationContext context = new AnnotationConfigApplicationContext(ServerConfig.class);

        String sslMode = context.getEnvironment().getProperty("sslMode", "nossl");
        int port = 8080;
        if (sslMode.equals("cert") || sslMode.equals("selfSignedCert")) {
            port = 8443;
        }

        UserDbInit userDbInit = context.getBean(UserDbInit.class);
        userDbInit.createDbIfNotExists();

        ClipTextServer server = context.getBean(ClipTextServer.class);
        Channel channel = server.start(host, port);

        // Wait until the server socket is closed.
        try {
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Server interrupted!", e);
        }
    }
}
