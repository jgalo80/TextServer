package net.jgn.cliptext.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import net.jgn.cliptext.crypt.PasswordHasher;
import net.jgn.cliptext.crypt.SHA1PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author jose
 */
public class WebSocketLoginHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketLoginHandler.class);
    private NettyHttpFileHandler httpFileHandler = new NettyHttpFileHandler();

    private static final Map<String, String> USER_MAP = new ConcurrentHashMap<>();
    private PasswordHasher passwordHasher = new SHA1PasswordHasher();

    public WebSocketLoginHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        // If you're going to do normal HTTP POST authentication before upgrading the
        // WebSocket, the recommendation is to handle it right here
        if (req.method() == HttpMethod.POST) {
            if (req.uri().equals("/register")) {
                signup(ctx, req);
            } else if (req.uri().equals("/login")) {
                login(ctx, req);
            }
            return;
        }

        // Allow only GET methods.
        if (req.method() != GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        // Send the demo page and favicon.ico
        if ("/".equals(req.uri())) {
            logger.info("Redirecting {} -> /index.html", req.uri());
            httpFileHandler.sendRedirect(ctx, "/index.html");
            return;
        }

        // Send the index page
        if ("/index.html".equals(req.uri()) || "/js/ws-client.js".equals(req.uri())) {
            logger.info("Serving file: {}", req.uri());
            httpFileHandler.sendFile(ctx, req);
        } else {
            logger.info("Not found: {}", req.uri());
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
        }
    }

    /**
     * Register the user in the system
     * @param ctx
     * @param req
     * @throws IOException
     */
    private void signup(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        String loginUser = getPostAttribute(decoder, "user");
        String password = getPostAttribute(decoder, "passwd");
        decoder.destroy();

        if (loginUser != null && password != null) {
            logger.info("[/signup] Registering user: {}", loginUser);
            if (USER_MAP.containsKey(loginUser)) {
                logger.warn("[/signup] user {} already registered. ", loginUser);
            } else {
                String hashedPassword = passwordHasher.hashPassword(password, passwordHasher.generateSalt());
                logger.debug("[/signup] user {}, hashed password: {}", loginUser, hashedPassword);
                USER_MAP.put(loginUser, hashedPassword);
            }
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            // Close the connection as soon as the error message is sent.
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

        } else {
            httpFileHandler.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Manages user login
     * @param ctx
     * @param req
     * @throws IOException
     */
    private void login(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        String loginUser = getPostAttribute(decoder, "user");
        String password = getPostAttribute(decoder, "passwd");
        decoder.destroy();

        if (loginUser != null && password != null) {
            logger.info("[/login] Authenticating user: {}", loginUser);
            String hashpw = USER_MAP.get(loginUser);
            if (hashpw == null) {
                logger.error("[/login] User not registered: {}", loginUser);
                httpFileHandler.sendError(ctx, HttpResponseStatus.FORBIDDEN);

            } else {
                if (passwordHasher.checkPassword(password, hashpw)) {
                    logger.info("[/login] User logged in: {}", loginUser);
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    DefaultCookie userCookie = new DefaultCookie("USER", loginUser);
                    response.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(userCookie));

                    // Close the connection as soon as the error message is sent.
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                } else {
                    logger.error("[/login] Hash password doesn't match for user: {}", loginUser);
                    httpFileHandler.sendError(ctx, HttpResponseStatus.FORBIDDEN);
                }
            }
        } else {
            logger.error("[/login] User and/or password not provided");
            httpFileHandler.sendError(ctx, HttpResponseStatus.FORBIDDEN);
        }
    }

    private String getPostAttribute(HttpPostRequestDecoder decoder, String attrName) throws IOException {
        String attrValue = null;
        InterfaceHttpData bodyHttpData = decoder.getBodyHttpData(attrName);
        if (bodyHttpData != null && bodyHttpData.getHttpDataType().equals(InterfaceHttpData.HttpDataType.Attribute)) {
            Attribute attrData = (Attribute) bodyHttpData;
            attrValue = attrData.getValue();
        }
        return attrValue;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error en WebSocketLoginHandler", cause);
        ctx.close();
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

}