package net.jgn.cliptext.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

/**
 * @author jose
 */
public class TextServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;
    private final String websocketPath;
    private final SqlSessionFactory sqlSessionFactory;

    public TextServerInitializer(SqlSessionFactory sqlSessionFactory, SslContext sslContext, String websocketPath) {
        this.sslContext = sslContext;
        this.websocketPath = websocketPath;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        if (sslContext != null) {
            p.addLast(sslContext.newHandler(ch.alloc()));
        }
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(262144));
        p.addLast(new ChunkedWriteHandler());
        p.addLast(new WebSocketServerCompressionHandler());
        p.addLast(new WebSocketServerProtocolHandler(websocketPath, null, true));
        p.addLast(new WebSocketLoginHandler(sqlSessionFactory));
        p.addLast(new WebSocketFrameHandler());
    }

}
