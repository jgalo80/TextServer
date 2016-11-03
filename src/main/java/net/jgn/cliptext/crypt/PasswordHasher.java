package net.jgn.cliptext.crypt;

/**
 * @author jose
 */
public interface PasswordHasher {

    /**
     * Generate salt
     * @return
     */
    String generateSalt();

    /**
     * Get the hashed password
     * @param clearPw
     * @param salt
     * @return
     */
    String hashPassword(String clearPw, String salt);

    /**
     * Checks password with the hashed one
     * @param clearPw
     * @param hashPw
     * @return
     */
    boolean checkPassword(String clearPw, String hashPw);

}
