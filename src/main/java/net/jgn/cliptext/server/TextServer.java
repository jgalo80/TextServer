package net.jgn.cliptext.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Websocket server
 *
 * @author jgalo
 */
public class TextServer {

    private static final Logger logger = LoggerFactory.getLogger(TextServer.class);

    private static final boolean SSL = System.getProperty("SSL") != null;

    /**
     * Main
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Configure SSL.
        final SslContext sslCtx = SSL ? SslContextCreator.createContext() : null;

        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : (SSL ? 443 : 80);

        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new TextServerInitializer(sslCtx, "/websocket"));

            // Start the server.
            ChannelFuture f = b.bind(host, port).sync();
            logger.info("Text Server started on {}:{}", host, port);

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            logger.info("Text Server shutdown started");
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            logger.info("Text Server shutdown completed");
        }
    }
}
