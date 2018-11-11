package org.springframework.cloud.kubernetes.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

// inspired by spring-cloud-commons: RefreshAutoConfigurationMoreClassPathTests

@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({"spring-boot-actuator-autoconfigure-*.jar", "spring-boot-starter-actuator-*.jar"})
public class MissingActuatorTest {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Test
	public void unknownClassProtected() {
		try (ConfigurableApplicationContext context = getApplicationContext(
			Config.class, "debug=true")) {
			String output = this.outputCapture.toString();
			assertThat(output).doesNotContain("Failed to introspect annotations on [class org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration")
				.doesNotContain("TypeNotPresentExceptionProxy");
		}
	}

	private static ConfigurableApplicationContext getApplicationContext(
		Class<?> configuration, String... properties) {
		return new SpringApplicationBuilder(configuration).web(WebApplicationType.NONE).properties(properties).run();
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

	}

}
