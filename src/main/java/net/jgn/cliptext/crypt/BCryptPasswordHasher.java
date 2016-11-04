package net.jgn.cliptext.crypt;

/**
 * @author jose
 */
public class BCryptPasswordHasher implements PasswordHasher {

    @Override
    public String generateSalt() {
        return BCrypt.gensalt();
    }

    @Override
    public String hashPassword(String clearPw, String salt) {
        return BCrypt.hashpw(clearPw, salt);
    }

    @Override
    public boolean checkPassword(String clearPw, String hashPw) {
        return BCrypt.checkpw(clearPw, hashPw);
    }
}
