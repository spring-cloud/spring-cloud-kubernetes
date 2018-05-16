package org.springframework.cloud.kubernetes.condition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	static class AnyK8sBean {}

	static class OnlyVanillaK8sBean {}

	static class OpenshiftBean {}

	@Configuration
	static class AppConfiguration {

		@Bean
		@ConditionalOnKubernetes
		public AnyK8sBean anyK8sBean() {
			return new AnyK8sBean();
		}

		@Bean
		@ConditionalOnKubernetes(requireVanilla = true)
		public OnlyVanillaK8sBean onlyVanillaK8sBean() {
			return new OnlyVanillaK8sBean();
		}

		@Bean
		@ConditionalOnOpenshift
		public OpenshiftBean openshiftBean() {
			return new OpenshiftBean();
		}
	}
}
