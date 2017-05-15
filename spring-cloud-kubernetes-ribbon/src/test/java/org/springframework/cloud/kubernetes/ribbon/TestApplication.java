package org.springframework.cloud.kubernetes.ribbon;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * @author <a href="mailto:cmoullia@redhat.com">Charles Moulliard</a>
 */
@SpringBootApplication
public class TestApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestApplication.class, args);
	}

	@LoadBalanced
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}


	// TODO - Should be refactored when we will fix how the Default client could be created out of the box
	@Bean
	public IClientConfig ribbonClientConfig() {
		DefaultClientConfigImpl config = new DefaultClientConfigImpl();
		config.setClientName("testapp");
		config.setProperty(KubernetesConfigKey.Namespace,"testns");
		config.setProperty(CommonClientConfigKey.ServerListRefreshInterval,"500");
		return config;
	}

}
