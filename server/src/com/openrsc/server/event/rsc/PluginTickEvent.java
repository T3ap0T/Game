package com.openrsc.server.event.rsc;

import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Future;

public class PluginTickEvent extends GameTickEvent {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final PluginTask pluginTask;
	private final WalkToAction walkToAction; // Storing the Player's context walkToAction so we can cancel the plugin if the walking is cancelled
	private Future<Integer> future = null;

	public PluginTickEvent(final World world, final Mob owner, final String descriptor, final WalkToAction walkToAction, final PluginTask pluginTask) {
		super(world, owner, 0, descriptor, true);
		this.walkToAction = walkToAction;
		this.pluginTask = pluginTask;
	}

	public void run() {
		// We want to cancel this plugin event if the most recently executed walk to action is not the same as this plugin's context walk to action.
		if (walkToAction != null && walkToAction != getPlayerOwner().getLastExecutedWalkToAction()) {
			if (getFuture() != null) {
				// This will trigger PluginTask.pause()'s wait() call to generate an InterruptedException.
				// We then throw a special PluginInterruptedException which can only be caught by PluginTask.action() or PluginTask.call().
				// When these methods catch the exception it will return the thread which will close the PluginTask's thread down.
				getFuture().cancel(true);
			}
			stop();
			return;
		}

		// Submitting in run because we want to only run game code on tick bounds so we start the execution inside of a tick
		if(getFuture() == null) {
			submitPluginTask();
		}

		// Restart the plugin thread if it has waited long enough
		synchronized(getPluginTask()) {
			getPluginTask().tick();

			if(getPluginTask().shouldRun() && !getFuture().isDone()) {
				getPluginTask().run();
			}
		}

		// Wait for the plugin to get to a pause point or finish completely. This also waits for the PluginTask to start which is also intended to run plugin code on tick bounds.
		while(!getPluginTask().isInitialized() || (getPluginTask().isThreadRunning() && getPluginTask().isTickCompleted() && !getFuture().isDone())) {
			try {
				Thread.sleep(1);
			} catch (final InterruptedException ex) {
				LOGGER.catching(ex);
			}
		}

		// Stop this event if the future/thread has completed.
		if (getFuture().isDone()) {
			stop();
			return;
		}
	}

	public void stop() {
		super.stop();
		getPluginTask().stop();
	}

	private void submitPluginTask() {
		setFuture(getWorld().getServer().getPluginHandler().submitPluginTask(getPluginTask()));
	}

	public Future<Integer> getFuture() {
		return future;
	}

	private void setFuture(final Future<Integer> future) {
		this.future = future;
	}

	public final PluginTask getPluginTask() {
		return pluginTask;
	}
}
