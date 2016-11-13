package net.jgn.cliptext.client;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.jgn.cliptext.cmdline.AbstractMatchCommandProcessor;
import net.jgn.cliptext.cmdline.AbstractStartsWithCommandProcessor;
import net.jgn.cliptext.cmdline.ConsoleInputLoop;
import net.jgn.cliptext.server.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

/**
 *
 */
public final class WebSocketStage {

    private static final String ISRG_ROOT_X1_FINGERPRINT = "CA:BD:2A:79:A1:07:6A:31:F2:1D:25:36:35:CB:03:9D:43:29:A5:E8";
    private static final String LETSENCRYPT_AUTH_X3_FINGERPRINT = "E6:A3:B4:5B:06:2D:50:9B:33:82:28:2D:19:6E:FE:97:D5:95:6C:CB";
    private static final String LETSENCRYPT_AUTH_X4_FINGERPRINT = "C0:5E:24:71:E5:89:A5:70:53:F2:74:7E:E0:6A:59:3C:51:3E:23:A5";

    private Logger logger = LoggerFactory.getLogger(getClass());

    private String url;
    private String user;
    private String uid;
    private Channel clientChannel;
    private Gson gson = new Gson();

    public WebSocketStage(String url, String user, String uid) {
        this.url = url;
        this.user = user;
        this.uid = uid;
    }

    public void loop() throws Exception {
        ConsoleInputLoop inputLoop = new ConsoleInputLoop("[websocket stage] > ");

        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("help") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                logger.info("connect");
                logger.info("msg <msg>");
                logger.info("broadcast <msg>");
                logger.info("quit ");
                return true;
            }
        });

        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("connect") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                try {
                    URI uri = new URI(url);
                    String scheme = uri.getScheme();
                    final boolean ssl = "wss".equalsIgnoreCase(scheme);
                    final int port = uri.getPort() == -1 ? (ssl ? 443 : 80) : uri.getPort();
                    final SslContext sslCtx;
                    if (ssl) {
                        String certDN = System.getProperty("certDN");
                        TrustManagerFactory trustManagerFactory = new CAFingerprintTrustManagerFactory(certDN,
                                LETSENCRYPT_AUTH_X3_FINGERPRINT,
                                LETSENCRYPT_AUTH_X4_FINGERPRINT,
                                ISRG_ROOT_X1_FINGERPRINT);
                        sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();
                    } else {
                        sslCtx = null;
                    }

                    EventLoopGroup group = new NioEventLoopGroup();

                    DefaultHttpHeaders customHeaders = new DefaultHttpHeaders();
                    customHeaders.add(HttpHeaderNames.COOKIE, ServerCookieEncoder.STRICT.encode("USER", user));
                    customHeaders.add(HttpHeaderNames.COOKIE, ServerCookieEncoder.STRICT.encode("UID", uid));

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

                    logger.info("Connecting to " + uri);
                    clientChannel = b.connect(uri.getHost(), port).sync().channel();
                    handler.handshakeFuture().sync();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SSLException e) {
                    e.printStackTrace();
                }

                return true;
            }
        });

        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("msg") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                String textMessage = commandArgs.get(1);
                Command msgCommand = Command.create()
                        .command(Command.MSG_CMD_NAME)
                        .payload(textMessage)
                        .date(new Date())
                        .user(user)
                        .build();
                WebSocketFrame frame = new TextWebSocketFrame(gson.toJson(msgCommand));
                clientChannel.writeAndFlush(frame);
                return true;
            }
        });

        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("broadcast") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                String textMessage = commandArgs.get(1);
                Command msgCommand = Command.create()
                        .command(Command.BROADCAST_CMD_NAME)
                        .payload(textMessage)
                        .date(new Date())
                        .user(user)
                        .build();
                WebSocketFrame frame = new TextWebSocketFrame(gson.toJson(msgCommand));
                clientChannel.writeAndFlush(frame);
                return true;
            }
        });

        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("quit") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                logger.info("Exiting...");
                return false;
            }
        });

        inputLoop.inputLoop();
    }

    /**
     * Main
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("URL is required.");
            System.err.println("Usage: WebSocketClient <URL> [user]");
            System.exit(1);
        }
        String url = args[0];
        String user = args.length > 1 ? args[1] : "guest";

        URI uri = new URI(url);
        String scheme = uri.getScheme();

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            System.err.println("Only WS(S) is supported.");
            System.exit(2);
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final int port = uri.getPort() == -1 ? (ssl ? 443 : 80) : uri.getPort();
        final SslContext sslCtx;
        if (ssl) {
            String certDN = System.getProperty("certDN");
            TrustManagerFactory trustManagerFactory = new CAFingerprintTrustManagerFactory(certDN,
                    LETSENCRYPT_AUTH_X3_FINGERPRINT,
                    LETSENCRYPT_AUTH_X4_FINGERPRINT,
                    ISRG_ROOT_X1_FINGERPRINT);
            sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();
        } else {
            sslCtx = null;
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

            System.out.println("Connecting to " + uri);
            Channel ch = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if (msg.toLowerCase().startsWith("signup")) {
                    signup(msg, ch);
                } else if (msg.toLowerCase().startsWith("login")) {
                    login(msg, ch);
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
                            .command(Command.MSG_CMD_NAME)
                            .payload(textMessage)
                            .date(new Date())
                            .user(user)
                            .build();
                    WebSocketFrame frame = new TextWebSocketFrame(gson.toJson(msgCommand));
                    ch.writeAndFlush(frame);
                } else if (msg.toLowerCase().startsWith("broadcast")) {
                    String textMessage = msg.substring(9).trim();
                    Command msgCommand = Command.create()
                            .command(Command.BROADCAST_CMD_NAME)
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

    private static void signup(String msg, Channel channel) {
        String[] params = msg.split("\\s");
        if (params.length == 3) {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/signup");
            //request.headers().set(HttpHeaderNames.HOST, host);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
            String postData = "user=" + params[1] + "&passwd=" + params[2];
            ByteBuf buf = request.content();
            buf.setCharSequence(0, postData, Charset.defaultCharset());
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
            channel.writeAndFlush(request);
        } else {
            System.err.println("[signup] Wrong params: " + msg);
        }
    }

    private static void login(String msg, Channel channel) {
        String[] params = msg.split("\\s");
        if (params.length == 3) {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/login");
            //request.headers().set(HttpHeaderNames.HOST, host);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
            String postData = "user=" + params[1] + "&passwd=" + params[2];
            ByteBuf buf = request.content();
            buf.setCharSequence(0, postData, Charset.defaultCharset());
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
            channel.writeAndFlush(request);
        } else {
            System.err.println("[login] Wrong params: " + msg);
        }
    }
}
