package net.jgn.cliptext.server;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import net.jgn.cliptext.crypt.Encryptor;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author jose
 */
public class UserCookieManager {

    private static final String CRYPTED_USERID_COOKIE = "UID";
    private static final String CLEAR_USER_COOKIE = "USER";
    private static final String KEY_PROPERTY_NAME = "USER_CRYPT_KEY";

    /**
     * Retrieve the crypted user cookie.
     * @param cookieHeader
     * @return
     */
    public String retrieveCryptedUserCookie(String cookieHeader) {
        if (cookieHeader != null) {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(cookieHeader);
            Optional<String> userCookie = cookies.stream()
                    .filter(cookie -> CRYPTED_USERID_COOKIE.equals(cookie.name()))
                    .map(Cookie::value)
                    .findFirst();
            if (userCookie.isPresent()) {
                String encryptedUser = userCookie.get();
                return Encryptor.decrypt(System.getProperty(KEY_PROPERTY_NAME), encryptedUser);
            }
        }
        return null;
    }

    /**
     * Create the crypted user cookie. If the server is running with SSL the cookie is secure and persistent (2 weeks)
     * @param user
     * @param secure
     * @return
     */
    public Cookie createCryptedUserCookie(String user, boolean secure) {
        String encryptedUser = Encryptor.encrypt(System.getProperty(KEY_PROPERTY_NAME), user);
        DefaultCookie userCookie = new DefaultCookie(CRYPTED_USERID_COOKIE, encryptedUser);
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
    public Cookie createClearUserCookie(String user) {
        Cookie cookie = new DefaultCookie(CLEAR_USER_COOKIE, user);
        cookie.setMaxAge(-1);
        return cookie;
    }
}
