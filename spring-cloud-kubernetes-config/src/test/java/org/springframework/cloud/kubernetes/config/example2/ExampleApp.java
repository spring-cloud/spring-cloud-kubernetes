package org.springframework.cloud.kubernetes.config.example2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableConfigurationProperties(ExampleAppProps.class)
public class ExampleApp {

	public static void main(String[] args) {
		SpringApplication.run(org.springframework.cloud.kubernetes.config.example.App.class, args);
	}

	@RestController
	public static class Controller {

		private final ExampleAppProps exampleAppProps;

		public Controller(ExampleAppProps exampleAppProps) {
			this.exampleAppProps = exampleAppProps;
		}

		@GetMapping("/common")
		public Response commonMessage() {
			return new Response(exampleAppProps.getCommonMessage());
		}

		@GetMapping("/m1")
		public Response message1() {
			return new Response(exampleAppProps.getMessage1());
		}

		@GetMapping("/m2")
		public Response message2() {
			return new Response(exampleAppProps.getMessage2());
		}

		@GetMapping("/m3")
		public Response message3() {
			return new Response(exampleAppProps.getMessage3());
		}
	}
	
	public static class Response {
		
		private final String message;

		public Response(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

}
