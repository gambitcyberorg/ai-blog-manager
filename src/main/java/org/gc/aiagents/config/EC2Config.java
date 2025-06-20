package org.gc.aiagents.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;

@Configuration
public class EC2Config extends ElasticsearchConfiguration {

    @Value("${es.trust-store}")
    private String  trustStore;

    @Value("${es.username}")
    private String username;

    @Value("${es.password}")
    private String password;

    @Value("${es.cluster-nodes}")
    private String cluster;

    @Override
    public ClientConfiguration clientConfiguration() {
        try {
            var builder = ClientConfiguration.builder()
                    .connectedTo(cluster);
            
            // Only use SSL if trust-store is provided and not empty
            if (trustStore != null && !trustStore.trim().isEmpty()) {
                builder.usingSsl(getSSLContext());
            }
            
            // Only use basic auth if username and password are provided and not empty
            if (username != null && !username.trim().isEmpty() && 
                password != null && !password.trim().isEmpty()) {
                builder.withBasicAuth(username, password);
            }
            
            return builder.withClientConfigurer(ElasticsearchClients.ElasticsearchHttpClientConfigurationCallback.from(httpAsyncClientBuilder -> {
                        // configure the HttpAsyncClient
                        httpAsyncClientBuilder.setKeepAliveStrategy((response, context) -> Duration.ofMinutes(5).toMillis());
                        return httpAsyncClientBuilder;
                    }))
                    .build();
        } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException |
                 KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private SSLContext getSSLContext() throws
            CertificateException,
            IOException, NoSuchAlgorithmException,
            KeyStoreException,
            KeyManagementException
    {
        byte[] decode = Files.readAllBytes(new File(trustStore).toPath());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Certificate ca;
        try (InputStream certificateInputStream = new ByteArrayInputStream(decode)) {
            ca = cf.generateCertificate(certificateInputStream);
        }

        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

}
