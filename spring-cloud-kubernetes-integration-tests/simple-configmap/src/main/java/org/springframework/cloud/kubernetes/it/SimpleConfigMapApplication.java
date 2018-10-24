package org.springframework.cloud.kubernetes.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableConfigurationProperties(DummyConfig.class)
public class SimpleConfigMapApplication {

	@Autowired
	private DummyConfig dummyConfig;

	@GetMapping("/greeting")
	public Greeting greeting() {
		return new Greeting(dummyConfig.getMessage());
	}

	public static void main(String[] args) {
		SpringApplication.run(SimpleConfigMapApplication.class, args);
	}

	public static class Greeting {

		private final String message;

		public Greeting(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

}
