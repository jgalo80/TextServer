package net.jgn.cliptext.client;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import net.jgn.cliptext.cmdline.AbstractMatchCommandProcessor;
import net.jgn.cliptext.cmdline.AbstractStartsWithCommandProcessor;
import net.jgn.cliptext.cmdline.ConsoleInputLoop;
import net.jgn.cliptext.server.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.URISyntaxException;
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
    private String sessionId;
    private String uid;

    private EventLoopGroup clientEventLoopGroup;
    private Channel clientChannel;
    private Gson gson = new Gson();

    public WebSocketStage(String url, String sessionId, String user, String uid) {
        this.url = url;
        this.user = user;
        this.sessionId = sessionId;
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
                        TrustManagerFactory trustManagerFactory;
                        if (port == 443) {
                            String certDN = System.getProperty("certDN");
                            trustManagerFactory = new CAFingerprintTrustManagerFactory(certDN,
                                    LETSENCRYPT_AUTH_X3_FINGERPRINT,
                                    LETSENCRYPT_AUTH_X4_FINGERPRINT);
                        } else {
                            // If port is not 443, the server is considered as a test environment
                            trustManagerFactory = InsecureTrustManagerFactory.INSTANCE;
                        }
                        sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();
                    } else {
                        sslCtx = null;
                    }

                    clientEventLoopGroup = new NioEventLoopGroup();

                    DefaultHttpHeaders customHeaders = new DefaultHttpHeaders();
                    Cookie sessionCookie = new DefaultCookie("_SSID_", sessionId);
                    Cookie uidCookie = new DefaultCookie("UID", uid);
                    Cookie userCookie = new DefaultCookie("USER", user);

                    customHeaders.add(HttpHeaderNames.COOKIE,
                            ClientCookieEncoder.STRICT.encode(sessionCookie, uidCookie, userCookie));

                    // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
                    // If you change it to V00, ping is not supported and remember to change
                    // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
                    final WebSocketClientHandler handler =
                            new WebSocketClientHandler(
                                    WebSocketClientHandshakerFactory.newHandshaker(
                                            uri, WebSocketVersion.V13, null, true, customHeaders));

                    Bootstrap b = new Bootstrap();
                    b.group(clientEventLoopGroup)
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
                    logger.error("Error en la URL", e);
                } catch (InterruptedException e) {
                    logger.error("Interrupted!", e);
                } catch (SSLException e) {
                    logger.error("Error SSL", e);
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
                if (clientEventLoopGroup != null) {
                    clientEventLoopGroup.shutdownGracefully();
                }
                return false;
            }
        });

        inputLoop.inputLoop();
    }

}
