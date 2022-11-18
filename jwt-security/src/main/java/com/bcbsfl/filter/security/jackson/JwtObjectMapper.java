package com.bcbsfl.filter.security.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/*
 * This class is used to centralize the Mapper Configurations
 */
public class JwtObjectMapper {
	private static ObjectMapper mapper;
	
	
	public static ObjectMapper getObjectMapper(){
		if(mapper == null){
			mapper = new ObjectMapper();
			
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
	        
	        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
			mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

		}
		return mapper;
	}
	
}
