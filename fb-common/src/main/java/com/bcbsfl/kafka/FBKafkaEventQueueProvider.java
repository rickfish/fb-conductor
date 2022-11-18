package com.bcbsfl.kafka;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.contribs.queue.kafka.KafkaObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * @author preeth, rickfish
 */
@Singleton
public class FBKafkaEventQueueProvider implements EventQueueProvider {
  private static Logger logger = LoggerFactory.getLogger(FBKafkaEventQueueProvider.class);
  protected Map<String, KafkaObservableQueue> queues = new ConcurrentHashMap<>();
  private Configuration config;

  @Inject
  public FBKafkaEventQueueProvider(Configuration config) {
    this.config = config;
    logger.info("Florida Blue Kafka Event Queue Provider initialized.");
  }

  @Override
  public ObservableQueue getQueue(String queueURI) {
    return queues.computeIfAbsent(queueURI, q -> new FBKafkaObservableQueue(queueURI, config));
  }
}