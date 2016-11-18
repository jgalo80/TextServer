package net.jgn.cliptext.server;

import com.google.gson.Gson;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelMatchers;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.jgn.cliptext.server.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler of websocket frames
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private static Map<String, ChannelGroup> USER_CHANNELS = new ConcurrentHashMap<>();
    private static ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private final SessionManager sessionManager;

    // stateless JSON serializer/deserializer
    private Gson gson = new Gson();
    private CookieManager cookieManager = new CookieManager();
    private String user = null;

    public WebSocketFrameHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;

            Set<Cookie> cookies = cookieManager.extractCookies(handshake.requestHeaders().getAll(HttpHeaderNames.COOKIE));
            String encryptedUserId = cookieManager.retrieveCryptedUserIdCookie(cookies);
            boolean userValidated = false;

            if (encryptedUserId != null) {
                user = cookieManager.decryptUserId(encryptedUserId);
                if (user != null) {
                    Session session = sessionManager.retrieveSessionFromCookies(cookies);
                    if (session == null) {
                        logger.warn("[handshake completed] There is no session for user {} !!", user);
                    } else if (!user.equals(session.get("USER"))) {
                        logger.warn("[handshake completed] User from the cookie UID is not the same that the one got from the session [{} != {}] !!",
                                user, session.get("USER"));
                    } else {
                        logger.info("[handshake completed] Adding channel {} to ALL_CHANNELS", ctx.channel());
                        ALL_CHANNELS.add(ctx.channel());

                        ChannelGroup cg = USER_CHANNELS.putIfAbsent(user, new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
                        if (cg == null) {
                            cg = USER_CHANNELS.get(user);
                        }
                        cg.add(ctx.channel());
                        logger.info("[handshake completed] Adding channel {} to USER_CHANNELS[{}]", ctx.channel(), user);
                        userValidated = true;
                    }
                } else {
                    logger.warn("[handshake completed] Can't decrypt UID cookie (manipulated?): [{}]", encryptedUserId);
                }
            } else {
                logger.warn("[handshake completed] UID cookie doesn't exist");
            }

            if (!userValidated) {
                // Close the channel from the server
                logger.warn("[handshake completed] Error validating the user after handshake. Closing channel {}", ctx.channel());
                ctx.channel().writeAndFlush(new CloseWebSocketFrame()).addListener(ChannelFutureListener.CLOSE);
            }
        }
        super.userEventTriggered(ctx, evt);
    }



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // ping and pong frames already handled

        if (frame instanceof TextWebSocketFrame) {
            String frameText = ((TextWebSocketFrame) frame).text();
            logger.info("Received websocket frame [{}], channel {}", frameText, ctx.channel());

            Command command = gson.fromJson(frameText, Command.class);
            if (command == null) {
                logger.error("Command not recognized: {}", frameText);
            } else {
                if (user == null) {
                    logger.warn("User not logged!!");

                } else if (Command.MSG_CMD_NAME.equals(command.getCommand())) {
                    logger.info("Message to USER_CHANNELS: {}", user);
                    // Manda el texto (payload) al resto de clientes del mismo grupo
                    USER_CHANNELS.get(user).writeAndFlush(new TextWebSocketFrame(frameText), ChannelMatchers.isNot(ctx.channel()));
                    // Y responde con un ACK al cliente que ha mandado el commando brodcast
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(Command.ACK_COMMAND)));

                } else if (Command.BROADCAST_CMD_NAME.equals(command.getCommand())) {
                    logger.info("Broadcast to ALL_CHANNELS: {}");
                    // Manda el texto (payload) al resto de clientes conectados
                    ALL_CHANNELS.writeAndFlush(new TextWebSocketFrame(frameText), ChannelMatchers.isNot(ctx.channel()));
                    // Y responde con un ACK al cliente que ha mandado el commando brodcast
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(Command.ACK_COMMAND)));

                } else if (Command.STATUS_CMD_NAME.equals(command.getCommand())) {
                    logger.info("Status: {}", user);

                    ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(Command.ACK_COMMAND)));
                }
            }
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Error in websocket handler in channel {}", ctx.channel(), cause);
        super.exceptionCaught(ctx, cause);
    }
}
