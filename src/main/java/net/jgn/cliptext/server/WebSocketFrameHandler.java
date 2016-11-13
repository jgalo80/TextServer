package net.jgn.cliptext.server;

import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelMatchers;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.jgn.cliptext.server.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Echoes uppercase content of text frames.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private static Map<String, ChannelGroup> USER_CHANNELS = new ConcurrentHashMap<>();
    private static ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // stateless JSON serializer/deserializer
    private Gson gson = new Gson();
    private UserCookieManager userCookieManager = new UserCookieManager();
    private String user = null;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            logger.info("Handshake completed. URI: {}", handshake.requestUri());
            logger.info("Handshake completed. Adding channel to ALL_CHANNELS");
            ALL_CHANNELS.add(ctx.channel());

            Optional<String> uidCookieHeader = handshake.requestHeaders().getAll(HttpHeaderNames.COOKIE).stream()
                    .filter(c -> c.startsWith("UID="))
                    .findFirst();

            if (uidCookieHeader.isPresent()) {
                user = userCookieManager.retrieveCryptedUserCookie(uidCookieHeader.get());
                if (user != null) {
                    ChannelGroup cg = USER_CHANNELS.putIfAbsent(user, new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
                    if (cg == null) {
                        cg = USER_CHANNELS.get(user);
                    }
                    cg.add(ctx.channel());
                    logger.info("Handshake completed. Adding channel to USER_CHANNELS[{}]", user);
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // ping and pong frames already handled

        if (frame instanceof TextWebSocketFrame) {
            String frameText = ((TextWebSocketFrame) frame).text();
            logger.info("received ws frame [{}]", frameText);

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
        logger.error("Error in websocket handler", cause);
        super.exceptionCaught(ctx, cause);
    }
}
