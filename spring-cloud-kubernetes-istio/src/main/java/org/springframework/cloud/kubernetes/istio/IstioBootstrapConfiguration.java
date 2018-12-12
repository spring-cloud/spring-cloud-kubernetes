package org.springframework.cloud.kubernetes.istio;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.istio.utils.MeshUtils;
import org.springframework.cloud.kubernetes.istio.utils.StandardMeshUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
@EnableConfigurationProperties(IstioClientProperties.class)
public class IstioBootstrapConfiguration {

	private static final Log LOG = LogFactory.getLog(IstioBootstrapConfiguration.class);

	private static final String ISTIO_PROFILE = "istio";

	@Bean
	@ConditionalOnMissingBean
	public MeshUtils istioMeshUtils(IstioClientProperties istioClientProperties) {
		return new StandardMeshUtils(istioClientProperties);
	}

	@EnableConfigurationProperties(IstioClientProperties.class)
	protected static class IstioDetectionConfiguration {

		private final MeshUtils utils;


		private final ConfigurableEnvironment environment;

		public IstioDetectionConfiguration(MeshUtils utils, ConfigurableEnvironment environment) {
			this.utils = utils;
			this.environment = environment;
		}

		@PostConstruct
		public void detectIstio() {
			addIstioProfile(environment);
		}

		void addIstioProfile(ConfigurableEnvironment environment) {
			if (utils.isIstioEnabled()) {
				if (hasIstioProfile(environment)) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("'istio' already in list of active profiles");
					}
				} else {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Adding 'istio' to list of active profiles");
					}
					environment.addActiveProfile(ISTIO_PROFILE);
				}
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.warn("Not running inside kubernetes with istio enabled. Skipping 'istio' profile activation.");
				}
			}
		}

		private boolean hasIstioProfile(Environment environment) {
			for (String activeProfile : environment.getActiveProfiles()) {
				if (ISTIO_PROFILE.equalsIgnoreCase(activeProfile)) {
					return true;
				}
			}
			return false;
		}
	}


}
