package net.jgn.cliptext.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author jose
 */
@Component
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    private static final String SESSIONID_COOKIE = "_SSID_";

    private final Cache<String, Session> sessionStore;

    public SessionManager() {
        sessionStore = CacheBuilder.newBuilder()
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .removalListener(not -> { LOGGER.debug("Session [{}] has expired", not.getKey()); } )
                .build();
    }

    /**
     * Retrieves the session id cookie and look for that id in the session store. Returns null if the
     * cookie doesn't exist or the store doesn't contain the id
     * @param cookies set of cookies
     * @return
     */
    Session retrieveSessionFromCookies(Set<Cookie> cookies) {
        if (cookies != null) {
            Optional<String> userCookie = cookies.stream()
                    .filter(cookie -> SESSIONID_COOKIE.equals(cookie.name()))
                    .map(Cookie::value)
                    .findFirst();
            if (userCookie.isPresent()) {
                String sessionId = userCookie.get();
                LOGGER.debug("Looking for session [{}]", sessionId);
                return sessionStore.getIfPresent(sessionId);
            } else {
                LOGGER.warn("There's no {} cookie", SESSIONID_COOKIE);
            }
        } else {
            LOGGER.warn("There's no cookies!");
        }
        return null;
    }

    /**
     * Creates a new session and sets a cookie with its id in the response
     * @param response
     * @return
     */
    Session createNewSession(FullHttpResponse response) {
        Session session = new Session();
        sessionStore.put(session.getId(), session);
        LOGGER.debug("Creating new session [{}]", session.getId());
        Cookie sessionCookie = new DefaultCookie(SESSIONID_COOKIE, session.getId());
        sessionCookie.setPath("/");
        response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(sessionCookie));
        return session;
    }
}
