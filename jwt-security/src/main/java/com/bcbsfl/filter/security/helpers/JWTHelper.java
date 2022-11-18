package com.bcbsfl.filter.security.helpers;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.security.cert.CertificateException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import com.bcbsfl.filter.security.config.Configurations;
import com.bcbsfl.filter.security.jwt.ThreadLocalUser;
import com.bcbsfl.filter.security.jwt.error.JwtCustomError;
import com.bcbsfl.filter.security.key.JwtSigningKeyResolver;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

public class JWTHelper {
	private static final String ALL = "ALL";
	public static final String AUTH_HEADER_PREFIX = "(Bearer |bearer |BEARER )";
//	private static final String JWT_COOKIE_NAME = "PMI_JWT";
	public static final String AUTHORIZATION_HEADER = "authorization";
	/**
	 * These might be sent in the request by one of our APIs. The single portal uses userapi to get to conductor so they are passing
	 * the etd service account in the basic auth header. But we need the actual user that logged into the conductor ui to perform
	 * data entitlement validations. So since the JWT will be the one for our service account, we need the additional headers for the
	 * logged-in user information.
	 */
	public static final String CONDUCTOR_USER_RACF_HEADER = "Conductor-User-Racf";
	public static final String CONDUCTOR_USER_ROLES_HEADER = "Conductor-User-Roles";
	
	private static final JWTLogger LOGGER = JWTLogger.getInstance();

	/**
	 * Used to get the user id from the JWT 
	 * @return
	 */
	public static String getJwtUserId() {
		return ThreadLocalUser.getJwtUserId();
	}

	/**
	 * Used to get the user id of the user that is logged in to the application making the request 
	 * @return
	 */
	public static String getLoggedInUserId() {
		return ThreadLocalUser.getLoggedInUserId();
	}

	/**
	 * Used to get the data entitlements of the user
	 * @return
	 */
	public static String[] getDataEntitlements() {
		return ThreadLocalUser.getDataEntitlements();
	}

	/**
	 * Used to get the data entitlements of the user in the form of a list
	 * @return
	 */
	public static List<String> getDataEntitlementsList() {
		List<String> dataEntitlements = new ArrayList<String>();
		for(String dataEntitlement : ThreadLocalUser.getDataEntitlements()) {
			dataEntitlements.add(dataEntitlement);
		}
		return dataEntitlements;
	}
	
	public static String[] getValidUserSecRoles() {
		String[] dataEntitlements = getDataEntitlements();
		if(dataEntitlements.length > 0) {
			List<String> allValidSecRoles = new ArrayList<String>();
			List<String> validSecRoles = null;
			for(String dataEntitlement : dataEntitlements) {
				validSecRoles = AuthHelper.getSecRolesForDataEntitlement(dataEntitlement);
				if(validSecRoles != null) {
					for(String secRole : validSecRoles) {
						if(!allValidSecRoles.contains(secRole)) {
							allValidSecRoles.add(secRole);
						}
					}
					
				}
			}
			return (allValidSecRoles.size() > 0 ? allValidSecRoles.toArray(new String[0]) : new String[0]);
		}
		return new String[0];
	}
	
	/**
	 * Used to get the user id of either the logged-in user if there is one or the one from the JWT if not
	 * @return
	 */
	public static String getUserId() {
		String loggedInUserId = getLoggedInUserId();
		return loggedInUserId == null ? getJwtUserId() : loggedInUserId;
	}

	/* (non-Javadoc)
	 * @see com.bcbsfl.filter.security.IJWTHelper#getJwtClaims(java.lang.String, java.security.PublicKey)
	 */
	public Claims getJwtClaims(String jwt) throws ExpiredJwtException, UnsupportedJwtException,
			MalformedJwtException, SignatureException, IllegalArgumentException, CertificateException, JwtCustomError {
		if(jwt == null){
			throw new JwtCustomError("Authorization token can not be empty");
		}
		//Determine the number of parts in the JWT
		//if there are 2 parts that JWT is not signed and will be allowed only in local or sandbox environments
		switch (StringUtils.split(jwt,".").length) {
		case 2:
			if(Configurations.getFilterConfig().getEnv().equalsIgnoreCase("local") || Configurations.getFilterConfig().getEnv().equalsIgnoreCase("sandbox") || Configurations.getFilterConfig().getEnv().equalsIgnoreCase("dev"))
				return Jwts.parser().setAllowedClockSkewSeconds(Configurations.getFilterConfig().getExpirationClockSkew()).parseClaimsJwt(jwt).getBody();
			break;
		default:
			return Jwts.parser().setSigningKeyResolver(new JwtSigningKeyResolver()).setAllowedClockSkewSeconds(Configurations.getFilterConfig().getExpirationClockSkew()).parseClaimsJws(jwt).getBody();
		}
		return Jwts.parser().setSigningKeyResolver(new JwtSigningKeyResolver()).setAllowedClockSkewSeconds(Configurations.getFilterConfig().getExpirationClockSkew()).parseClaimsJws(jwt).getBody();
	}

