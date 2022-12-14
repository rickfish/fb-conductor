/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.netflix.conductor.contribs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.inject.name.Named;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.kafka.KafkaEventQueueProvider;

/**
 * @author preeth, rickfish
 *
 */
public class KafkaModule extends AbstractModule {
  private static Logger logger = LoggerFactory.getLogger(KafkaModule.class);

  @Override
  protected void configure() {
    logger.info("Kafka module configured.");
  }

  @ProvidesIntoMap
  @StringMapKey("kafka")
  @Singleton
  @Named("EventQueueProviders")
  public EventQueueProvider getKafkaEventQueueProvider(Configuration configuration) {
    return new KafkaEventQueueProvider(configuration);
  }
}