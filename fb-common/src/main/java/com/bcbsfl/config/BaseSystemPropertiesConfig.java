/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 *
 */
package com.bcbsfl.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 *
 */
public class BaseSystemPropertiesConfig implements BaseConfig {

    private static Logger logger = LoggerFactory.getLogger(BaseSystemPropertiesConfig.class);

    @Override
    public int getIntProperty(String key, int defaultValue) {
        String val = getProperty(key, Integer.toString(defaultValue));
        try {
            defaultValue = Integer.parseInt(val);
        } catch (NumberFormatException e) {
        }
        return defaultValue;
    }

    @Override
    public long getLongProperty(String key, long defaultValue) {
        String val = getProperty(key, Long.toString(defaultValue));
        try {
            defaultValue = Integer.parseInt(val);
        } catch (NumberFormatException e) {
        }
        return defaultValue;
    }

    @Override
    public String getProperty(String key, String defaultValue) {

        String val = null;
        try {
            val = System.getenv(key.replace('.', '_'));
            if (val == null || val.isEmpty()) {
                val = Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return val;
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String val = getProperty(name, null);

        if (val != null) {
            return Boolean.parseBoolean(val);
        } else {
            return defaultValue;
        }
    }

    @Override
    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        Properties props = System.getProperties();
        props.entrySet().forEach(entry -> map.put(entry.getKey().toString(), entry.getValue()));
        return map;
    }
}
