package net.jgn.cliptext.cfg;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.jgn.cliptext.server.SessionManager;
import net.jgn.cliptext.server.SslContextWrapper;
import net.jgn.cliptext.server.TextServerInitializer;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;

/**
 * @author jose
 */
@Configuration
@ComponentScan(value = "net.jgn.cliptext.server")
@Import(DbConfig.class)
@PropertySource("classpath:server.properties")
public class ServerConfig {

    @Autowired
    private Environment env;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private SessionManager sessionManager;


    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean(name = "bossGroup")
    @Scope("prototype")
    public NioEventLoopGroup bossGroup() {
        return new NioEventLoopGroup(env.getProperty("bossGroupThreadCount", Integer.class, 1));
    }

    @Bean(name = "workerGroup")
    @Scope("prototype")
    public NioEventLoopGroup workerGroup() {
        return new NioEventLoopGroup();
    }


    @Bean(name = "privateKey")
    public File privateKey() {
        String sslMode = env.getProperty("sslMode", "nossl");
        if (sslMode.equals("cert")) {
            return new File(env.getProperty("PRIV_KEY_FILE"));
        } else if (sslMode.equals("selfSignedCert")) {
            return new File("src/main/resources/self-signed-certs/test.key");
        }
        return null;
    }

    @Bean(name = "certificate")
    public File certificate() {
        String sslMode = env.getProperty("sslMode", "nossl");
        if (sslMode.equals("cert")) {
            return new File(env.getProperty("CERT_CHAIN_FILE"));
        } else if (sslMode.equals("selfSignedCert")) {
            return new File("src/main/resources/self-signed-certs/test-self-signed.crt");
        }
        return null;
    }

    @Bean
    public SslContextWrapper sslCtxWrapper() throws SSLException, CertificateException {
        String sslMode = env.getProperty("sslMode", "nossl");
        if (sslMode.equals("nossl")) {
            return new SslContextWrapper(null);
        } else {
            SslContext sslCtx = SslContextBuilder.forServer(certificate(), privateKey()).build();
            return new SslContextWrapper(sslCtx);
        }
    }

    @Bean
    public TextServerInitializer textServerInitializer() throws IOException, CertificateException {
        return new TextServerInitializer(sqlSessionFactory,
                sslCtxWrapper().getSslContext(),
                sessionManager,
                env.getProperty("websocketPath"));
    }

}
