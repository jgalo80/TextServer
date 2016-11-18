package net.jgn.cliptext.server;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import net.jgn.cliptext.crypt.Encryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author jose
 */
class CookieManager {

    private static final Logger logger = LoggerFactory.getLogger(CookieManager.class);

    private static final String CRYPTED_USERID_COOKIE = "UID";
    private static final String CLEAR_USER_COOKIE = "USER";
    private static final String KEY_PROPERTY_NAME = "USER_CRYPT_KEY";

    /**
     * Retrieve the crypted user cookie.
     * @param cookies set of cookies
     * @return
     */
    String retrieveCryptedUserIdCookie(Set<Cookie> cookies) {
        Optional<String> userCookie = cookies.stream()
                .filter(cookie -> CRYPTED_USERID_COOKIE.equals(cookie.name()))
                .map(Cookie::value)
                .findFirst();
        return userCookie.orElse(null);
    }

    /**
     * Decrypt user cookie.
     * @return
     */
    String decryptUserId(String userId) {
        if (userId != null) {
            String plainUser = Encryptor.decrypt(System.getProperty(KEY_PROPERTY_NAME), userId);
            if (plainUser == null) {
                logger.warn("Error decrypting {} cookie. Maybe is manipulated");
            }
            return plainUser;
        }
        return null;
    }

    /**
     * Create the crypted user cookie. If the server is running with SSL the cookie is secure and persistent (2 weeks)
     * @param user
     * @param secure
     * @return
     */
    Cookie createCryptedUserCookie(String user, boolean secure) {
        String encryptedUser = Encryptor.encrypt(System.getProperty(KEY_PROPERTY_NAME), user);
        DefaultCookie userCookie = new DefaultCookie(CRYPTED_USERID_COOKIE, encryptedUser);
        userCookie.setPath("/");
        userCookie.setSecure(secure);
        if (secure) {
            userCookie.setMaxAge(TimeUnit.DAYS.toSeconds(14));
        }
        return userCookie;
    }

    /**
     * Create a cookie for storing the user name just for the session
     * @param user
     * @return
     */
    Cookie createClearUserCookie(String user) {
        Cookie cookie = new DefaultCookie(CLEAR_USER_COOKIE, user);
        cookie.setPath("/");
        return cookie;
    }

    /**
     * Extract all cookies from headers
     * @param cookieHeaders
     * @return
     */
    Set<Cookie> extractCookies(List<String> cookieHeaders) {
        if (cookieHeaders != null) {
            return cookieHeaders.stream()
                    .map(ServerCookieDecoder.STRICT::decode)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }
}
