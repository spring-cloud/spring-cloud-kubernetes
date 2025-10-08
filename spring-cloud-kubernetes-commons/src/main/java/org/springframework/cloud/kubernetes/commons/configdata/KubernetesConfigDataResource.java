/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.configdata;

import java.util.List;

import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.Profiles;
import org.springframework.core.env.Environment;

/**
 * @author Ryan Baxter
 */
public final class KubernetesConfigDataResource extends ConfigDataResource {

	private final Profiles profiles;

	private final Environment environment;

	KubernetesConfigDataResource(boolean optional, Profiles profiles, Environment environment) {
		super(optional);
		this.profiles = profiles;
		this.environment = environment;
	}

	List<String> getAcceptedProfiles() {
		return this.profiles.getAccepted();
	}

	Environment getEnvironment() {
		return this.environment;
	}

}
