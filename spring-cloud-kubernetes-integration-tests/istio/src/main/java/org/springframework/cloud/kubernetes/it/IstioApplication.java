package org.springframework.cloud.kubernetes.it;

import me.snowdrop.istio.client.IstioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@RestController
public class IstioApplication {

    @Autowired
    private Environment environment;

    // used just to ensure that the IstioClient is properly injected into the context
    @Autowired
    private IstioClient istioClient;

	@GetMapping("/profiles")
	public List<String> profiles() {
		return Arrays.asList(environment.getActiveProfiles());
	}

	public static void main(String[] args) {
		SpringApplication.run(IstioApplication.class, args);
	}

}
