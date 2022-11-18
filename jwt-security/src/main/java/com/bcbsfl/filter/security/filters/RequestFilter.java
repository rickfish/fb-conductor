package com.bcbsfl.filter.security.filters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.util.IOUtils;
import com.bcbsfl.cache.AuthCache;
import com.bcbsfl.config.FBCommonConfiguration;
import com.bcbsfl.filter.security.config.Configurations;
import com.bcbsfl.filter.security.config.Endpoint;
import com.bcbsfl.filter.security.config.Endpoint.HttpMethodType;
import com.bcbsfl.filter.security.config.EndpointConfigs;
import com.bcbsfl.filter.security.helpers.AuthHelper;
import com.bcbsfl.filter.security.helpers.JWTHelper;
import com.bcbsfl.filter.security.jackson.JwtObjectMapper;
import com.bcbsfl.mail.EmailSender;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fb.etd.cache.user.conductor.ConductorUser;
import com.fb.etd.cache.user.conductor.ConductorUserCache;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

@Provider
public class RequestFilter implements ContainerRequestFilter {
	private static final Logger logger = LoggerFactory.getLogger(RequestFilter.class);
	
	static public final String RESOURCENAME_IN_ARRAY = "array";
	static public final String ARRAY_IS_AT_ROOT = "ROOT";
	static public final String SECROLE_ATTRIBUTE = "secRole";

	@Context
	private HttpServletRequest httpRequest;

	EndpointConfigs configs = null;

	private final TaskService taskService;
	private final WorkflowService workflowService;
	private FBCommonConfiguration configuration;

	private boolean enableAuthorizationChecking = true; 
	private boolean sendEmailInsteadOfReturning403 = false;
	private EmailSender emailSender = null;
    private AuthCache authCache = new AuthCache();
	private ConductorUserCache userCache = new ConductorUserCache();

	@Inject
	public RequestFilter(FBCommonConfiguration configuration, TaskService taskService, WorkflowService workflowService) {
		this.setConfiguration(configuration);
		this.taskService = taskService;
		this.workflowService = workflowService;
		this.enableAuthorizationChecking = configuration.enableAuthorizationChecking();
		this.sendEmailInsteadOfReturning403 = configuration.sendEmailInsteadOfReturning403();
		this.emailSender = new EmailSender();
		configs = loadEndpointConfigs();
	}

	@Override
	public ContainerRequest filter(ContainerRequest request) {
		/*
		 * We only need to check if restricted if the request contains a user id
		 */
		if(enableAuthorizationChecking) {
			try {
				if(JWTHelper.getUserId() != null) {
					/*
					 * userId is the id of the user making the request (the one in the JWT). This could be a service account or a RACF id.
					 */
					String userId = JWTHelper.getJwtUserId();
					/*
					 * loggedInUserId is the id of the logged-in user of the application making the request. This will only be
					 * available if it is a UI application that knows it has to pass the RACF to Conductor.
					 */
					String loggedInUserId = JWTHelper.getLoggedInUserId();
					blockRestrictedRequests(userId, loggedInUserId, request);
				}
			} catch(Exception e) {
				/*
				 * These situations are filling up the logs. Somehow many users are
				 * executing this endpoint, not sure if it is conductor itself doing it or what,
				 * but this happens a lot.
				 */
				if(!"admin/config".equals(request.getPath())) {
					e.printStackTrace();
				}
				throw e;
			}
		}
		return request;
	}

