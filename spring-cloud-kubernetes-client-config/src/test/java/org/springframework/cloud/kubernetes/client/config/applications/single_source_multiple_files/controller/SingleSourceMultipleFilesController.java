/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files.controller;

import org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files.properties.Color;
import org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files.properties.Name;
import org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files.properties.Shape;
import org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files.properties.Type;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SingleSourceMultipleFilesController {

	private final Name name;

	private final Shape shape;

	private final Color color;

	private final Type type;

	public SingleSourceMultipleFilesController(Name name, Shape shape, Color color, Type type) {
		this.name = name;
		this.shape = shape;
		this.color = color;
		this.type = type;
	}

	@GetMapping("/single_source-multiple-files/type")
	public String type() {
		return type.getType();
	}

	@GetMapping("/single_source-multiple-files/shape")
	public String shape() {
		return shape.getRaw();
	}

	@GetMapping("/single_source-multiple-files/color")
	public String color() {
		return "raw:" + color.getRaw() + "###" + "ripe:" + color.getRipe();
	}

	@GetMapping("/single_source-multiple-files/name")
	public String name() {
		return name.getName();
	}

}
