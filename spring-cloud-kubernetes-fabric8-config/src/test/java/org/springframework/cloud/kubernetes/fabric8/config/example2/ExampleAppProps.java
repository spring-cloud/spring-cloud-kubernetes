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

package org.springframework.cloud.kubernetes.fabric8.config.example2;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bean")
public class ExampleAppProps {

	private String commonMessage;

	private String message1;

	private String message2;

	private String message3;

	public String getCommonMessage() {
		return this.commonMessage;
	}

	public void setCommonMessage(String commonMessage) {
		this.commonMessage = commonMessage;
	}

	public String getMessage1() {
		return this.message1;
	}

	public void setMessage1(String message1) {
		this.message1 = message1;
	}

	public String getMessage2() {
		return this.message2;
	}

	public void setMessage2(String message2) {
		this.message2 = message2;
	}

	public String getMessage3() {
		return this.message3;
	}

	public void setMessage3(String message3) {
		this.message3 = message3;
	}

}
