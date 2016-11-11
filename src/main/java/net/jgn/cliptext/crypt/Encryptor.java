package net.jgn.cliptext.crypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * @author jose
 */
public class Encryptor {

    private static final Logger logger = LoggerFactory.getLogger(Encryptor.class);

    public static String encrypt(String key, String value) {
        try {
            String initVector = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes());
            return initVector + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            logger.error("Error encrypting text [{}]", value);
        }

        return null;
    }

    public static String decrypt(String key, String encrypted) {
        try {
            if (encrypted == null || encrypted.length() < 16) {
                throw new IllegalArgumentException("encrypted text isn't long enough");
            }
            String initVector = encrypted.substring(0, 16);
            String realEncrypted = encrypted.substring(16);

            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(realEncrypted));

            return new String(original);
        } catch (Exception ex) {
            logger.error("Error decrypting text [{}]", encrypted);
        }
        return null;
    }

}
