/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.EnvReader;

/**
 * @author wind57
 */
class LeaderUtilsTests {

	@Test
	void hostNameReadFromEnvProperty() throws UnknownHostException {

		MockedStatic<EnvReader> envReaderMockedStatic = Mockito.mockStatic(EnvReader.class);
		envReaderMockedStatic.when(() -> EnvReader.getEnv("HOSTNAME")).thenReturn("from-env");

		String hostname = LeaderUtils.hostName();
		Assertions.assertEquals("from-env", hostname);

		envReaderMockedStatic.close();

	}

	@Test
	void hostNameReadFromApiCall() throws UnknownHostException {

		MockedStatic<EnvReader> envReaderMockedStatic = Mockito.mockStatic(EnvReader.class);
		envReaderMockedStatic.when(() -> EnvReader.getEnv("HOSTNAME")).thenReturn("");

		MockedStatic<InetAddress> inet4AddressMockedStatic = Mockito.mockStatic(InetAddress.class);
		InetAddress inetAddress = Mockito.mock(InetAddress.class);
		Mockito.when(inetAddress.getHostName()).thenReturn("from-api-call");
		inet4AddressMockedStatic.when(InetAddress::getLocalHost).thenReturn(inetAddress);

		String hostname = LeaderUtils.hostName();
		Assertions.assertEquals("from-api-call", hostname);

		envReaderMockedStatic.close();
		inet4AddressMockedStatic.close();
	}

}
