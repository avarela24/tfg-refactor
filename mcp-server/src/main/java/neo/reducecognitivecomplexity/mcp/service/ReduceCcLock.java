package neo.reducecognitivecomplexity.mcp.service;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * Bloqueo compartido entre todas las llamadas que invocan reduce-cc.
 * <p>
 * reduce-cc arranca Eclipse contra el mismo workspace en disco, y
 * Eclipse solo permite una instancia abierta sobre un workspace a la
 * vez — así que servimos las llamadas una detrás de otra en vez de
 * dejarlas competir por el mismo workspace.
 * <p>
 * Sin límite de espera propio: quien tenga el candado puede tardar lo
 * que necesite (incluso horas, en un proyecto grande), y quien espera
 * confía en que el propio servicio (ver DockerAnalysisService) detecta
 * y corta cualquier atasco real por silencio prolongado, liberando el
 * candado — así que aquí no hace falta duplicar esa lógica.
 */
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