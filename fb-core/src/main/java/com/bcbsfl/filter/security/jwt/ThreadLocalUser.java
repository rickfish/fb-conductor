package com.bcbsfl.filter.security.jwt;

/**
 * Stores the incoming JWT token in a ThreadLocal so all components on the thread can access it. 
 */
public class ThreadLocalUser {
	private static ThreadLocal<String> threadLocalJwt = new ThreadLocal<String>();
	private static ThreadLocal<String> threadLocalJwtUserId = new ThreadLocal<String>();
	private static ThreadLocal<String> threadLocalLoggedInUserId = new ThreadLocal<String>();
	private static ThreadLocal<String[]> threadLocalDataEntitlements = new ThreadLocal<String[]>();
	private static ThreadLocal<String[]> threadLocalValidSecRoles = new ThreadLocal<String[]>();
	/**
	 * Set the JWT into the ThreadLocal
	 * @param jwt
	 */
	public static void setJwt(String jwt) {
		threadLocalJwt.set(jwt);
	}
	/**
	 * Get the JWT from the ThreadLocal
	 * @return
	 */
	public static String getJwt() {
		return threadLocalJwt.get();
	}
	/**
	 * Get the logged-in user's data entitlements from the ThreadLocal
	 * @return
	 */
	public static String[] getDataEntitlements() {
		return threadLocalDataEntitlements.get();
	}
	/**
	 * Set the logged-in user's data entitlements into the ThreadLocal
	 * @param dataEntitlements
	 */
	public static void setDataEntitlements(String[] dataEntitlements) {
		threadLocalDataEntitlements.set(dataEntitlements);
	}
	/**
	 * Get the user's valid security roles based on their data entitlements
	 * @return
	 */
	public static String[] getValidSecRoles() {
		return threadLocalValidSecRoles.get();
	}
	/**
	 * Set the user's valid security roles based on their data entitlements
	 * @param dataEntitlements
	 */
	public static void setValidSecRoles(String[] validSecRoles) {
		threadLocalValidSecRoles.set(validSecRoles);
	}
	/**
	 * Set the JWT userId into the ThreadLocal
	 * @param userId
	 */
	public static void setJwtUserId(String userId) {
		threadLocalJwtUserId.set(userId);
	}
	/**
	 * Get the JWT userId from the ThreadLocal
	 * @return
	 */
	public static String getJwtUserId() {
		return threadLocalJwtUserId.get();
	}
	/**
	 * Set the logged-in userId into the ThreadLocal
	 * @param userId
	 */
	public static void setLoggedInUserId(String userId) {
		threadLocalLoggedInUserId.set(userId);
	}
	/**
	 * Get the logged-in userId from the ThreadLocal
	 * @return
	 */
	public static String getLoggedInUserId() {
		return threadLocalLoggedInUserId.get();
	}
	/**
	 * Return a properly formatted JWT Authorization header
	 * @return
	 */
	public static String getAuthorizationHeader() {
		String jwt = getJwt();
		return (jwt == null ? null : "Bearer " + jwt);
	}
}
