package com.bcbsfl.filter.security.jwt;

import static com.bcbsfl.filter.security.helpers.AuthHelper.sub;
import static com.bcbsfl.filter.security.helpers.AuthHelper.roles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.bcbsfl.filter.security.config.Configurations;
import com.bcbsfl.filter.security.config.FilterConfigs;
import com.bcbsfl.filter.security.config.UriFilter;
import com.bcbsfl.filter.security.helpers.JWTHelper;
import com.bcbsfl.filter.security.jwt.error.JwtCustomError;
import com.bcbsfl.filter.security.jwt.error.JwtErrorResponse;
import com.google.inject.Singleton;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import net.minidev.json.JSONObject;

@Singleton
public class JwtAuthenticationFilter implements Filter {
	private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	static FilterConfigs config;

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// TODO Auto-generated method stub
		LOGGER.debug("Init JWT Filter");
		config = Configurations.getFilterConfig();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 * javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		Long starttime = System.currentTimeMillis();

		LOGGER.debug("Current Configuration: " + config);
		if (config == null) {
			LOGGER.debug("Init JWT Filter");
			config = Configurations.getFilterConfig();
		}

		HttpServletRequest req = (HttpServletRequest) request;

		if (req.getMethod().equalsIgnoreCase("OPTIONS")) {
			chain.doFilter(request, response);
			return;
		}

		String requestedURI = req.getRequestURI();

