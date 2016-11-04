package net.jgn.cliptext.crypt;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jose
 */
public class SHA1PasswordHasherTest {

    @Test
    public void testHash() {
        PasswordHasher hasher = new SHA1PasswordHasher();
        String salt = hasher.generateSalt();
        String hashedPassword = hasher.hashPassword("12345", salt);
        System.out.println("Salt: " + salt + "  hash: " + hashedPassword);

        Assert.assertTrue(hasher.checkPassword("12345", hashedPassword));
    }
}
