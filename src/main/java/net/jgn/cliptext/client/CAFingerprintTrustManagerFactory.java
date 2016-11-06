package net.jgn.cliptext.client;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.InternalThreadLocalMap;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author jose
 */
public class CAFingerprintTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");
    private static final Pattern FINGERPRINT_STRIP_PATTERN = Pattern.compile(":");
    private static final int SHA1_BYTE_LEN = 20;
    private static final int SHA1_HEX_LEN = SHA1_BYTE_LEN * 2;

    private static final FastThreadLocal<MessageDigest> tlmd = new FastThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                // All Java implementation must have SHA1 digest algorithm.
                throw new Error(e);
            }
        }
    };

    private final TrustManager tm = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
            checkTrusted("client", chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
            checkTrusted("server", chain);
        }

        private void checkTrusted(String type, X509Certificate[] chain) throws CertificateException {
            // Collect certficate issuers names
            Set<String> caDNs = Arrays.stream(chain)
                    .filter(c -> c.getIssuerDN() != null)
                    .map(c -> c.getIssuerDN().getName())
                    .collect(Collectors.toSet());

            Set<X509Certificate> fingerprintNotFoundCerts = new HashSet<>();
            for (X509Certificate cert : chain) {
                if (caDNs.contains(cert.getSubjectDN().getName())) {
                    // cert is an issuer (intermediate)
                    byte[] fingerprint = fingerprint(cert);
                    boolean found = false;
                    for (byte[] allowedFingerprint: fingerprints) {
                        if (Arrays.equals(fingerprint, allowedFingerprint)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        fingerprintNotFoundCerts.add(cert);
                    }

                } else {
                    // validate leaf certificate (my cert)
                    if (!cert.getSubjectDN().getName().equals(leafCertDn)) {
                        throw new CertificateException(
                                type + " certificate not expected ('" + fingerprintNotFoundCerts
                                + "' <> '" + leafCertDn + "')");
                    }
                    cert.checkValidity();
                }
            }

            if (!fingerprintNotFoundCerts.isEmpty()) {
                throw new CertificateException(
                        type + " certificate with unknown fingerprint: " + fingerprintNotFoundCerts);
            }
        }

        private byte[] fingerprint(X509Certificate cert) throws CertificateEncodingException {
            MessageDigest md = tlmd.get();
            md.reset();
            return md.digest(cert.getEncoded());
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return EmptyArrays.EMPTY_X509_CERTIFICATES;
        }
    };

    private final byte[][] fingerprints;
    private final String leafCertDn;

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints in heaxdecimal form
     */
    public CAFingerprintTrustManagerFactory(String leafCertDn, Iterable<String> fingerprints) {
        this(leafCertDn, toFingerprintArray(fingerprints));
    }

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints in heaxdecimal form
     */
    public CAFingerprintTrustManagerFactory(String leafCertDn, String... fingerprints) {
        this(leafCertDn, toFingerprintArray(Arrays.asList(fingerprints)));
    }

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints
     */
    public CAFingerprintTrustManagerFactory(String leafCertDn, byte[]... fingerprints) {
        if (fingerprints == null) {
            throw new NullPointerException("fingerprints");
        }

        List<byte[]> list = InternalThreadLocalMap.get().arrayList();
        for (byte[] f: fingerprints) {
            if (f == null) {
                break;
            }
            if (f.length != SHA1_BYTE_LEN) {
                throw new IllegalArgumentException("malformed fingerprint: " +
                        ByteBufUtil.hexDump(Unpooled.wrappedBuffer(f)) + " (expected: SHA1)");
            }
            list.add(f.clone());
        }
        this.leafCertDn = leafCertDn;
        this.fingerprints = list.toArray(new byte[list.size()][]);
    }

    private static byte[][] toFingerprintArray(Iterable<String> fingerprints) {
        if (fingerprints == null) {
            throw new NullPointerException("fingerprints");
        }

        List<byte[]> list = InternalThreadLocalMap.get().arrayList();
        for (String f: fingerprints) {
            if (f == null) {
                break;
            }

            if (!FINGERPRINT_PATTERN.matcher(f).matches()) {
                throw new IllegalArgumentException("malformed fingerprint: " + f);
            }
            f = FINGERPRINT_STRIP_PATTERN.matcher(f).replaceAll("");
            if (f.length() != SHA1_HEX_LEN) {
                throw new IllegalArgumentException("malformed fingerprint: " + f + " (expected: SHA1)");
            }

            byte[] farr = new byte[SHA1_BYTE_LEN];
            for (int i = 0; i < farr.length; i ++) {
                int strIdx = i << 1;
                farr[i] = (byte) Integer.parseInt(f.substring(strIdx, strIdx + 2), 16);
            }
            list.add(farr);
        }

        return list.toArray(new byte[list.size()][]);
    }

    @Override
    protected void engineInit(KeyStore keyStore) throws Exception { }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception { }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[] { tm };
    }
}

