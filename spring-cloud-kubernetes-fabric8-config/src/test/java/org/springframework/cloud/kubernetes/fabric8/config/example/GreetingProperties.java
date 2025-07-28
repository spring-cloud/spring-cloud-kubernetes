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

package org.springframework.cloud.kubernetes.fabric8.config.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bean")
class GreetingProperties {

	private String greeting = "Hello, %s!";

	private String farewell = "Goodbye, %s!";

	private String morning = "Good morning, %s!";

	private String bonjour = "Bonjour, %s!";

	private List<String> items = List.of();

	private Map<String, String> map = new HashMap<>();

	String getGreeting() {
		return this.greeting;
	}

	void setGreeting(String greeting) {
		this.greeting = greeting;
	}

	String getFarewell() {
		return this.farewell;
	}

	void setFarewell(String farewell) {
		this.farewell = farewell;
	}

	String getMorning() {
		return this.morning;
	}

	void setMorning(String morning) {
		this.morning = morning;
	}

	String getBonjour() {
		return this.bonjour;
	}

	void setBonjour(String bonjour) {
		this.bonjour = bonjour;
	}

	List<String> getItems() {
		return items;
	}

	void setItems(List<String> items) {
		this.items = items;
	}

	Map<String, String> getMap() {
		return map;
	}

	void setMap(Map<String, String> map) {
		this.map = map;
	}

}
