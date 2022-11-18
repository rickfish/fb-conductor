package com.bcbsfl.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.apache.commons.lang3.StringUtils;

public class KafkaLoginModule implements LoginModule {
	public KafkaLoginModule() {
		super();
	}
	protected Map<String, Object> sharedState;
	protected Map<String, Object> options;
	@SuppressWarnings("unchecked")
	@Override
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
		this.sharedState = (Map<String, Object>) sharedState;
		this.options = (Map<String, Object>) options;
	}

	@Override
	public boolean login() throws LoginException {
		String userName = getProperty("kafka.default.jaas.username", null);
		String password = getProperty("kafka.default.jaas.password",  null);		
		List<String> messages = new ArrayList<String>();
		
		if (StringUtils.isBlank(userName)) {
			messages.add("The user name is required.");
		}
		
		if (StringUtils.isBlank(password)) {
			messages.add("The password is required.");
		}
		
		if (messages.size() > 0) {
			throw new LoginException(String.join(" ", messages));
		}
		
		sharedState.put("javax.security.auth.login.name", userName);
		sharedState.put("javax.security.auth.login.password", password.toCharArray());
		
		return true;
	}

	@Override
	public boolean logout() throws LoginException {
		return true;
	}
	
	@Override
	public boolean abort() throws LoginException {
		return true;
	}

	@Override
	public boolean commit() throws LoginException {
		return true;
	}

	public String getProperty(String key, String defaultValue) {

		String val = null;
		try{
			val = System.getenv(key.replace('.','_'));
			if (val == null || val.isEmpty()) {
				val = Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return val;
	}
}
