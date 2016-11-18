package net.jgn.cliptext.crypt;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jose
 */
public class EncryptorTest {

    @Test
    public void testEncryptorOk() {
        String key = "0123456789123456"; //
        String crypted = Encryptor.encrypt(key, "hola");
        System.out.println("hola -> [" + crypted + "]");

        Assert.assertTrue(Encryptor.decrypt(key, crypted).equals("hola"));
    }

    @Test
    public void testEncryptorError() {
        String key = "0123456789123456"; //
        String crypted = "xxxe77a03385429chPsNW2XpTjnFNpqOlWSt7Q==";

        String decrypted = Encryptor.decrypt(key, crypted);
        System.out.println("Decryped: " + decrypted);

        Assert.assertNull(Encryptor.decrypt(key, crypted));
    }

}
