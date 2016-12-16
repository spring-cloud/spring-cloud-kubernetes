package org.springframework.cloud.kubernetes.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import io.fabric8.kubernetes.client.KubernetesClient;

public class ConfigUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretsPropertySource.class);

    public static <C extends AbstractConfigProperties> String getApplicationName(Environment env, C config) {
        String name = config.getName();
        if (StringUtils.isEmpty(name)) {
            LOGGER.debug(config.getConfigurationTarget() + " name has not been set, taking it from property/env {} (default={})",
                    Constants.SPRING_APPLICATION_NAME,
                    Constants.FALLBACK_APPLICATION_NAME);

            name = env.getProperty(
                    Constants.SPRING_APPLICATION_NAME,
                    Constants.FALLBACK_APPLICATION_NAME);
        }

        return name;
    }

    public static <C extends AbstractConfigProperties> String getApplicationNamespace(KubernetesClient client, Environment env, C config) {
        String namespace = config.getNamespace();
        if (StringUtils.isEmpty(namespace)) {
            LOGGER.debug(config.getConfigurationTarget() + " namespace has not been set, taking it from client (ns={})",
                    client.getNamespace());

            namespace = client.getNamespace();
        }

        return namespace;
    }

}
