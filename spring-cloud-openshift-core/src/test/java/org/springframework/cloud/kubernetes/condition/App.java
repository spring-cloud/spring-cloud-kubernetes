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

	static class OpenshiftBean {}

	@Configuration
	static class AppConfiguration {

		@Bean
		@ConditionalOnOpenshift
		public OpenshiftBean anyK8sBean() {
			return new OpenshiftBean();
		}

	}
}
