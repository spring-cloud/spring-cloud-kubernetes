/*
 * Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Name service controller.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RestController
public class NameController {

	private static final Logger LOG = LoggerFactory.getLogger(NameController.class);

	private final String hostName = System.getenv("HOSTNAME");

	@RequestMapping("/")
	public String ribbonPing() {
		LOG.info("Ribbon ping");
		return this.hostName;
	}

	/**
	 * Endpoint to get a name with a capability to delay a response for some number of milliseconds.
	 *
	 * @param delayValue Milliseconds for how long the response should be delayed.
	 * @return Host name.
	 */
	@RequestMapping("/name")
	public String getName(@RequestParam(value = "delay", defaultValue = "0") int delayValue) {
		LOG.info(String.format("Returning a name '%s' with a delay '%d'", this.hostName, delayValue));
		delay(delayValue);
		return this.hostName;
	}

	private void delay(int delayValue) {
		try {
			Thread.sleep(delayValue);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