		LOGGER.debug("Current URI: " + requestedURI);
		UriFilter currentURIConfig = getUriConfig(requestedURI);
		if (currentURIConfig.getSecured()) {
			LOGGER.debug("URI is secured");
			JWTHelper jwtUtils = new JWTHelper();

			try {
				/*
				 * Store the JWT token in a ThreadLocal so it can be accessed by all components on the same thread.
				 */
				String jwt = jwtUtils.getTokenFromRequest(req);
				ThreadLocalUser.setJwt(jwt);
				
				/*
				 * There is a user id that is in the JWT token and one that is optionally passed in by the calling
				 * application that specifies the actual user that is logged into their application.
				 */
				Claims claims = jwtUtils.getJwtClaims(jwt);
				if(claims != null && claims.get(sub) != null) {
					ThreadLocalUser.setJwtUserId((String) claims.get(sub));
				}
				
				ThreadLocalUser.setLoggedInUserId(req.getHeader(JWTHelper.CONDUCTOR_USER_RACF_HEADER));

				LOGGER.debug("JWT Claims: " + claims);

				/*
				 * Host Header will not match Audience when coming thru SPS The domain can be
				 * derived from the "sm_proxyrequest" header and then compared to the audience.
				 */
				if (!jwtUtils.verifyAudience(claims.getAudience(), req.getHeader("HOST"), claims.getIssuer())) {
					throw new JwtCustomError("Audience defined in JWT is not Allowed for this endpoint");
				}

				// Verify the Roles
				if (!jwtUtils.verifyRole(currentURIConfig.getRoles(), (List<String>) claims.get("roles"))) {
					throw new JwtCustomError("User does not have a role allowed for this path.");
				}
				
				/* 
				 * Get the roles associated with the user and extract their data entitlements and, from that, their valid security roles
				 */
				String[] loggedInUserRoles = getLoggedInUserRoles(req);
				ThreadLocalUser.setDataEntitlements(loggedInUserRoles.length == 0 ? getJwtUserRoles(claims) : loggedInUserRoles);
				ThreadLocalUser.setValidSecRoles(JWTHelper.getValidUserSecRoles());

				// Add Claims information to the Request Attributes.
				// This will allow the Applicaiton to pull information from
				// the Request Attributes. and not require JWT understanding
				request.setAttribute("jwt", claims);
				Set<String> keySet = claims.keySet();
				for (String currentKey : keySet) {
					request.setAttribute(currentKey, claims.get(currentKey));
				}
			} catch (Exception e) {
				unAuthorizedResponse(response, e.getMessage());
				return;
			}
		} else {
			LOGGER.debug("URI Not Secured");
			LOGGER.debug("Filter Time: " + (System.currentTimeMillis() - starttime) + " milliseconds for path "
					+ req.getRequestURI());
		}
		try {
			chain.doFilter(request, response);
			
			JSONObject obj = new JSONObject();
			obj.put("requestURI", ((HttpServletRequest) request).getRequestURI().toString());
			obj.put("response_time", (System.currentTimeMillis() - starttime));
			MDC.put("process_time", ((HttpServletRequest) request).getRequestURI().toString());
			LOGGER.debug(obj.toJSONString());	
			
		} catch (Throwable e) {
			throw e;
		}finally {
			MDC.remove("process_time");
			MDC.clear();
		}
	}

	/**
	 * Used to get the logged-in user roles from a special request header that might or might not be in the request
	 * @param req
	 * @return
	 */
	public static String[] getLoggedInUserRoles(HttpServletRequest req) {
		String roles = req.getHeader(JWTHelper.CONDUCTOR_USER_ROLES_HEADER);
		if(roles != null) {
			String[] rolesArray = roles.split(";");
			List<String> rolesList = new ArrayList<String>();
			for(String role: rolesArray) {
				rolesList.add(role);
			}
			return !rolesList.isEmpty() ? rolesList.toArray(new String[0]) : new String[0];
		}
		return new String[0];
	}

	/**
	 * Used to get the roles from the JWT claims
	 * @param req
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static String[] getJwtUserRoles(Claims claims) {
		List<String> rolesList = (null == claims ? null : (List<String>) claims.get(roles));
		if(rolesList != null) {
			return !rolesList.isEmpty() ? rolesList.toArray(new String[0]) : new String[0];
		}
		return new String[0];
	}

	/*
	 * Generate and respond with the error message
	 */
	/**
	 * @param response
	 * @param errorMessage
	 */
	private void unAuthorizedResponse(ServletResponse response, String errorMessage) {
		LOGGER.error("Returning Authorization error: " + errorMessage);
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		httpResponse.setHeader("JWTAuthError", errorMessage);

		try {
			if (config.getUnAuthRedirect() != null) {
				LOGGER.error("redirecting unauthorized user to " + config.getUnAuthRedirect());
				httpResponse.sendRedirect(config.getUnAuthRedirect());
			} else {
				LOGGER.error("Got an 'unauthorized' error: " + errorMessage);
				JwtErrorResponse error = new JwtErrorResponse();
				error.setCode("403");
				error.setMessage(errorMessage);
				httpResponse.getWriter().append(error.getJSON());
				httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
				httpResponse.flushBuffer();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * @param requestURI
	 * @return UriFilter object containing configuration settings for the specified
	 *         URI
	 */
	private UriFilter getUriConfig(String requestURI) {
		LOGGER.trace("Looping thru Configured Path URIs for a match to " + requestURI);
		for (UriFilter uri : config.getUris()) {
			Pattern pattern = Pattern.compile(uri.getUri());
			Matcher matcher = pattern.matcher(requestURI);
			if (matcher.find()) {
				LOGGER.trace(requestURI + " matched " + uri.getUri());
				return uri;
			}
		}
		LOGGER.trace(
				"No match found.  Returning default URI config with secured equal to " + config.getDefaultSecured());
		UriFilter defaultUri = new UriFilter();
		defaultUri.setUri("/*");
		defaultUri.setSecured(config.getDefaultSecured());
		return defaultUri;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#destroy()
	 */
	@Override
	public void destroy() {

	}

	@SuppressWarnings("unused")
	private Claims buildClaims() {
		DefaultClaims claims = new DefaultClaims();
		claims.put("app", "svc-pmilogin-unit");
		claims.put("sub", "racf");
		claims.put("aud", "pmilogin-unita.bcbsfl.com");

		claims.put("iss", "PMI-Unit");

		List<String> roles = new ArrayList<>();
		roles.add("CN=Data Services Entitlements - BLUECARD - Stage,OU=Requested,OU=Groups,DC=bcbsfl,DC=com");
		// roles.add("CN=Data Services Entitlements - Sensitive Code -
		// Unit,OU=Requested,OU=Groups,DC=bcbsfl,DC=com");
		// roles.add("CN=Data Services Entitlements - Write - FEP -
		// UNIT,OU=Requested,OU=Groups,DC=bcbsfl,DC=com");
		claims.put("roles", roles);
		return claims;
	}

}
