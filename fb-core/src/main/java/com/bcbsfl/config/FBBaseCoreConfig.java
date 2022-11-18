/*
 * Copyright 2016 Netflix, Inc.
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
package com.bcbsfl.config;

import java.util.Map;

/**
 * @author Viren
 */
public interface FBBaseCoreConfig {
    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined integer property.
     */
    int getIntProperty(String name, int defaultValue);

    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined string property.
     */
    String getProperty(String name, String defaultValue);

    boolean getBooleanProperty(String name, boolean defaultValue);

    default boolean getBoolProperty(String name, boolean defaultValue) {
        String value = getProperty(name, null);
        if (null == value || value.trim().length() == 0) {
            return defaultValue;
        }
        return Boolean.valueOf(value.trim());
    }

    /**
     * @return Returns all the configurations in a map.
     */
    Map<String, Object> getAll();
    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined Long property.
     */
    long getLongProperty(String name, long defaultValue);
}
