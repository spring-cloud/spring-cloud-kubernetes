package org.springframework.cloud.kubernetes.commons;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DeleteMeSandbox {

	public static void main(String[] args) throws InterruptedException {
		ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
		Runnable task = () -> {
			System.out.println("running");
		};
		ScheduledFuture<?> future = pool.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS);


		Runnable outputTask = () -> {
			while (true) {
				System.out.println("pool size : " + pool.getQueue().size());
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		};
		Thread thread = new Thread(outputTask);
		thread.start();

		Thread.sleep(3_000);
		future.cancel(true);
	}

}
