package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.AfterSuite;
import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses(SuiteSandbox.InnerTest.class)
public class SuiteSandbox {

	public static class InnerTest {

		@BeforeSuite
		public static void beforeSuite() {
			System.out.println("go");
		}

		@AfterSuite
		static void afterSuite() {

		}

		@Test
		void test() {

		}
	}

}
