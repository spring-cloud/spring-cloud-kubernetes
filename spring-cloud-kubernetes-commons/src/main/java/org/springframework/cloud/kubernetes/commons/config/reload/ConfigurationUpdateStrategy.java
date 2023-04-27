/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.util.Objects;

/**
 * This is the superclass of all named strategies that can be fired when the configuration
 * changes.
 *
 * @author Nicola Ferraro
 */
public record ConfigurationUpdateStrategy(String name, Runnable reloadProcedure) {

	/**
	 * Strategy that does nothing.
	 */
	public static final ConfigurationUpdateStrategy NOOP = new ConfigurationUpdateStrategy("no-op", () -> {

	});

	public ConfigurationUpdateStrategy(String name, Runnable reloadProcedure) {
		this.name = Objects.requireNonNull(name, "name cannot be null");
		this.reloadProcedure = Objects.requireNonNull(reloadProcedure, "reloadProcedure cannot be null");
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "{name='" + this.name + "'}";
	}

}