	/* (non-Javadoc)
	 * @see com.bcbsfl.filter.security.IJWTHelper#validateJWT(java.lang.String, java.security.PublicKey)
	 */
	public boolean validateJWT(String jwt) throws ExpiredJwtException, UnsupportedJwtException,
			MalformedJwtException, SignatureException, IllegalArgumentException, CertificateException, JwtCustomError {
		if (!getJwtClaims(jwt).isEmpty()) {
			return true;
		} else {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.bcbsfl.filter.security.IJWTHelper#getTokenFromRequest(javax.servlet.http.HttpServletRequest)
	 */
	public String getTokenFromRequest(HttpServletRequest req) {
		LOGGER.debug("Entered Method GetTokenFromRequest");

		String jwtValue = null;

		//*****************Comment out Logging of all header values****************
//		Enumeration<String> headerNames = req.getHeaderNames();
//		LOGGER.fine("List Header Values: ");
//		while (headerNames.hasMoreElements()) {
//			String currentName = headerNames.nextElement();
//			LOGGER.fine("{} : {}", currentName, req.getHeader(currentName));
//		}

		LOGGER.debug("Get the Authorization Header");
		String authHeaderVal = req.getHeader(AUTHORIZATION_HEADER);
		//If header Value is null and Debugging print out all headers
		if(authHeaderVal == null && LOGGER.isDebugEnabled()){
			Enumeration<String> headerNames = req.getHeaderNames();
			while(headerNames.hasMoreElements()){
				String headerName = headerNames.nextElement();
				
				LOGGER.debug(headerName + "[" + req.getHeader(headerName) + "]");
			}
		}
		if (authHeaderVal != null) {
			LOGGER.debug("Authorization Header: "+ authHeaderVal);
			jwtValue = authHeaderVal.replaceAll(AUTH_HEADER_PREFIX, "");
		}

		//*************************************
		//REMOVE Pulling of JWT from Cookie per Request from Alex Carlson
		//*************************************
		//JWT value was not in the Authorization Header.  Looking in the Cookies
//		if (jwtValue == null) {
//			if (req.getCookies() == null) {
//				return "";
//			}
//
//			for (Cookie cookie : (req.getCookies())) {
//				LOGGER.debug("Cookies: "+cookie.getName()+"["+cookie.getValue()+"]");
//				if (cookie.getName().equalsIgnoreCase(JWT_COOKIE_NAME)) {
//					jwtValue = cookie.getValue();
//				}
//			}
//		}
		
		LOGGER.debug("JWT: " + jwtValue);
		return jwtValue;
	}

	/* (non-Javadoc)
	 * @see com.bcbsfl.filter.security.IJWTHelper#verifyRole(java.util.List, java.util.List)
	 */
	public boolean verifyRole(List<String> allowedRoles, List<String> userRoles) {
		if(allowedRoles == null || allowedRoles.contains(ALL)){
			LOGGER.debug("ALL roles are allowed.  Returning True");
			return true;
		}
		for(String currentAllowedRole : allowedRoles){
			for(String currentUserRole : userRoles){
				if(currentAllowedRole.equalsIgnoreCase(currentUserRole)){
					return true;
				}
			}
		}
		
		return false;
	}

	public boolean verifyAudience(String audience, String header, String issuer) {
		if(issuer.toUpperCase().contains("PROD")){
			LOGGER.debug("Verifying Audience against Host Header");
			if(audience.equalsIgnoreCase(header)){
				LOGGER.debug("Audience Match");
				return true;
			}else{
				LOGGER.debug("Audience is not allowed");
				LOGGER.warn("Header Value: " + header + " Does not match JWT Audience: " + audience);
				return false;
			}
		}
		
		return true;
	}
	
	

}
