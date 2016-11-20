package net.jgn.cliptext.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Created by jose on 5/11/16.
 */
@Component
public class ClipTextServerImpl implements ClipTextServer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    @Qualifier("bossGroup")
    private NioEventLoopGroup bossGroup;

    @Autowired
    @Qualifier("workerGroup")
    private NioEventLoopGroup workerGroup;

    @Autowired
    private TextServerInitializer textServerInitializer;

    private Channel channel;

    @Override
    public Channel start(String bindingAddress, int port) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(textServerInitializer);

        try {
            // Start the server.
            channel = b.bind(bindingAddress, port).sync().channel();
            logger.info("Text Server started on {}:{}", bindingAddress, port);

            // Wait until the server socket is closed.
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Server interrupted!", e);
        }
        return channel;
    }

    @Override
    public void stop() {
        if (this.channel != null) {
            this.channel.close().addListener(ChannelFutureListener.CLOSE);
        }
        logger.info("Text Server shutdown started");
        // Shut down all event loops to terminate all threads.
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        logger.info("Text Server shutdown completed");
    }
}
