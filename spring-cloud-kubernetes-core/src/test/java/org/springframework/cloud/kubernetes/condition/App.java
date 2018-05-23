package org.springframework.cloud.kubernetes.condition;

import io.fabric8.openshift.client.OpenShiftClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.kubernetes.condition.ConditionalOnKubernetes.Range;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	static class K8SBean {}

	static class K8SVersion18OrNewerBean {}

	static class K8SVersionOlderThan18Bean {}

	static class K8SVersionExactly18Bean {}

	static class OpenshiftBean {}

	static class OpenshiftVersion18OrNewerBean {}

	static class OpenshiftVersionOlderThan111Bean {}

	static class OpenshiftVersionOlderThan18Bean {}

	@Configuration
	static class AppConfiguration {

		@Bean
		@ConditionalOnKubernetes
		public K8SBean k8sBean() {
			return new K8SBean();
		}

		@Bean
		@ConditionalOnKubernetes(version = "1.8")
		public K8SVersion18OrNewerBean k8SVersion18OrNewerBean() {
			return new K8SVersion18OrNewerBean();
		}

		@Bean
		@ConditionalOnKubernetes(version = "1.8", range = Range.OLDER_THAN)
		public K8SVersionOlderThan18Bean k8SVersionOlderThan18Bean() {
			return new K8SVersionOlderThan18Bean();
		}

		@Bean
		@ConditionalOnKubernetes(version = "1.8", range = Range.EXACT)
		public K8SVersionExactly18Bean k8SVersionExactly18Bean() {
			return new K8SVersionExactly18Bean();
		}

		@Bean
		@ConditionalOnKubernetes(classClientMustAdaptTo = OpenShiftClient.class)
		public OpenshiftBean openshiftBean() {
			return new OpenshiftBean();
		}

		@Bean
		@ConditionalOnKubernetes(classClientMustAdaptTo = OpenShiftClient.class, version = "1.8")
		public OpenshiftVersion18OrNewerBean openshiftVersion18OrNewerBean() {
			return new OpenshiftVersion18OrNewerBean();
		}

		@Bean
		@ConditionalOnKubernetes(classClientMustAdaptTo = OpenShiftClient.class,
			version = "1.11", range = Range.OLDER_THAN)
		public OpenshiftVersionOlderThan111Bean openshiftVersionLessThan111Bean() {
			return new OpenshiftVersionOlderThan111Bean();
		}

		@Bean
		@ConditionalOnKubernetes(classClientMustAdaptTo = OpenShiftClient.class,
			version = "1.8", range = Range.OLDER_THAN)
		public OpenshiftVersionOlderThan111Bean openshiftVersionLessThan18Bean() {
			return new OpenshiftVersionOlderThan111Bean();
		}

	}
}
