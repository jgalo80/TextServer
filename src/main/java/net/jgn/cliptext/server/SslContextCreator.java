package net.jgn.cliptext.server;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.File;

/**
 * @author jose
 */
public class SslContextCreator {

    public static SslContext createContext() throws SSLException {
        SslContext sslContext = null;
        String certChain = System.getProperty("CERT_CHAIN_FILE");
        String privKey = System.getProperty("PRIV_KEY_FILE");
        if (certChain == null || privKey == null) {
            throw new IllegalStateException("CERT_CHAIN_FILE and PRIV_KEY_FILE are required system properties");
        }
        File certChainFile = new File(certChain);
        if (!certChainFile.canRead()) {
            throw new IllegalStateException(certChain + " (CERT_CHAIN_FILE) doesn't exist or isn't readable");
        }
        File privKeyFile = new File(privKey);
        if (!privKeyFile.canRead()) {
            throw new IllegalStateException(privKey + " (PRIV_KEY_FILE) doesn't exist or isn't readable");
        }
        return SslContextBuilder.forServer(certChainFile, privKeyFile).build();
    }
}