	/**
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param request
	 *            Validate the Request URI and throws 403, if restricted
	 */
	private void blockRestrictedRequests(String userId, String loggedInUserId, ContainerRequest request) {
		/*
		 * See restrictedEndpoints.json to understand what is in the configs.getEndpoints() array
		 */
		if (!configs.getEnabled())
			return;

		Object errorInfo = null;
		try {
			/*
			 * For each endpoint, the method must match the current request's method and the request path must match
			 * a pattern in one of the endpoint definitions, otherwise it isn't blocked. 
			 */
			ResponseBuilder builder = null;
			for (Endpoint endpoint : configs.getEndpoints()) {
				if(endpoint.getMethod().contains(HttpMethodType.valueOf(request.getMethod()))) {
					Pattern pattern = Pattern.compile(endpoint.getEndpoint());
					Matcher matcher = pattern.matcher(request.getPath());
					if (matcher.find()) {
						errorInfo = performEndpointAuth(userId, loggedInUserId, request, endpoint);
						if(errorInfo != null) {
							builder = Response.status(Response.Status.FORBIDDEN).entity(errorInfo);
						} else {
							errorInfo = performEndpointSecRoleValidation(userId, loggedInUserId, request, endpoint);
							if(errorInfo != null) {
								builder = Response.status(Response.Status.BAD_REQUEST).entity(errorInfo);
							}
						}
						break;
					}
				}
			}
			if(builder != null && errorInfo != null) {
				logger.error("*** Error in request: " + errorInfo.toString());
				throw new WebApplicationException(builder.build());
			}
		} catch(WebApplicationException e) {
			logger.info("WebApplication Exception for user '" + userId + "', path: " + request.getPath() + (errorInfo == null ? "" : ", error: " + errorInfo));
			throw e;
		} catch(RequestFilterException e) {
			ResponseBuilder builder = null;
			builder = Response.status(Response.Status.FORBIDDEN).entity(e.getAppropriateErrorInfo(request));
			logger.info("RequestFilterException for user '" + userId + "', path: " + request.getPath() + ", error: " + e.getErrorInfo());
			throw new WebApplicationException(builder.build());
		} catch(Exception e) {
			ResponseBuilder builder = null;
			builder = Response.status(Response.Status.FORBIDDEN).entity(getErrorInfo(e));
			e.printStackTrace();
			throw new WebApplicationException(builder.build());
		}
	}

	/**
	 * For an endpoint, validate that the user is authorized to call it
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param request the request
	 * @param endpoint the endpoint that the user is trying to call
	 * @return an ErrorInfo object if there is an error
	 * @throws Exception
	 */
	private Object performEndpointAuth(String userId, String loggedInUserId, ContainerRequest request, Endpoint endpoint) throws Exception {
		Object errorInfo = null;
		switch(endpoint.getEnvProtection()) {
		case RESTRICTED: 	// RESTRICTED means nobody can execute this endpoint
			errorInfo = getUnauthorizedErrorInfo(request, "RESTRICTED URI");
			break;
		case SUPERGROUPS: 	// SUPERGROUPS means only members of the supergroups stewardship ids are authorized
			try {
				ConductorUser user = this.userCache.get(userId);
				if(user == null || !user.hasStewardshipForAnyEnv(this.configs.getSuperGroups())) {
					errorInfo = getUnauthorizedErrorInfo(request, "RESTRICTED URI");
				}
			} catch(Exception e) {
				logger.error("While calling the userCache.get() to validate SUPERGROUPS auth, got this exception: " + e.getMessage());
				errorInfo = getUnauthorizedErrorInfo(request, "ERROR CHECKING AUTHORIZATION");
			}
			break; 
		case AUTHORIZED:	// AUTHORIZED means the endpoint can be hit only by users that are authorized
			validateAuthorization(userId, loggedInUserId, endpoint, request);
			break;
		default:
			break;
		}
		return errorInfo;
	}
	

