package net.jgn.cliptext.server;

import io.netty.handler.ssl.SslContext;

/**
 * Created by jose on 5/11/16.
 */
public class SslContextWrapper {

    private SslContext sslContext;

    public SslContextWrapper(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

}
