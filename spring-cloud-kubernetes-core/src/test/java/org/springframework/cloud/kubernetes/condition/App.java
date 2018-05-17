package org.springframework.cloud.kubernetes.condition;

import io.fabric8.openshift.client.OpenShiftClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	static class K8SBean {}

	static class OpenshiftBean {}

	@Configuration
	static class AppConfiguration {

		@Bean
		@ConditionalOnKubernetes
		public K8SBean anyK8sBean() {
			return new K8SBean();
		}

		@Bean
		@ConditionalOnKubernetes(classClientMustAdaptTo = OpenShiftClient.class)
		public OpenshiftBean openshiftBean() {
			return new OpenshiftBean();
		}

	}
}
