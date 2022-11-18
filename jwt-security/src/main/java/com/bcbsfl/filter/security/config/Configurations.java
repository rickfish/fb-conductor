package com.bcbsfl.filter.security.config;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import org.dozer.DozerBeanMapper;
import org.dozer.loader.api.BeanMappingBuilder;
import org.dozer.loader.api.TypeMappingOptions;

import com.bcbsfl.filter.security.helpers.EnvironmentHelper;
import com.bcbsfl.filter.security.helpers.JWTLogger;
import com.bcbsfl.filter.security.jackson.JwtObjectMapper;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class Configurations {
	private static final String JWT_FILTER_CONFIG = "JWTFilterConfig";
	private static final String[] JWT_FILTER_CONFIG_OVERRIDE = { "logLevel","logSysOut","enabled", "unAuthRedirect", "defaultSecured", "acceptedAudience", "certReload", "expirationClockSkew" };
	static final JWTLogger LOGGER = JWTLogger.getInstance();

	{
		populateConfigurations();
	}

	static FilterConfigs filterConfig;

	/**
	 * @return
	 */
	public static FilterConfigs getFilterConfig() {
		LOGGER.debug("Entered GetFilterConfig Method");
		if (filterConfig == null) {
			synchronized (Configurations.class) {
				populateConfigurations();
				return filterConfig;
			}
		}
		return filterConfig;
	}

	/**
	 * @param filterConfig
	 */
	public static void setFilterConfig(FilterConfigs filterConfig) {
		Configurations.filterConfig = filterConfig;
	}

	/**
	 * Pull Configuration string from the Environmental Variable or build
	 * Default configuartion object
	 */
	private static void populateConfigurations() {
		String filterString = EnvironmentHelper.getEnvValue(JWT_FILTER_CONFIG);
		try {
			filterConfig = loadDefaultConfig();
			if(EnvironmentHelper.getEnvValue("environment") != null){
				filterConfig.setEnv(EnvironmentHelper.getEnvValue("environment"));
			}
			
			if (filterString != null) {
				DozerBeanMapper mapper = new DozerBeanMapper();
				mapper.addMapping(new BeanMappingBuilder() {

					@Override
					protected void configure() {
						mapping(FilterConfigs.class, FilterConfigs.class, TypeMappingOptions.mapNull(false));

					}
				});
				mapper.map(JwtObjectMapper.getObjectMapper().readValue(filterString, FilterConfigs.class), filterConfig);
				// filterConfig =
				// JwtObjectMapper.getObjectMapper().readValue(filterString,
				// FilterConfigs.class);
			} else {
				LOGGER.debug("FilterString was null.  Populating filter configuraiton with default configuraitons");

			}
			

			for (String c : JWT_FILTER_CONFIG_OVERRIDE) {
				try {
					String envVar = EnvironmentHelper.getEnvValue(JWT_FILTER_CONFIG + "_" + c);
					if (envVar != null) {
						if (filterConfig.property(c).metaProperty().propertyType().getName() == "java.lang.Boolean") {
							filterConfig.metaBean().propertySet(filterConfig, c, Boolean.valueOf(envVar), true);
						} else if (filterConfig.property(c).metaProperty().propertyType().getName() == "java.util.List") {
							List<String> audAllowed = JwtObjectMapper.getObjectMapper().readValue(envVar, List.class);
							filterConfig.metaBean().propertySet(filterConfig, c, audAllowed, true);
						} else if (filterConfig.property(c).metaProperty().propertyType().getName() == "java.lang.Integer") {
							filterConfig.metaBean().propertySet(filterConfig, c, Integer.valueOf(envVar), true);
						} else {
							filterConfig.metaBean().propertySet(filterConfig, c, envVar, true);
						}
					}
				} catch (NoSuchElementException e) {
					LOGGER.warn("Unknown Property: " + c);
				}
			}

		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @return
	 */
	private static FilterConfigs loadDefaultConfig() {
		FilterConfigs defaultConfig = new FilterConfigs();

		try {
			defaultConfig = JwtObjectMapper.getObjectMapper().readValue(Configurations.class.getResourceAsStream("/defaultConfiguration.json"), FilterConfigs.class);
		} catch (JsonParseException e) {
			
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return defaultConfig;
	}

	

}
