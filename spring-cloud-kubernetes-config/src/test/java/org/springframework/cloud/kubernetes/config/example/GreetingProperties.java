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

package org.springframework.cloud.kubernetes.config.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bean")
public class GreetingProperties {

	private String greeting = "Hello, %s!";

	private String farewell = "Goodbye, %s!";

	private String morning = "Good morning, %s!";

	private String bonjour = "Bonjour, %s!";

	public String getGreeting() {
		return this.greeting;
	}

	public void setGreeting(String greeting) {
		this.greeting = greeting;
	}

	public String getFarewell() {
		return this.farewell;
	}

	public void setFarewell(String farewell) {
		this.farewell = farewell;
	}

	public String getMorning() {
		return this.morning;
	}

	public void setMorning(String morning) {
		this.morning = morning;
	}

	public String getBonjour() {
		return this.bonjour;
	}

	public void setBonjour(String bonjour) {
		this.bonjour = bonjour;
	}

}
