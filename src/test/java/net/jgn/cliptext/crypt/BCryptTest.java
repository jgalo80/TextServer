package net.jgn.cliptext.crypt;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jose
 */
public class BCryptTest {

    @Test
    public void testHash() {
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw("12345", salt);
        System.out.println("Salt: " + salt + "  hash: " + hashedPassword);

        Assert.assertTrue(BCrypt.checkpw("12345", hashedPassword));
    }
}
