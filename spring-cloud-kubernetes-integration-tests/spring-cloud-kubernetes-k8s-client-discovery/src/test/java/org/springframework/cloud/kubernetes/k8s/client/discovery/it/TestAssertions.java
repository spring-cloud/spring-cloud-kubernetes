/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.discovery.it;

import java.time.Duration;

import org.springframework.boot.test.system.CapturedOutput;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

final class TestAssertions {

	private TestAssertions() {

	}

	static void assertLogStatement(CapturedOutput output, String textToAssert) {
		await().atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofMillis(200))
			.untilAsserted(() -> assertThat(output.getOut()).contains(textToAssert));
	}

}
