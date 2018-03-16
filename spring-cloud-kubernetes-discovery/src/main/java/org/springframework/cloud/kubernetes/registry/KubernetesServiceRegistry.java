package org.springframework.cloud.kubernetes.registry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

public class KubernetesServiceRegistry implements ServiceRegistry<KubernetesRegistration> {

	private static final Log log = LogFactory.getLog(KubernetesServiceRegistry.class);

	public KubernetesServiceRegistry() {
	}

	@Override
	public void register(KubernetesRegistration registration) {
		log.info("Registering : " + registration);
	}

	@Override
	public void deregister(KubernetesRegistration registration) {
		log.info("DeRegistering : " + registration);
	}

	@Override
	public void close() {

	}

	@Override
	public void setStatus(KubernetesRegistration registration,
						  String status) {
		log.info("Set Status for : " + registration + " Status: " + status);

	}

	@Override
	public <T> T getStatus(KubernetesRegistration registration) {
		log.info("Get Status for : " + registration );
		return null;
	}
}
