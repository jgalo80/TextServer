package net.jgn.cliptext.crypt;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * @author jose
 */
public class SHA1PasswordHasher implements PasswordHasher {

    @Override
    public String generateSalt() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public String hashPassword(String clearPw, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.reset();
            byte[] digest = md.digest((salt + clearPw).getBytes("UTF-8"));
            return salt + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // always available in jvm
        } catch (UnsupportedEncodingException e) {
            // UTF-8 always supported
        }
        return null;
    }

    @Override
    public boolean checkPassword(String clearPw, String hashPw) {
        if (clearPw == null || hashPw == null) {
            return false;
        }
        if (clearPw.isEmpty() || hashPw.length() <= 32) {
            return false;
        }
        String salt = hashPw.substring(0, 32);
        return hashPw.equals(hashPassword(clearPw, salt));
    }
}
