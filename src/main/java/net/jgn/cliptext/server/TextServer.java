package net.jgn.cliptext.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
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

        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new WebSocketServerCompressionHandler());
                            p.addLast(new WebSocketServerProtocolHandler("/websocket", null, true));
                            p.addLast(new WebSocketLoginHandler());
                            p.addLast(new WebSocketFrameHandler());
                        }
                    });

            // Start the server.
            ChannelFuture f = b.bind("localhost", SSL? 443 : 80).sync();
            logger.info("Text Server started");

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
