package hotel.reservation.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a secondary Tomcat connector on port 7070 for the data API.
 * Web UI runs on 3001 (main), API also accessible on 7070.
 */
@Configuration
public class AdditionalPortConfig {

    private static final int API_PORT = 7070;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalConnector() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(API_PORT);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