	/**
	 * For an endpoint, validate that there is a secRole attribute in the proper place of the request body
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param request the request
	 * @param endpoint the endpoint that the user is trying to call
	 * @return an ErrorInfo object if there is an error
	 * @exception
	 */
	private Object performEndpointSecRoleValidation(String userId, String loggedInUserId, ContainerRequest request, Endpoint endpoint) throws Exception {
		Object errorInfo = null;

		byte[] inputStreamBytes = null;
		ByteArrayInputStream requestBody = null;
		JsonNode requestBodyJson = null;
		
		if(endpoint == null || null == endpoint.getSecRoleLocation() || request == null) {
			return null;
		}
		
		switch(endpoint.getSecRoleLocation()) {
		case INPUT_OF_ALL_TASKS:
		case INPUT_OF_ALL_WORKFLOWS_TASKS:
		case REQUEST_BODY:
		case REQUEST_BODY_INPUT:
			/*
			 * Save the body's input stream because once it is read it cannot be read again.
			 */
			inputStreamBytes = getRequestInputStreamBytes(request);
			requestBody = new ByteArrayInputStream(inputStreamBytes);
			ObjectMapper mapper = new ObjectMapper();
			requestBodyJson = mapper.readTree(requestBody);
			if(requestBodyJson == null) {
				errorInfo = this.getMissingSecRoleErrorInfo(userId, request, endpoint, "a request that required a " + SECROLE_ATTRIBUTE + " in the request body had no request body");
			} else {
				switch(endpoint.getSecRoleLocation()) {
				case INPUT_OF_ALL_TASKS: 			// secRole attribute needs to be in the input data of all tasks in a workflow
					errorInfo = checkAllSimpleTasksForSecRole(userId, loggedInUserId, requestBodyJson, endpoint, request);
					break;
				case INPUT_OF_ALL_WORKFLOWS_TASKS: 	// secRole attribute needs to be in the input data of all tasks in all workflows in an array of workflows
					if(requestBodyJson.isArray()) {
						for(Iterator<JsonNode> iter = requestBodyJson.iterator(); iter.hasNext();) {
							errorInfo = checkAllSimpleTasksForSecRole(userId, loggedInUserId, iter.next(), endpoint, request);
							if(errorInfo != null) {
								break;
							}
						}
					} else {
						errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, "the request body is supposed to contain an array but it doesn't so we cannnot check for the existence of the " + SECROLE_ATTRIBUTE + " attribute");
					}
					break;
				case REQUEST_BODY:					// secRole attribute needs to be at the root of the request body json
					JsonNode thenode = requestBodyJson.get(SECROLE_ATTRIBUTE);
					if(null == thenode || thenode instanceof NullNode) {
						errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, SECROLE_ATTRIBUTE + " attribute not found at the root of the request body for '" + getResourceNames(userId, endpoint, request) + "'");
					} else {
						String secRoleAttr = thenode.asText();
						if(!AuthHelper.isValidSecRole(secRoleAttr)) {
							errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, SECROLE_ATTRIBUTE + " attribute is not a valid " + SECROLE_ATTRIBUTE + " for '" + getResourceNames(userId, endpoint, request) + "'. Value is '" + secRoleAttr + "'");
						}
					}
					break;
				case REQUEST_BODY_INPUT:			// secRole attribute needs to be in the input data at the root of the request body json
					JsonNode node = requestBodyJson.get("input");
					if(node == null || null == node.get(SECROLE_ATTRIBUTE) || node.get(SECROLE_ATTRIBUTE) instanceof NullNode) {
						errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, SECROLE_ATTRIBUTE + " attribute not found in the input segment of the request body for '" + getResourceNames(userId, endpoint, request) + "'");
					} else {
						String secRoleAttr = node.get(SECROLE_ATTRIBUTE).asText();
						if(!AuthHelper.isValidSecRole(secRoleAttr)) {
							errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, SECROLE_ATTRIBUTE + " attribute is not a valid " + SECROLE_ATTRIBUTE + "for '" + getResourceNames(userId, endpoint, request) + "'. Value is '\" + secRoleAttr + \"'");
						}
					}
					break;
				default:
					break;
				}
			}
			break;
		default:
			break;
		}

		if(requestBody != null) {
			/*
			 * Reset the input stream so it can be processed by the target.
			 */
			request.setEntityInputStream(new ByteArrayInputStream(inputStreamBytes));
		}
		
		return errorInfo;
	}
	
	/**
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param requestBodyJson
	 * @param request
	 * @return
	 */
	private Object checkAllSimpleTasksForSecRole(String userId, String loggedInUserId, JsonNode requestBodyJson, Endpoint endpoint, ContainerRequest request) {
		Object errorInfo = null;
		JsonNode arr = requestBodyJson.get("tasks");
		if(arr != null && arr.isArray()) {
			JsonNode element = null;
			for(Iterator<JsonNode> iter = arr.iterator(); iter.hasNext();) {
				element = iter.next();
				JsonNode taskType = element.get("type");
				if(taskType != null && "SIMPLE".equalsIgnoreCase(taskType.asText())) {
					JsonNode input = element.get("inputParameters");
					if(input == null || null == input.get(SECROLE_ATTRIBUTE) || input.get(SECROLE_ATTRIBUTE) instanceof NullNode) {
						errorInfo = getMissingSecRoleErrorInfo(userId, request, endpoint, SECROLE_ATTRIBUTE + " attribute not found in the input segment of the '" + element.get("name") + "' task in the request body");
						break;
					}
				}
			}
		}
		return errorInfo;
	}
	
	private String getSecRoleErrorMsgPrefix(String userId, Endpoint endpoint, ContainerRequest request) {
		String errorMessagePrefix = null;
		String resourceName = null;
		if(endpoint.getResourceNameLoc() != null) {
			try {
				List<String> resourceNames = getResourceNames(userId, endpoint, request);
				resourceName = resourceNames == null || resourceNames.size() == 0 ? null : resourceNames.get(0);
			} catch(Exception e) {}
		}
		errorMessagePrefix = "[URI: " + request.getPath() + (resourceName == null ? "": ", Resource: " + resourceName) + "] ";
		return errorMessagePrefix;
	}

	/**
	 * Validate that the user executing the request is in the appropriate stewardship group for the request's resource
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param endpoint the Endpoint object that matched the request
	 * @param request the incoming request
	 * @throws Exception
	 */
	private void validateAuthorization(String userId, String loggedInUserId, Endpoint endpoint, ContainerRequest request) throws Exception {
		List<String> resourceNames = getResourceNames(userId, endpoint, request);
		/*
		 * We have one or more resource names. Get the authorization status for each one
		 */
		if(resourceNames != null) {
			for(String resourceName: resourceNames) {
				switch(endpoint.getResourceType()) {
				case WORKFLOW:
					authorizeResource(userId, loggedInUserId, resourceName, "workflow");
					break;
				case TASK:
					authorizeResource(userId, loggedInUserId, resourceName, "task");
					break;
				case WORKFLOWTASK:
					/*
					 * In the case of a WORKFLOWTASK, we are concerned about the stewardship id of the
					 * workflow, not the task itself. This accommodates shared tasks since the task
					 * stewardship is the owner of the shared task...we want the stewardship of the
					 * workflow running the shared task.
					 */
					authorizeResource(userId, loggedInUserId, resourceName, "workflow");
					break;
				default:
					break;
				}
			}
		}
	}

	private void authorizeResource(String userId, String loggedInUserId, String resourceName, String resourceType) throws Exception {
		try {
			String s = userId + AuthCache.REQUEST_STRING_DELIMITER 
				+ (resourceType.equals("workflow") ? AuthCache.WORKFLOW_RELATED_ACTION : AuthCache.TASK_RELATED_ACTION)
				+ AuthCache.REQUEST_STRING_DELIMITER + resourceName;
			/*
			 * The AuthCache will either get an authorization response from the cache or from the auth server. 
			 */
			String response = authCache.get(s);
			if(response == null) {
				throw new RequestFilterException("Could not get authorization information from the authentication server for the '" + resourceName + "' " + resourceType + " so defaulting to NOT AUTHORIZED");
			} else if(!"true".equalsIgnoreCase(response)) {
				logger.error("For user '" + userId + "' and the '" + resourceName + "' " + resourceType + ", we got this authorization error: " + response +
				". Note that we will be sending an email about this issue" +
				(this.sendEmailInsteadOfReturning403 ? "." : " as well as returning to the caller a 403 response code."));
				this.emailSender.sendAuthErrorEmail(userId, loggedInUserId, resourceName, resourceType, response);
				if(!this.sendEmailInsteadOfReturning403) {
					throw new RequestFilterException("For the '" + resourceName + "' " + resourceType + ", we got this authorization error: " + response);
				}
			}
		} catch(RequestFilterException e) {
			throw e;
		} catch(Exception e) {
			logger.error("For user '" + userId + "' and the '" + resourceName + "' " + resourceType + ", we got this authorization exception: " + e.getMessage() +
			". Note that we will be sending an email about this issue" +
			(this.sendEmailInsteadOfReturning403 ? "." : " as well as returning to the caller a 403 response code."));
			this.emailSender.sendAuthErrorEmail(userId, loggedInUserId, resourceName, resourceType, e.getMessage());
			if(!this.sendEmailInsteadOfReturning403) {
				throw new RequestFilterException("Could not get authorization info associated with the '" + resourceName + "' " + resourceType + " so we cannot authorize this request");
			}
		}
	}
	
	/**
	 * Find the names of the resources that are part of the request path or
	 * body so that we can pull the stewardship id out of them.
	 * @param userId the racf id of the user making the request
	 * @param endpoint the object that contains information about where the resource can be found in the request
	 * @param request the incoming request
	 * @return a list of resource names associated with the request
	 * @throws Exception
	 */
	private List<String> getResourceNames(String userId, Endpoint endpoint, ContainerRequest request)  throws Exception {
		List<String> resourceNames = new ArrayList<String>();
		Endpoint.ResourceNameLoc resourceNameLoc = endpoint.getResourceNameLoc();
		byte[] inputStreamBytes = null;
		/*
		 * The 'location' can either be 'path', 'body', or 'workflow_id_lookup'. If path, the resource name is in one of the segments of the
		 * path. If body, the resource name is either in the body's json at the root or there are multiple in an array
		 * where each one has the resource name at the root. If 'workflow_id_lookup', the workflow id is found in the 
		 * path and we need to do a lookup to find the workflow and get the workflow name.
		 */
		switch (endpoint.getResourceNameLoc().getLocation()) {
		case PATH: // resource name is in the path
			String resourceName = getPathSegmentString(resourceNameLoc.getPathSegment(), request);
			resourceNames.add(resourceName);
			break;
		case BODY: // resource name is in the body of the= request 
			/*
			 * Save the body's input stream because once it is read it cannot be read again.
			 */
			inputStreamBytes = getRequestInputStreamBytes(request);
			resourceNames = getResourceNamesFromBody(resourceNameLoc, new ByteArrayInputStream(inputStreamBytes));
			/*
			 * Reset the input stream so it can be processed by the target.
			 */
			request.setEntityInputStream(new ByteArrayInputStream(inputStreamBytes));
			break;
		case ARRAY_OF_IDS:
			switch(endpoint.getResourceType()) {
			case WORKFLOW:
				/*
				 * Save the body's input stream because once it is read it cannot be read again.
				 */
				inputStreamBytes = getRequestInputStreamBytes(request);
				resourceNames = getResourceNamesFromWorkflowIds(new ByteArrayInputStream(inputStreamBytes));
				/*
				 * Reset the input stream so it can be processed by the target.
				 */
				request.setEntityInputStream(new ByteArrayInputStream(inputStreamBytes));
				break;
			default:
				break;
			}
			break;
		case ID_LOOKUP: // The resource's id is in the path, do a lookup to find the resource
			switch(endpoint.getResourceType()) {
			case WORKFLOW:
				String workflowId = null;
				if(resourceNameLoc.getPathSegment() != null) {
					workflowId = getPathSegmentString(resourceNameLoc.getPathSegment(), request);				
				} else {
					/*
					 * Save the body's input stream because once it is read it cannot be read again.
					 */
					inputStreamBytes = getRequestInputStreamBytes(request);
					workflowId = getResourceIdFromBody(
						resourceNameLoc.getAttributeName(), new ByteArrayInputStream(inputStreamBytes));
					/*
					 * Reset the input stream so it can be processed by the target.
					 */
					request.setEntityInputStream(new ByteArrayInputStream(inputStreamBytes));
				}
				if(workflowId != null) {
					Workflow workflow = this.workflowService.getExecutionStatus(workflowId, false);
					if(workflow != null) {
						resourceNames.add(workflow.getWorkflowName());
					} else {
						throw new RequestFilterException("Could not find a Workflow associated with workflow id '" + workflowId + "' so we cannot get the stewardship id for this request");
					}
				}
				break;
			case TASK:
				/*
				 * When we are doing an ID_LOOKUP, we would never be concerned with the TASK ownerApp,
				 * just the ownerApp of the workflow that is running the task. This accomodates shared
				 * tasks...i.e. a workflow running a shared task would not be the same stewardship as the
				 * owner of that shared task...therefore this case should never happen if the configuration
				 * was done properly.
				 */
				break;
			case WORKFLOWTASK:
				String taskId = null;
				if(resourceNameLoc.getPathSegment() != null) {
					taskId = getPathSegmentString(resourceNameLoc.getPathSegment(), request);				
				} else {
					/*
					 * Save the body's input stream because once it is read it cannot be read again.
					 */
					inputStreamBytes = getRequestInputStreamBytes(request);
					taskId = getResourceIdFromBody(
						resourceNameLoc.getAttributeName(), new ByteArrayInputStream(inputStreamBytes));
					/*
					 * Reset the input stream so it can be processed by the target.
					 */
					request.setEntityInputStream(new ByteArrayInputStream(inputStreamBytes));
				}
				if(taskId != null) {
					Task task = this.taskService.getTask(taskId);
					if(task != null) {
						/*
						 * We only care about the stewardship of the workflow since the task could
						 * be a shared task in which case the group using the shared task only can 
						 * manipulate the shared task if it is in their workflow. Note that task.getWorkflowType()
						 * doesn't always work so we have to get the workflow first.
						 */
						if(task.getWorkflowType() != null) {
							resourceNames.add(task.getWorkflowType());
						} else {
							try {
								Workflow workflow = this.workflowService.getExecutionStatus(task.getWorkflowInstanceId(), false);
								if(workflow != null) {
									resourceNames.add(workflow.getWorkflowName());
								} else {
									throw new RequestFilterException("Could not find a Workflow associated with workflow id '" + task.getWorkflowInstanceId() + "' from task id '"+ taskId + "' so we cannot get the stewardship id for this request");
								}
							} catch(Exception e) {
								logger.info("Got an '" + e.getMessage() + "' exception while processing the '" + request.getPath() + "' url for user '" + userId + "'");
							}
						}
					} else {
						throw new RequestFilterException("Could not find a Task associated with task id '" + taskId + "' so we cannot get the stewardship id for this request");
					}
				}
				break;
			default:
				break;
			}
			break;
		}
		return resourceNames;
	}
	
	/**
	 * Given the number of the path segment, return the string in that position
	 * @param pathSegment the segment number
	 * @param request the incoming request
	 * @return the string
	 * @throws Exception
	 */
	private String getPathSegmentString(int pathSegment, ContainerRequest request) throws Exception {
		String path = request.getPath();
		String s = null;
		int currentPathSegment = 0;
		int startOfString = 0;
		int slashLoc = 0;
		/*
		 * The segments of the path are separated by slashes. Go thru the path until we find the resource name in the
		 * specified segment.
		 */
		while(slashLoc != -1) {
			if(currentPathSegment == pathSegment) {
				break;
			} else {
				slashLoc = path.indexOf("/", startOfString);
				if(slashLoc != -1) {
					s = path.substring(startOfString, slashLoc);
					startOfString = slashLoc + 1;
					currentPathSegment++;
				} else {
					s = path.substring(startOfString);
				}
			}
		}
		return s;
	}

	/**
	 * The resource name (or names) is in the request body. There can be one if the body is not an array or multiple if
	 * it is an array. Go through the request body and grab all the resource names
	 * @param resourceNameLoc the object that has information about where to find the resource name
	 * @param requestBody the body of the incoming request
	 * @return one or more resource names found in the request body
	 * @throws Exception
	 */
	private List<String> getResourceNamesFromBody(Endpoint.ResourceNameLoc resourceNameLoc, InputStream requestBody) throws Exception {
		List<String> resourceNames = new ArrayList<String>();
		ObjectMapper mapper = new ObjectMapper();
		JsonNode json = null;
		try {
			json = mapper.readTree(requestBody);
		} catch(Exception e) {
			System.err.println("Got an error trying to parse this JSON: ");
			System.err.println(IOUtils.toString(requestBody));
			throw e;
		}
		/*
		 * If there is an array name in the ResourceNameLoc object, we know we have to look thru the body as an array
		 */
		if(resourceNameLoc.getArrayName() != null) {
			JsonNode arr = json;
			/*
			 * The array can either be the body itself or it can be an array within the body
			 */
			if(!ARRAY_IS_AT_ROOT.equals(resourceNameLoc.getArrayName())) {
				arr = json.path(resourceNameLoc.getArrayName());
			}
			arr.forEach(element -> {
				JsonNode resource = element.path(resourceNameLoc.getAttributeName());
				if(resource != null) {
					resourceNames.add(resource.asText());
				}
			});
		} else {
			/*
			 * No array so just get it as an attribute of the body itself
			 */
			JsonNode resource = json.path(resourceNameLoc.getAttributeName());
			if(resource != null) {
				resourceNames.add(resource.asText());
			}
		}
		return resourceNames;
	}

	/**
	 * The array of workflow ids is in the request body. For each one, find the workflow name. Send back a list of unique
	 * workflow names (there could be duplicates).
	 * @param requestBody the body of the incoming request which contains an array of id strings
	 * @return one or more resource names associated with the ids in the request body
	 * @throws Exception
	 */
	private List<String> getResourceNamesFromWorkflowIds(InputStream requestBody) throws Exception {
		List<String> resourceNames = new ArrayList<String>();
		ObjectMapper mapper = new ObjectMapper();

		@SuppressWarnings("unchecked")
		ArrayList<String> idList = mapper.readValue(requestBody, ArrayList.class);
		idList.forEach(id -> {
			Workflow workflow = this.workflowService.getExecutionStatus(id, false);
			if(workflow != null && workflow.getWorkflowName() != null && !resourceNames.contains(workflow.getWorkflowName())) {
				resourceNames.add(workflow.getWorkflowName());
			}
		});
		return resourceNames;
	}

	/**
	 * The id of the resource is in the request body. Go through the request body and find it
	 * @param attributeName the name of the attribute containing the id
	 * @param requestBody the body of the incoming request
	 * @return the id found in the request body
	 * @throws Exception
	 */
	private String getResourceIdFromBody(String attributeName, InputStream requestBody) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode json = null;
		try {
			json = mapper.readTree(requestBody);
		} catch(Exception e) {
			System.err.println("Got an error trying to parse this JSON: ");
			System.err.println(IOUtils.toString(requestBody));
			throw e;
		}
		JsonNode id = json.path(attributeName);
		if(id != null) {
			return id.asText();
		}
		return null;
	}

	private Object getUnauthorizedErrorInfo(ContainerRequest request, String message) {
		return getUnauthorizedErrorInfo(request, message, null, null);
	}
	
	private Object getUnauthorizedErrorInfo(ContainerRequest request, String message, String addlInfoName, String addlInfoValue) {
		Map<String, String> entityMap = new LinkedHashMap<>();
		entityMap.put("code", "NOT AUTHORIZED");
		entityMap.put("message", message);
		if(addlInfoName != null) {
			entityMap.put(addlInfoName, addlInfoValue);
		}
		return AuthHelper.getAppropriateErrorEntity(request, entityMap);
	}
	
	private Object getMissingSecRoleErrorInfo(String userId, ContainerRequest request, Endpoint endpoint, String message) {
		Map<String, String> entityMap = new LinkedHashMap<>();
		entityMap.put("code", "BAD REQUEST");
		String errorMessagePrefix = getSecRoleErrorMsgPrefix(userId, endpoint, request);
		entityMap.put("message", errorMessagePrefix + message);
		return AuthHelper.getAppropriateErrorEntity(request, entityMap);
	}
	
	private byte[] getRequestInputStreamBytes(ContainerRequest request) throws Exception {
		ByteArrayOutputStream inputByteStream = new ByteArrayOutputStream();
		IOUtils.copy(request.getEntityInputStream(), inputByteStream);
		return inputByteStream.toByteArray();
	}

	private static EndpointConfigs loadEndpointConfigs() {
		EndpointConfigs endpointConfigs = new EndpointConfigs();

		try {
			endpointConfigs = JwtObjectMapper.getObjectMapper().readValue(
					Configurations.class.getResourceAsStream("/restrictedEndpoints.json"), EndpointConfigs.class);
		} catch (JsonParseException e) {

		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return endpointConfigs;
	}

	private Map<String, String> getErrorInfo(Exception e) {
		Map<String, String> errorInfo = new LinkedHashMap<String, String>();
		errorInfo.put("code", "NOT AUTHORIZED");
		errorInfo.put("message", e.getMessage());
		return errorInfo;
	}
	
	public FBCommonConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(FBCommonConfiguration configuration) {
		this.configuration = configuration;
	}

	private class RequestFilterException extends Exception {
		private static final long serialVersionUID = 1L;
		private Map<String, String> errorInfo = new LinkedHashMap<String, String>();
		@SuppressWarnings("unused")
		public RequestFilterException() {
			super();
		}
		@SuppressWarnings("unused")
		public RequestFilterException(String message, String addlInfoName, String addlInfoValue) {
			super();
			errorInfo.put("code", "NOT AUTHORIZED");
			errorInfo.put("message", message);
			if(addlInfoName != null) {
				errorInfo.put(addlInfoName, addlInfoValue);
			}
		}
		public RequestFilterException(String message) {
			super();
			errorInfo.put("code", "NOT AUTHORIZED");
			errorInfo.put("message", message);
		}
		public Map<String, String> getErrorInfo() {
			return errorInfo;
		}
		public Object getAppropriateErrorInfo(ContainerRequest request) {
			return AuthHelper.getAppropriateErrorEntity(request, this.errorInfo);
		}
		@SuppressWarnings("unused")
		public void setErrorInfo(Map<String, String> errorInfo) {
			this.errorInfo = errorInfo;
		}
	}
}

// URL, METHOD, MESSAGE
