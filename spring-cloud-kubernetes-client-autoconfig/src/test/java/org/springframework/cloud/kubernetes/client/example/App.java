/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.example;

import io.kubernetes.client.openapi.ApiClient;
import okhttp3.OkHttpClient;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
@SpringBootApplication
public class App {

	@Bean
	public ApiClient apiClient() {
		ApiClient apiClient = mock(ApiClient.class);
		when(apiClient.getHttpClient()).thenReturn(new OkHttpClient.Builder().build());
		return apiClient;
	}

}
