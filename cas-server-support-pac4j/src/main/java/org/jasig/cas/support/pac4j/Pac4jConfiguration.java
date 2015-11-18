package org.jasig.cas.support.pac4j;

import org.pac4j.config.client.ConfigPropertiesFactory;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Initializes the pac4j configuration.
 *
 * @author Jerome Leleu
 * @since 4.2.0
 */
@Configuration
public class Pac4jConfiguration {

    private final static String CAS_PAC4J_PREFIX = "cas.pac4j.";

    @Value("${server.prefix:http://localhost:8080/cas}/login")
    private String serverLoginUrl;

    @Autowired(required = true)
    @Qualifier("casProperties")
    private Properties casProperties;

    @Autowired(required = true)
    private IndirectClient[] clients;

    /**
     * Returning the built clients.
     *
     * @return the built clients.
     */
    @Bean(name = "builtClients")
    public Clients clients() {
        List<Client> allClients = new ArrayList<>();
        // add all indirect clients from the Spring context
        allClients.addAll(Arrays.<Client>asList(clients));

        // turn the properties file into a map of properties
        final Map<String, String> properties = new HashMap<>();
        final Enumeration names = casProperties.propertyNames();
        while (names.hasMoreElements()) {
            final String name = (String) names.nextElement();
            if (name.startsWith(CAS_PAC4J_PREFIX)) {
                properties.put(name.substring(CAS_PAC4J_PREFIX.length()), casProperties.getProperty(name));
            }
        }

        // add the new clients found via properties
        final ConfigFactory configFactory = new ConfigPropertiesFactory(properties);
        final Config propertiesConfig = configFactory.build();
        allClients.addAll(propertiesConfig.getClients().getClients());

        // build a Clients configuration
        if (allClients == null || allClients.size() == 0) {
            throw new IllegalArgumentException("At least one pac4j client must be defined");
        }
        return new Clients(serverLoginUrl, allClients);
    }
}
