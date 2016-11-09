package net.jgn.cliptext.cfg;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import net.jgn.cliptext.server.SslContextCreator;
import net.jgn.cliptext.server.SslContextWrapper;
import net.jgn.cliptext.server.TextServerInitializer;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

import javax.net.ssl.SSLException;
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

    @Bean
    public SslContextWrapper sslCtxWrapper() throws SSLException, CertificateException {
        String sslMode = env.getProperty("sslMode", "nossl");
        if (sslMode.equals("cert")) {
            return new SslContextWrapper(SslContextCreator.createContext());
        } else if (sslMode.equals("selfSignedCert")) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            SslContext sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            return new SslContextWrapper(sslCtx);
        } else {
            return new SslContextWrapper(null);
        }
    }

    @Bean
    public TextServerInitializer textServerInitializer() throws IOException, CertificateException {
        return new TextServerInitializer(sqlSessionFactory,
                sslCtxWrapper().getSslContext(),
                env.getProperty("websocketPath"));
    }

}
