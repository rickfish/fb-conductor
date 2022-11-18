package com.bcbsfl.filter.security.helpers;

import java.util.Map;
import java.util.Optional;

public class EnvironmentHelper {
	static final JWTLogger LOGGER = JWTLogger.getInstance();

    public static String getProperty(String key, String defaultValue) {
        String val = null;
        try {
            val = System.getenv(key.replace('.', '_'));
            if (val == null || val.isEmpty()) {
                val = Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return val;
    }

    /**
	 * @param varName
	 * @return Configuration String from the Environmental variables
	 */
	public static String getEnvValue(String varName) {
		Map<String, String> env = System.getenv();
		for (String envName : env.keySet()) {
			if (envName.equalsIgnoreCase(varName)) {
				String envValue = env.get(envName);
				LOGGER.debug(String.format(envName + "=" + envValue));
				if(envValue.equalsIgnoreCase("${"+varName+"}") || envValue.equalsIgnoreCase("")){
					LOGGER.debug("Env Variable exists but does not appear to be set. returning Null");
					envValue = null;
				}
				return envValue;
			}
		}
		LOGGER.debug(String.format(varName + " was not set in the Environmenatal Variables...returning null"));
		return null;
	}
}
