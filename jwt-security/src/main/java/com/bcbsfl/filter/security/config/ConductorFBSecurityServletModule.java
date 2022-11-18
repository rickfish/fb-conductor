package com.bcbsfl.filter.security.config;

import java.util.Optional;

import com.bcbsfl.config.FBCommonConfiguration;
import com.bcbsfl.config.SystemPropertiesFBCommonConfiguration;
import com.bcbsfl.filter.security.jwt.JwtAuthenticationFilter;
import com.google.inject.servlet.ServletModule;

public class ConductorFBSecurityServletModule extends ServletModule {

	@Override
	protected void configureServlets() {
		
		String path = Optional.ofNullable(System.getProperty("url.path")).orElse(Optional.ofNullable(System.getenv("url_path")).orElse("/api"));
	
		filter(path + "/metadata*").through(JwtAuthenticationFilter.class);
		filter(path + "/workflow*").through(JwtAuthenticationFilter.class);
		filter(path + "/event*").through(JwtAuthenticationFilter.class);
		filter(path + "/poll*").through(JwtAuthenticationFilter.class);
		filter(path + "/admin*").through(JwtAuthenticationFilter.class);
		filter(path + "/tasks*").through(JwtAuthenticationFilter.class);
	}

}
