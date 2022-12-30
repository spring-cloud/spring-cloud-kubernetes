package org.springframework.cloud.kubernetes.commons.config.configdata;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;

record ConfigDataProperties(KubernetesClientProperties clientProperties,
		ConfigMapConfigProperties configMapProperties, SecretsConfigProperties secretsProperties) {

	static Registrar of(ConfigDataLocationResolverContext context) {
		Properties all = Properties.of(context);
		return () -> {
			registerProperties(context, all.clientProperties(), all.configMapProperties(), all.secretsConfigProperties());
			return new ConfigDataProperties(all.clientProperties(), all.configMapProperties(), all.secretsConfigProperties());
		};
	}

	@FunctionalInterface
	interface Registrar {
		ConfigDataProperties register();
	}

	private static void registerProperties(ConfigDataLocationResolverContext resolverContext,
			KubernetesClientProperties clientProperties, ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties) {

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		registerSingle(bootstrapContext, KubernetesClientProperties.class, clientProperties,
			"configDataKubernetesClientProperties");

		registerSingle(bootstrapContext, ConfigMapConfigProperties.class, configMapProperties,
			"configDataConfigMapConfigProperties");

		registerSingle(bootstrapContext, SecretsConfigProperties.class, secretsProperties,
			"configDataSecretsConfigProperties");
	}
}
