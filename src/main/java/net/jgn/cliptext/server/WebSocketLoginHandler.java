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
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import net.jgn.cliptext.crypt.PasswordHasher;
import net.jgn.cliptext.crypt.SHA1PasswordHasher;
import net.jgn.cliptext.server.repo.UserRepository;
import net.jgn.cliptext.server.user.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author jose
 */
public class WebSocketLoginHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketLoginHandler.class);
    private NettyHttpFileHandler httpFileHandler = new NettyHttpFileHandler();

    private PasswordHasher passwordHasher = new SHA1PasswordHasher();
    private UserCookieManager userCookieManager = new UserCookieManager();

    private final SqlSessionFactory sqlSessionFactory;

    public WebSocketLoginHandler(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
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
            if (req.uri().equals("/signup")) {
                signup(ctx, req);
            } else if (req.uri().equals("/login")) {
                login(ctx, req);
            } else {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            }
            return;
        }

        // Allow only GET methods.
        if (req.method() != GET) {
            if (req.uri().equals("/auth")) {
                auth(ctx, req);
            } else {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            }
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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error en WebSocketLoginHandler", cause);
        ctx.close();
    }

    /**
     * Client needs to make a get request to /auth to performs cookie authorization.
     * If the cookie doesn't exist or is invalid the server redirects to /login
     * @param ctx
     * @param req
     */
    private void auth(ChannelHandlerContext ctx, FullHttpRequest req) {
        String cookieHeader = req.headers().get(HttpHeaderNames.COOKIE);
        boolean userCookieValid = false;

        if (cookieHeader != null) {
            String user = userCookieManager.retrieveCryptedUserCookie(cookieHeader);
            if (user != null) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

                boolean isSecure = ctx.pipeline().get(SslHandler.class) != null;
                // Update the cookie
                Cookie cryptedUserCookie = userCookieManager.createCryptedUserCookie(user, isSecure);
                Cookie clearUserCookie = userCookieManager.createClearUserCookie(user);
                response.headers().set(HttpHeaderNames.SET_COOKIE,
                        ServerCookieEncoder.STRICT.encode(cryptedUserCookie, clearUserCookie));

                // Close the connection as soon as the error message is sent.
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                userCookieValid = true;
            }
        }
        if (!userCookieValid) {
            sendRedirect(ctx, "/login");
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
            if (retrieveUser(loginUser) != null) {
                logger.warn("[/signup] user {} already registered. ", loginUser);
            } else {
                String hashedPassword = passwordHasher.hashPassword(password, passwordHasher.generateSalt());
                logger.debug("[/signup] user {}, hashed password: {}", loginUser, hashedPassword);
                storeUser(loginUser, hashedPassword);
            }
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            // Close the connection as soon as the error message is sent.
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

        } else {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
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
            User user = retrieveUser(loginUser);
            if (user == null) {
                logger.error("[/login] User not registered: {}", loginUser);
                sendError(ctx, HttpResponseStatus.FORBIDDEN);

            } else {
                if (passwordHasher.checkPassword(password, user.getHashPassword())) {
                    logger.info("[/login] User logged in: {}", loginUser);
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    boolean isSecure = ctx.pipeline().get(SslHandler.class) != null;
                    Cookie userCookie = userCookieManager.createCryptedUserCookie(loginUser, isSecure);
                    response.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(userCookie));

                    // Close the connection as soon as the error message is sent.
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                } else {
                    logger.error("[/login] Hash password doesn't match for user: {} - {}", loginUser, user.getHashPassword());
                    sendError(ctx, HttpResponseStatus.FORBIDDEN);
                }
            }
        } else {
            logger.error("[/login] User and/or password not provided");
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
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

    private User retrieveUser(String userName) {
        SqlSession session = sqlSessionFactory.openSession(true);
        UserRepository userRepository;
        try {
            userRepository = session.getMapper(UserRepository.class);
            return userRepository.selectByUserName(userName);
        } finally {
            session.close();
        }
    }

    private User storeUser(String userName, String hashPassword) {
        SqlSession session = sqlSessionFactory.openSession(true);
        UserRepository userRepository;
        User user = new User(userName, hashPassword);
        try {
            userRepository = session.getMapper(UserRepository.class);
            userRepository.insertUser(user);
        } finally {
            session.close();
        }
        return user;
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

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}