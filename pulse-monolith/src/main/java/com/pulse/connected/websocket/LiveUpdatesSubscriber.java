package com.pulse.connected.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;

/**
 * Starts the Redis subscription for live updates without making Redis a startup requirement.
 * {@link RedisMessageListenerContainer#start()} fails the whole application context when Redis is
 * unreachable, so the container is not auto-started; this subscribes it from a background thread
 * and retries every {@code retryInterval} until it is listening. Until then the instance still
 * serves its own browsers, it just misses updates produced on other instances.
 */
class LiveUpdatesSubscriber implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesSubscriber.class);

    private final RedisMessageListenerContainer container;
    private final Duration retryInterval;
    private volatile boolean running;
    private volatile Thread worker;

    LiveUpdatesSubscriber(RedisMessageListenerContainer container, Duration retryInterval) {
        this.container = container;
        this.retryInterval = retryInterval;
    }

    @Override
    public void start() {
        running = true;
        Thread thread = new Thread(this::subscribeUntilListening, "live-updates-subscribe");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void subscribeUntilListening() {
        boolean warned = false;
        while (running && !container.isListening()) {
            try {
                container.start();
            } catch (Exception e) {
                if (!warned) {
                    log.warn("Live updates from other instances are paused: cannot subscribe to Redis ({}). "
                            + "Retrying every {}.", e.getMessage(), retryInterval);
                    warned = true;
                }
            }
            if (container.isListening()) {
                if (warned) {
                    log.info("Subscribed to Redis: live updates from other instances resumed.");
                }
                return;
            }
            try {
                Thread.sleep(retryInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
