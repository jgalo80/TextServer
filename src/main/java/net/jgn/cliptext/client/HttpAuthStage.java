package net.jgn.cliptext.client;

import net.jgn.cliptext.cmdline.AbstractMatchCommandProcessor;
import net.jgn.cliptext.cmdline.AbstractStartsWithCommandProcessor;
import net.jgn.cliptext.cmdline.ConsoleInputLoop;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Optional;

/**
 * Created by jose on 12/11/16.
 */
public class HttpAuthStage {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private String baseUrl;
    private OkHttpClient client;
    private CookieManager cookieManager;
    private boolean authenticated;

    public HttpAuthStage(String baseUrl) {
        this.baseUrl = baseUrl;
        this.cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieJar cookieJar = new JavaNetCookieJar(cookieManager);
        this.client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
        this.authenticated = false;
    }

    public void loop() throws Exception {
        ConsoleInputLoop inputLoop = new ConsoleInputLoop("[http stage] > ");
        inputLoop.addCommandProcessor(new AbstractMatchCommandProcessor("help") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                logger.info("signup <user> <password>");
                logger.info("login <user> <password>");
                logger.info("setuid <value>");
                logger.info("auth ");
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
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        logger.info("[signup] User registered {}", commandArgs.get(1));
                    } else {
                        logger.warn("[signup] Error registering user {}", commandArgs.get(1));
                    }
                } catch (IOException e) {
                    logger.error("[signup] Error making request", e);
                }
                return true;
            }
        });
        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("login") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                RequestBody requestBody = new FormBody.Builder()
                        .add("user", commandArgs.get(1))
                        .add("passwd", commandArgs.get(2))
                        .build();
                Request request = new Request.Builder().url(baseUrl + "/login").post(requestBody).build();
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        logger.info("[login] User logged {}", commandArgs.get(1));
                        cookieManager.getCookieStore().getCookies().stream()
                                .forEach(c -> logger.info("[login] cookie {}", c));
                    } else {
                        logger.warn("[login] Error registering user {}", commandArgs.get(1));
                    }
                } catch (IOException e) {
                    logger.error("[login] Error making request", e);
                }
                return true;
            }
        });
        inputLoop.addCommandProcessor(new AbstractStartsWithCommandProcessor("auth") {
            @Override
            public boolean execCommand(List<String> commandArgs) {
                boolean continueLoop = true;
                Request request = new Request.Builder().url(baseUrl + "/auth").build();
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        if (response.isRedirect()) {
                            logger.warn("[auth] UID cookie doesn't exist or is invalid");
                            logger.warn("[auth] Execute login again {}", response.header("Location"));
                        } else {
                            continueLoop = false;
                            logger.info("[auth] UID auth cookie is valid");
                            cookieManager.getCookieStore().getCookies().stream()
                                    .forEach(c -> logger.info("[auth] cookie {}", c));
                            authenticated = true;
                        }
                    } else {
                        logger.warn("[auth] Error validating auth cookie");
                    }
                } catch (IOException e) {
                    logger.error("[auth] Error making request", e);
                }
                return continueLoop;
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
                .map(c -> c.getValue())
                .findFirst();
        return userCookie.orElse(null);
    }

    public String getAuthenticatedUid() {
        Optional<String> uidCookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("UID"))
                .map(c -> c.getValue())
                .findFirst();
        return uidCookie.orElse(null);
    }
}
