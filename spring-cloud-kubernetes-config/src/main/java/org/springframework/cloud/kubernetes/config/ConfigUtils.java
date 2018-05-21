package org.springframework.cloud.kubernetes.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import io.fabric8.kubernetes.client.KubernetesClient;

import static org.springframework.cloud.kubernetes.config.Constants.FALLBACK_APPLICATION_NAME;
import static org.springframework.cloud.kubernetes.config.Constants.SPRING_APPLICATION_NAME;

public class ConfigUtils {

	private static final Log LOG = LogFactory.getLog(ConfigUtils.class);

    public static <C extends AbstractConfigProperties> String getApplicationName(Environment env,
		String configName, String configurationTarget) {
        String name = configName;
        if (StringUtils.isEmpty(name)) {
        	//TODO: use relaxed binding
            if (LOG.isDebugEnabled()) {
                LOG.debug(configurationTarget +
                        " name has not been set, taking it from property/env " +
                        SPRING_APPLICATION_NAME + " (default=" + FALLBACK_APPLICATION_NAME + ")");
            }

            name = env.getProperty(SPRING_APPLICATION_NAME, FALLBACK_APPLICATION_NAME);
        }

        return name;
    }

    public static <C extends AbstractConfigProperties> String getApplicationNamespace(
		KubernetesClient client, String configNamespace, String configurationTarget) {
        String namespace = configNamespace;
        if (StringUtils.isEmpty(namespace)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
					configurationTarget + " namespace has not been set, taking it from client (ns="+client.getNamespace()+")");
            }

            namespace = client.getNamespace();
        }

        return namespace;
    }

}
