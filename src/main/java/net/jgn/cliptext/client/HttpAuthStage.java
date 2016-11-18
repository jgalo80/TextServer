package net.jgn.cliptext.client;

import net.jgn.cliptext.cmdline.AbstractMatchCommandProcessor;
import net.jgn.cliptext.cmdline.AbstractStartsWithCommandProcessor;
import net.jgn.cliptext.cmdline.ConsoleInputLoop;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

/**
 * Authorization stage of the command line client
 * @author jose
 */
class HttpAuthStage {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private String baseUrl;
    private OkHttpClient client;
    private CookieManager cookieManager;
    private boolean authenticated;

    public HttpAuthStage(String baseUrl) {
        this.baseUrl = baseUrl;
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        this.client = configureClient(baseUrl, cookieManager);
        this.authenticated = false;
    }

    /**
     * Configure the http client
     * @param baseUrl
     * @param cookieManager
     * @return
     */
    private OkHttpClient configureClient(String baseUrl, CookieManager cookieManager) {
        CookieJar cookieJar = new JavaNetCookieJar(cookieManager);
        OkHttpClient okHttpClient = null;
        try {
            URI uri = new URI(baseUrl);
            if (uri.getScheme().equals("https")) {
                if (uri.getPort() != -1) {
                    FileInputStream fin = new FileInputStream("src/main/resources/self-signed-certs/test-self-signed.crt");
                    X509TrustManager trustManager = SelfSignedCertTrustManager.create(fin);
                    SSLSocketFactory sslSocketFactory;
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[] { trustManager }, null);
                    sslSocketFactory = sslContext.getSocketFactory();
                    okHttpClient = new OkHttpClient.Builder()
                            .sslSocketFactory(sslSocketFactory, trustManager)
                            .cookieJar(cookieJar).build();
                }
            }
            if (okHttpClient == null) {
                okHttpClient = new OkHttpClient.Builder().cookieJar(cookieJar).build();
            }

        } catch (URISyntaxException e) {
            logger.error("Error in URL: {}", baseUrl, e);
        } catch (FileNotFoundException e) {
            logger.error("File not found.", e);
        } catch (GeneralSecurityException e) {
            logger.error("GeneralSecurityException...", e);
        }
        return okHttpClient;
    }

    public void loop() throws Exception {
        ConsoleInputLoop inputLoop = new ConsoleInputLoop("[http stage] > ");
        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("help") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                logger.info("signup <user> <password>");
                logger.info("login [<user> <password>]");
                logger.info("setuid <value>");
                logger.info("quit ");
                return true;
            }
        });
        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("setuid") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                HttpCookie uidCookie = new HttpCookie("UID", commandArgs.get(1));
                uidCookie.setDomain("localhost.local");
                uidCookie.setPath("/");
                uidCookie.setMaxAge(-1);
                cookieManager.getCookieStore().add(null, uidCookie);
                cookieManager.getCookieStore().getCookies()
                        .forEach(c -> logger.info("[setuid] cookie {}", c));
                return true;
            }
        });
        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("signup") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                RequestBody requestBody = new FormBody.Builder()
                        .add("user", commandArgs.get(1))
                        .add("passwd", commandArgs.get(2))
                        .build();
                Request request = new Request.Builder().url(baseUrl + "/signup").post(requestBody).build();
                logger.debug("Sending request: {}", request);
                Response response = null;
                try {
                    response = client.newCall(request).execute();
                    logger.debug("Response: {}", response);
                    if (response.isSuccessful()) {
                        logger.info("[signup] User registered {}", commandArgs.get(1));
                    } else {
                        logger.warn("[signup] Error registering user {}", commandArgs.get(1));
                    }
                } catch (IOException e) {
                    logger.error("[signup] Error making request", e);
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
                return true;
            }
        });
        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("login") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                boolean acceptMoreCommands = true;
                String user = null;
                Request request;
                if (commandArgs.size() == 3) {
                    user = commandArgs.get(1);
                    RequestBody requestBody = new FormBody.Builder()
                            .add("user", user)
                            .add("passwd", commandArgs.get(2))
                            .build();
                    request = new Request.Builder().url(baseUrl + "/login").post(requestBody).build();
                    logger.debug("Sending request: {}", request);
                } else {
                    request = new Request.Builder().url(baseUrl + "/login").build();
                }
                Response response = null;
                try {
                    response = client.newCall(request).execute();
                    logger.debug("Response: {}", response);
                    if (response.isSuccessful()) {
                        acceptMoreCommands = false;
                        logger.info("[login] User logged {}", user == null ? "by cookie" : user);
                        cookieManager.getCookieStore().getCookies()
                                .forEach(c -> logger.info("[login] cookie {}", c));
                        authenticated = true;
                    } else {
                        logger.warn("[login] Error logging in");
                    }
                } catch (IOException e) {
                    logger.error("[login] Error making request", e);
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
                return acceptMoreCommands;
            }
        });

        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("quit") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                logger.info("Exiting...");
                return false;
            }
        });
        inputLoop.inputLoop();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getAuthenticatedUser() {
        Optional<String> userCookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("USER"))
                .map(HttpCookie::getValue)
                .findFirst();
        return userCookie.orElse(null);
    }

    public String getSessionId() {
        Optional<String> userCookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("_SSID_"))
                .map(HttpCookie::getValue)
                .findFirst();
        return userCookie.orElse(null);
    }

    public String getAuthenticatedUid() {
        Optional<String> uidCookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("UID"))
                .map(HttpCookie::getValue)
                .findFirst();
        return uidCookie.orElse(null);
    }
}
