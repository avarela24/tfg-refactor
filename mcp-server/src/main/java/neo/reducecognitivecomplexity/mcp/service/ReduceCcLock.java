package neo.reducecognitivecomplexity.mcp.service;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

@Component
public class ReduceCcLock {

	private final ReentrantLock lock = new ReentrantLock();

	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}
}