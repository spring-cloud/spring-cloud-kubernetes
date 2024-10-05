package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class StatefulTestCase {

	public static List<String> callSequence = new ArrayList<>();

	public static class Test1 {
		@Test
		void statefulTest() {
			callSequence.add("test1");
		}
	}

	public static class Test2 {
		@Test
		void statefulTest() {
			callSequence.add("test2");
		}
	}

}
