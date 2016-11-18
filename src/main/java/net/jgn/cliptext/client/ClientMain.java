package net.jgn.cliptext.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * @author jose
 */
public class ClientMain {

    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("URL is required.");
            logger.error("Usage: <URL>");
            System.exit(1);
        }
        try {
            String url = args[0];
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            logger.info("Conecting to {}", url);

            HttpAuthStage httpAuthStage = new HttpAuthStage(url);
            httpAuthStage.loop();

            if (httpAuthStage.isAuthenticated()) {
                String uid = httpAuthStage.getAuthenticatedUid();
                String user = httpAuthStage.getAuthenticatedUser();
                String sessionId = httpAuthStage.getSessionId();
                String wsUrl = "wss".equalsIgnoreCase(scheme) ?
                        url.replace("https://", "wss://") : url.replace("http://", "ws://");
                wsUrl = wsUrl + "/websocket";

                logger.info("Entering websocket stage. Type 'connect' to open a connection with {}", wsUrl);
                WebSocketStage webSocketStage = new WebSocketStage(wsUrl, sessionId, user, uid);
                webSocketStage.loop();
            }

        } catch (Exception e) {
            logger.error("Unexpected error. ", e);
        }

    }
}
