/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.it;

import java.util.Arrays;
import java.util.List;

import me.snowdrop.istio.client.IstioClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class IstioApplication {

	@Autowired
	private Environment environment;

	// used just to ensure that the IstioClient is properly injected into the context
	@Autowired
	private IstioClient istioClient;

	public static void main(String[] args) {
		SpringApplication.run(IstioApplication.class, args);
	}

	@GetMapping("/profiles")
	public List<String> profiles() {
		return Arrays.asList(this.environment.getActiveProfiles());
	}

}
