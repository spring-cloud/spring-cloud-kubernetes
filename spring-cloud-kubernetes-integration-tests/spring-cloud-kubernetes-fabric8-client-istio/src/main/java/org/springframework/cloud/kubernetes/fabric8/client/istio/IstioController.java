/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.fabric8.client.istio;

import java.util.Arrays;
import java.util.List;

import io.fabric8.istio.client.IstioClient;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
public class IstioController {

	private final Environment environment;

	// not used, but just to prove that it is injected
	private final IstioClient istioClient;

	public IstioController(Environment environment, IstioClient istioClient) {
		this.environment = environment;
		this.istioClient = istioClient;
	}

	@GetMapping("/profiles")
	public List<String> profiles() {
		return Arrays.asList(environment.getActiveProfiles());
	}

}
