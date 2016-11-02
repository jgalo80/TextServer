package net.jgn.cliptext.server.client;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.jgn.cliptext.server.command.Command;

import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Date;

/**
 * This is an example of a WebSocket client.
 * <p>
 * In order to run this example you need a compatible WebSocket server.
 * Therefore you can either start the WebSocket server from the examples
 * by running WebSocketServer
 * or connect to an existing WebSocket server such as
 * <a href="http://www.websocket.org/echo.html">ws://echo.websocket.org</a>.
 * <p>
 * The client will attempt to connect to the URI passed to it as the first argument.
 * You don't have to specify any arguments if you want to connect to the example WebSocket server,
 * as this is the default.
 */
public final class WebSocketClient {

    private static final String ISRG_ROOT_X1_FINGERPRINT = "CA:BD:2A:79:A1:07:6A:31:F2:1D:25:36:35:CB:03:9D:43:29:A5:E8";
    private static final String LETSENCRYPT_AUTH_X3_FINGERPRINT = "E6:A3:B4:5B:06:2D:50:9B:33:82:28:2D:19:6E:FE:97:D5:95:6C:CB";
    private static final String LETSENCRYPT_AUTH_X4_FINGERPRINT = "C0:5E:24:71:E5:89:A5:70:53:F2:74:7E:E0:6A:59:3C:51:3E:23:A5";

    /**
     * Main
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("URL and certDN are required.");
            System.err.println("Usage: WebSocketClient <URL> <certDN> [user]");
            System.exit(1);
        }
        String url = args[0];
        String certDN = args[1];
        String user = args.length > 2 ? args[2] : "nouser";

        URI uri = new URI(url);
        String scheme = uri.getScheme();

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            System.err.println("Only WS(S) is supported.");
            System.exit(2);
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        final int port;
        if (ssl) {
            TrustManagerFactory trustManagerFactory = new CAFingerprintTrustManagerFactory(certDN,
                    LETSENCRYPT_AUTH_X3_FINGERPRINT,
                    LETSENCRYPT_AUTH_X4_FINGERPRINT,
                    ISRG_ROOT_X1_FINGERPRINT);
            sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();
            port = 443;
        } else {
            sslCtx = null;
            port = 80;
        }

        EventLoopGroup group = new NioEventLoopGroup();
        Gson gson = new Gson();
        try {
            DefaultHttpHeaders customHeaders = new DefaultHttpHeaders();
            customHeaders.add(HttpHeaderNames.COOKIE, ServerCookieEncoder.STRICT.encode("USER", user));

            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            final WebSocketClientHandler handler =
                    new WebSocketClientHandler(
                            WebSocketClientHandshakerFactory.newHandshaker(
                                    uri, WebSocketVersion.V13, null, true, customHeaders));

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), port));
                            }
                            p.addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    WebSocketClientCompressionHandler.INSTANCE,
                                    handler);
                        }
                    });

            Channel ch = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("bye".equals(msg.toLowerCase())) {
                    ch.writeAndFlush(new CloseWebSocketFrame());
                    ch.closeFuture().sync();
                    break;
                } else if ("ping".equals(msg.toLowerCase())) {
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 8, 1, 8, 1 }));
                    ch.writeAndFlush(frame);
                } else if (msg.toLowerCase().startsWith("msg")) {
                    String textMessage = msg.substring(3).trim();
                    Command msgCommand = Command.create()
                            .command("BROADCAST_MESSAGE")
                            .payload(textMessage)
                            .date(new Date())
                            .user(user)
                            .build();
                    WebSocketFrame frame = new TextWebSocketFrame(gson.toJson(msgCommand));
                    ch.writeAndFlush(frame);

                } else {
                    WebSocketFrame frame = new TextWebSocketFrame(msg);
                    ch.writeAndFlush(frame);
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
