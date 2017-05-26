/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.examples;

import java.util.Random;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class TraceController implements
ApplicationListener<EmbeddedServletContainerInitializedEvent> {

	private static final Log log = LogFactory.getLog(TraceController.class);

	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private Tracer tracer;
	@Autowired
	private SpanAccessor accessor;
	@Autowired
	private SampleBackground controller;
	@Autowired
	private Random random;
	private int port;

	@RequestMapping("/")
	public String hi() throws InterruptedException {
		Thread.sleep(this.random.nextInt(1000));
		log.info("Home page");
		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/hi2", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/call")
	public Callable<String> call() {
		return new Callable<String>() {
			@Override
			public String call() throws Exception {
				int millis = TraceController.this.random.nextInt(1000);
				Thread.sleep(millis);
				TraceController.this.tracer.addTag("callable-sleep-millis", String.valueOf(millis));
				Span currentSpan = TraceController.this.accessor.getCurrentSpan();
				return "async hi: " + currentSpan;
			}
		};
	}

	@RequestMapping("/async")
	public String async() throws InterruptedException {
		log.info("async");
		this.controller.background();
		return "ho";
	}

	@RequestMapping("/hi2")
	public String hi2() throws InterruptedException {
		log.info("hi2");
		int millis = this.random.nextInt(1000);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));
		return "hi2";
	}

	@RequestMapping("/traced")
	public String traced() throws InterruptedException {
		Span span = this.tracer.createSpan("http:customTraceEndpoint",
				new AlwaysSampler());
		int millis = this.random.nextInt(1000);
		log.info(String.format("Sleeping for [%d] millis", millis));
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		this.tracer.close(span);
		return "traced/" + s;
	}

	@RequestMapping("/start")
	public String start() throws InterruptedException {
		int millis = this.random.nextInt(1000);
		log.info(String.format("Sleeping for [%d] millis", millis));
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		return "start/" + s;
	}

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}
}
