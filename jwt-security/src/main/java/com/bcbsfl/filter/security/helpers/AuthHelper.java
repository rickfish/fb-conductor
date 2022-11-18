package com.bcbsfl.filter.security.helpers;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;

public class AuthHelper {
	static final JWTLogger LOGGER = JWTLogger.getInstance();
		
	private static Map<String, String> entitlementsMap = null;
	public static String roles = "roles";
	public static String sub = "sub";
	public static String app = "app";
	public static String iss = "iss";
	public static String secRole = "secRole";

	static {
		entitlementsMap = new HashMap<String, String>();
		entitlementsMap.put("FEP", "fep");
		entitlementsMap.put("SAO", "state");
		entitlementsMap.put("EMP", "employee");
		entitlementsMap.put("GEN", "general");
		entitlementsMap.put("BLU", "bluecard");
		entitlementsMap.put("BCH", "bluecard");
		entitlementsMap.put("SEN", "sensitive");
	}

	public enum ResponseType {
		WORKFLOW, TASK, LIST_WORKFLOW, LIST_TASK, WORKFLOW_SUMMARY, TASK_SUMMARY, DEFAULT
	}

	private static final ObjectMapper jsonMapper = new ObjectMapper();

	public static ResponseType determineResponseType(ContainerResponse response) {

		Object type = response.getEntityType();

		if (null == type || response.getStatus() != 200)
			return ResponseType.DEFAULT;

		if (type.equals(Workflow.class))
			return ResponseType.WORKFLOW;

		/* @Path("/in_progress/{workflowId}/{taskRefName}")
		 @Path("/{taskId}") */
		else if (null != type && type.equals(Task.class))
			return ResponseType.TASK;

		// @Path("/{name}/correlated/{correlationId}")
		else if ((type instanceof ParameterizedType)
				&& ((ParameterizedType) type).getRawType().equals(java.util.List.class)
				&& (((ParameterizedType) type).getActualTypeArguments()[0]).equals(Workflow.class))
			return ResponseType.LIST_WORKFLOW;

		// @Path("/in_progress/{tasktype}")
		else if ((type instanceof ParameterizedType)
				&& ((ParameterizedType) type).getRawType().equals(java.util.List.class)
				&& (((ParameterizedType) type).getActualTypeArguments()[0]).equals(Task.class))
			return ResponseType.LIST_TASK;

		// @Path("/search")
		else if ((type instanceof ParameterizedType)
				&& ((ParameterizedType) type).getRawType().equals(com.netflix.conductor.common.run.SearchResult.class)
				&& (((ParameterizedType) type).getActualTypeArguments()[0]).equals(WorkflowSummary.class))
			return ResponseType.WORKFLOW_SUMMARY;

		// @Path("/search")
		else if ((type instanceof ParameterizedType)
				&& ((ParameterizedType) type).getRawType().equals(com.netflix.conductor.common.run.SearchResult.class)
				&& (((ParameterizedType) type).getActualTypeArguments()[0]).equals(TaskSummary.class))
			return ResponseType.TASK_SUMMARY;

		return ResponseType.DEFAULT;

	}

	public static ContainerResponse getUnauthorizedResponse(ContainerRequest request, ContainerResponse response, String securityRoles) {
		response.setStatus(401);
		HashMap<String, String> entityMap = new LinkedHashMap<>();
		entityMap.put("code", "NOT AUTHORIZED");
		entityMap.put("message", "The requested resource is not authorized for available entitlements");
		entityMap.put("RequiredEntitlement(s)", securityRoles);
		response.setEntity(getAppropriateErrorEntity(request, entityMap));
		return response;
	}
	
	public static Object getAppropriateErrorEntity(ContainerRequest request, Map<String, String> entityMap) {
		List<String> headers = request.getRequestHeader("accept");
		if(headers != null && headers.contains("application/json")) {
			return entityMap;
		} else {
			try {
				return jsonMapper.writeValueAsString(entityMap);
			} catch(Exception e) {
				return entityMap.toString();
			} 
		}
	}

	/**
	 * Method to check if the user is having appropriate entitlements
	 * 
	 * @param dataEntitlement
	 * @param userEntitlements
	 * @return
	 */
	public static boolean isDataAuthorized(String dataEntitlement, List<String> userEntitlements) {
		if (null == userEntitlements) {
			return false;
		}
		String val = entitlementsMap.get((dataEntitlement == null ? null : dataEntitlement.toUpperCase()));
		return val != null ? userEntitlements.toString().toLowerCase().contains(val) : false;
	}

	/**
	 * Get the security roles associated with a data entitlement string
	 * 
	 * @param dataEntitlement
	 * @return
	 */
	public static List<String> getSecRolesForDataEntitlement(String dataEntitlement) {
		List<String> secRoles = new ArrayList<String>();
		if (null != dataEntitlement) {
			for(Map.Entry<String, String> entry : entitlementsMap.entrySet()) {
				if(dataEntitlement.toLowerCase().contains(entry.getValue())) {
					if(!secRoles.contains(entry.getKey())) {
						secRoles.add(entry.getKey());
					}
				}
			}
		}
		return secRoles;
	}

	/**
	 * Method to check if the user is having appropriate entitlements
	 * 
	 * @param inputData
	 * @param userEntitlements
	 * @return
	 */
	public static boolean isInputDataAuthorized(String inputData, List<String> userEntitlements) {

		String role = getSecurityRole(inputData);
		return role == null ? false : isDataAuthorized(role, userEntitlements);
	}

	/**
	 * Method to get the security role from input data of a workflow or task
	 * 
	 * @param inputData
	 * @return
	 */
	public static String getSecurityRole(String inputData) {

		String role = null;
		int secRoleIndex = inputData.indexOf(secRole);
		if (secRoleIndex != -1) {
			if(inputData.length() > (secRoleIndex + 11)) {
				role = inputData.substring(secRoleIndex + 8, secRoleIndex + 11);
			} else {
				throw new RuntimeException("In isInputDataAuthorized, inputData: " + inputData + ", secRole not long enough to do a check on role.");
			}
		}
		return role;
	}
	
	/**
	 * Check if the specified secRole is one of the valid ones
	 * @param secRole
	 * @return
	 */
	public static boolean isValidSecRole(String secRole) {
		return entitlementsMap.keySet().contains(secRole);
	}
}
