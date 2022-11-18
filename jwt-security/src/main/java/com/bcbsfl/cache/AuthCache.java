package com.bcbsfl.cache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import com.amazonaws.util.IOUtils;
import com.bcbsfl.filter.security.helpers.EnvironmentHelper;
import com.bcbsfl.filter.security.jwt.ThreadLocalUser;
import com.fb.etd.blueredis.BlueStringCache;

/**
 * Caches Authorization information about a user and a workflow/task. We make the request once to the Authorization server and
 * then cache the results for the next time the same user tries to perform an operation on the same resource.
 *
 */
public class AuthCache extends BlueStringCache{
	public static final String WORKFLOW_RELATED_ACTION = "WORKFLOW";
	public static final String TASK_RELATED_ACTION = "TASK";
	public static final String REQUEST_STRING_DELIMITER = "|";
	public static final String REQUEST_STRING_PARSING_DELIMITER = "\\|";
	
	/**
	 * Url of the auth server
	 */
	public static String url = null;

	public AuthCache() {
		this(EnvironmentHelper.getProperty("nfcapi.url", null));
	}
	
    public AuthCache(String theUrl) {
    	super("Authorization", (id) -> {
   			return makeRequest(id);
    	});
		url = theUrl;
    }

    /**
     * Called if the same request was made earlier for the same user
     * @param requestString a pipe-delimited string containing the user id and the action to be performed on a resource
     * @return
     * @throws Exception
     */
    private static String makeRequest(String requestString) throws Exception {
    	String authorized = "false";
    	String environment = EnvironmentHelper.getProperty("environment", null);
    	try {
	    	String path = "/nfc/api/v1";
			String[] components = requestString.split(REQUEST_STRING_PARSING_DELIMITER);
			if(components.length == 3) {
				switch(components[1].toUpperCase()) {
				case WORKFLOW_RELATED_ACTION:
					path += "/workflows/authorize?workflowname=";
					break;
				case TASK_RELATED_ACTION:
					path += "/workflows/task/authorize?taskname=";
					break;
				default:
					throw new Exception("Invalid request string passed to AuthCache: " + requestString);
				}
			} else {
				throw new Exception("Invalid request string passed to AuthCache - invalid number of string segments: " + requestString);
			}
			String authHeader = ThreadLocalUser.getAuthorizationHeader();
			String fullUrl = url + path + components[2] + "&env=" + environment;
	    	HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
	    	conn.setRequestProperty("Content-Type", "application/json");
	    	if(StringUtils.isNotBlank(authHeader)) {
	    		conn.setRequestProperty("Authorization", authHeader);
	    	}
	    	conn.connect();
	    	int code = conn.getResponseCode();
	    	String error = null;
	    	switch(code) {
	    	case HttpStatus.SC_OK:
		    	authorized = "true";
	    		break;
	    	case HttpStatus.SC_SERVICE_UNAVAILABLE:
		    	throw new Exception("When calling the authorization server it seems like it is unavailable based on an HTTP response code of " + HttpStatus.SC_SERVICE_UNAVAILABLE);
	    	case HttpStatus.SC_FAILED_DEPENDENCY:
		    	throw new Exception("When calling the authorization server it seems like the LDAP service is down because we got an HTTP response code of " + HttpStatus.SC_FAILED_DEPENDENCY);
    		default:
		    	InputStream is = conn.getErrorStream();
		    	error = (is != null ? IOUtils.toString(is) : "No error message was received from the authorization server");
	    	}
	    	if(StringUtils.isNotBlank(error)) {
	    		authorized = error + " (HTTP response code: " + code + ")";
	    	}
    	} catch(Exception e) {
    		throw e;
    	}
    	return authorized;
    }
}
