
package com.bcbsfl.filter.security.filters;

import static com.bcbsfl.filter.security.helpers.AuthHelper.determineResponseType;
import static com.bcbsfl.filter.security.helpers.AuthHelper.getSecurityRole;
import static com.bcbsfl.filter.security.helpers.AuthHelper.getUnauthorizedResponse;
import static com.bcbsfl.filter.security.helpers.AuthHelper.isDataAuthorized;
import static com.bcbsfl.filter.security.helpers.AuthHelper.isInputDataAuthorized;
import static com.bcbsfl.filter.security.helpers.AuthHelper.secRole;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.config.FBCommonConfiguration;
import com.bcbsfl.filter.security.helpers.AuthHelper.ResponseType;
import com.bcbsfl.filter.security.helpers.JWTHelper;
import com.bcbsfl.mail.EmailSender;
import com.google.inject.Inject;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

import io.jsonwebtoken.Claims;


public class ResponseFilter implements ContainerResponseFilter {

	private static final Logger logger = LoggerFactory.getLogger(ResponseFilter.class);
	
	@Context
	private HttpServletRequest httpRequest;
	private FBCommonConfiguration configuration;
	private boolean enableDataEntitlementValidation = true; 
	private boolean sendEmailInsteadOfReturning403 = false;
	private EmailSender emailSender = null;
	
	@Inject
	public ResponseFilter(FBCommonConfiguration configuration) {
		this.setConfiguration(configuration);
		this.enableDataEntitlementValidation = configuration.enableDataEntitlementValidation();
		this.sendEmailInsteadOfReturning403 = configuration.sendEmailInsteadOfReturning403();
		this.emailSender = new EmailSender();
	}
		
	@Override
	public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
		if(response.getStatus() >= 400) {
			return response;
		}

		if(this.enableDataEntitlementValidation) {
			response = filterAuthorizedDataOnly(request, response);;
		}
		
		return response;
	}

	/**
	 * @param response
	 * @return Performs Data Entitlement Check for every resource being returned
	 *         based on SecRole property in the resource and available entitlements
	 *         from JWT
	 */
	@SuppressWarnings("unchecked")
	private ContainerResponse filterAuthorizedDataOnly(ContainerRequest request, ContainerResponse response) {
		
		logger.debug("In Response Filter");
		
		Claims claim = (Claims) httpRequest.getAttribute("jwt");
		if (null == claim) { // JWT is disabled{
			return response;
		}

		ResponseType type = determineResponseType(response);
		/** 
		 * The special request headers take precedence over the JWT info. Sometimes the JWT will contain the etd
		 * service account. If so, the caller could choose to use special request headers to give the info for the 
		 * actual logged-in user.
		 */
		String loggedInUserId = JWTHelper.getLoggedInUserId();
		String userId = JWTHelper.getUserId();

		List<String> userRoles = JWTHelper.getDataEntitlementsList();
			
		List<String> resourcesWithIssues = new ArrayList<String>();
		switch (type) {

		case WORKFLOW: { // workflow/{workflowId} returns this
			String securityRole = (String) ((Workflow) response.getEntity()).getInput().get(secRole);
			if(null != securityRole) {  // These would be legacy workflows that were allowed to have a secRole of null
				if (!isDataAuthorized(securityRole, userRoles)) {
					addResourceWithIssue(resourcesWithIssues, ((Workflow)response.getEntity()).getWorkflowId(), securityRole);
					handleDataAuthError(userId, loggedInUserId, getFullRequestPath(request), "Workflow", resourcesWithIssues, userRoles, request, response);
				}
			}
			break;
		}

		case TASK: { // tasks/{taskId} and tasks/in_progress/{workflowId}/{taskRefName} return this
			String securityRole = (String) ((Task) response.getEntity()).getInputData().get(secRole);
			if(null != securityRole) {  // These would be legacy tasks that were allowed to have a secRole of null
				if (!isDataAuthorized(securityRole, userRoles)) {
					addResourceWithIssue(resourcesWithIssues, ((Task)response.getEntity()).getTaskId(), securityRole);
					handleDataAuthError(userId, loggedInUserId, getFullRequestPath(request), "Task", resourcesWithIssues, userRoles, request, response);
				}
			}
			break;
		}

		case LIST_WORKFLOW: { // workflow/{workflowId}/correlated/{corrId} returns this
			List<Workflow> lstWorkflows = (List<Workflow>) response.getEntity();
			Iterator<Workflow> itr = lstWorkflows.iterator();
			String secRole = null;
			while (itr.hasNext()) {
				Workflow workflow = itr.next();
				secRole = (String) workflow.getInput().get(secRole);
				if(null != secRole) {  // These would be legacy workflows that were allowed to have a secRole of null
					if (!isDataAuthorized(secRole, userRoles)) {
						addResourceWithIssue(resourcesWithIssues, workflow.getWorkflowId(), secRole);
						if(!this.sendEmailInsteadOfReturning403) {
							itr.remove();
						}
					}
				}
			}
			if(resourcesWithIssues.size() > 0) {
				handleDataAuthError(userId, loggedInUserId, getFullRequestPath(request), "Workflow", resourcesWithIssues, userRoles, request, response);
			}
			break;
		}

		case LIST_TASK: {  // batch poll returns this, tasks/in_progress/{taskType} returns this
			List<Task> lstTasks = (List<Task>) response.getEntity();
			Iterator<Task> itr = lstTasks.iterator();
			String secRole = null;
			while (itr.hasNext()) {
				Task task = itr.next();
				secRole = (String) task.getInputData().get(secRole);
				if(null != secRole) {  // These would be legacy tasks that were allowed to have a secRole of null
					if (!isDataAuthorized(secRole, userRoles)) {
						addResourceWithIssue(resourcesWithIssues, task.getTaskId(), secRole);
						if(!this.sendEmailInsteadOfReturning403) {
							itr.remove();
						}
					}
				}
			}
			if(resourcesWithIssues.size() > 0) {
				handleDataAuthError(userId, loggedInUserId, getFullRequestPath(request), "Task", resourcesWithIssues, userRoles, request, response);
			}
			break;
		}

		case WORKFLOW_SUMMARY: { // workflow/search returns this
			SearchResult<WorkflowSummary> lstSearchResults = (SearchResult<WorkflowSummary>) response.getEntity();
			Iterator<WorkflowSummary> itr = lstSearchResults.getResults().iterator();
			while (itr.hasNext()) {
				WorkflowSummary workflowSummary = itr.next();
				try {
					String secRole = getSecurityRole(workflowSummary.getInput()); 
					if(null != secRole) {  // These would be legacy workflows that were allowed to have a secRole of null
						if (!isInputDataAuthorized(workflowSummary.getInput(), userRoles)) {
							addResourceWithIssue(resourcesWithIssues, workflowSummary.getWorkflowId(), secRole);
//							if(!this.sendEmailInsteadOfReturning403) {
								itr.remove();
//							}
						}
					}
				} catch(Throwable t) {
					logger.error("The following exception applies to workflow id '" + workflowSummary.getWorkflowId() +
						"' (workflow type: '" + workflowSummary.getWorkflowType() + "')");
					t.printStackTrace();
					itr.remove();
				}
			}
			if(resourcesWithIssues.size() > 0) {
//				handleDataAuthError(userId, loggedInUserId, getFullRequestPath(request), "Workflow", resourcesWithIssues, userRoles, request, response);
			}
			break;
		}

		case TASK_SUMMARY: { // tasks/search returns this
			SearchResult<TaskSummary> lstSearchResults = (SearchResult<TaskSummary>) response.getEntity();
			Iterator<TaskSummary> itr = lstSearchResults.getResults().iterator();
			while (itr.hasNext()) {
				TaskSummary taskSummary = itr.next();
				try {
					String secRole = getSecurityRole(taskSummary.getInput()); 
					if(null != secRole) {  // These would be legacy tasks that were allowed to have a secRole of null
						if (!isInputDataAuthorized(taskSummary.getInput(), userRoles)) {
							addResourceWithIssue(resourcesWithIssues, taskSummary.getTaskId(), secRole); 
//							if(!this.sendEmailInsteadOfReturning403) {
								itr.remove();
//							}
						}
					}
				} catch(Throwable t) {
					logger.error("The following exception applies to task id '" + taskSummary.getTaskId() +
						"' (task def name: '" + taskSummary.getTaskDefName() + 
						"', task type: '" + taskSummary.getTaskType() + "')");
					throw t;
				}
			}
			if(resourcesWithIssues.size() > 0) {
//				handleDataAuthError(userId, getFullRequestPath(request), "Task", resourcesWithIssues, userRoles, request, response);
			}
			break;
		}

		default:
			logger.debug("Entitlement Check not applicable");
		}	
		return response;
	}
	
	private String getFullRequestPath(ContainerRequest request) {
		String requestPath = request.getPath();
		String queryParameters = request.getRequestUri().getQuery();
		if(StringUtils.isNotEmpty(queryParameters)) {
			requestPath += "?" + queryParameters;
		}
		return requestPath;
	}
	
	private void addResourceWithIssue(List<String> resourcesWithIssues, String resourceId, String securityRole) {
		resourcesWithIssues.add(resourceId + "|" + securityRole);
	}
	
	private void handleDataAuthError(String userId, String loggedInUserId, String requestPath, String resourceType, List<String> resourcesWithIssues, List<String> userRoles, ContainerRequest request, ContainerResponse response) {
		final StringBuffer securityRoles = new StringBuffer(); 
		if(resourcesWithIssues != null && resourcesWithIssues.size() > 0) {
			resourcesWithIssues.forEach(resourceWithIssue -> {
				String[] components = resourceWithIssue.split("\\|");
				if(components.length > 1) {
					if(securityRoles.length() > 0) {
						securityRoles.append(",");
					}
					securityRoles.append(components[1]);
					logDataAuthError(userId, requestPath, resourceType, components[0], components[1], userRoles);
				}
			});
		}
		this.emailSender.sendDataEntitlementErrorEmail(userId, loggedInUserId, requestPath, resourceType, resourcesWithIssues, userRoles); 
		if(!this.sendEmailInsteadOfReturning403) {
			getUnauthorizedResponse(request, response, securityRoles.toString());
		}
	}
	
	private void logDataAuthError(String userId, String requestPath, String resourceType, String resourceId, String securityRole, List<String> userRoles) {
		logger.error("User '" + userId + "' does not have the required data entitlement (" + securityRole + 
			") to access the " + resourceType + " with " + resourceType + " Id '" + resourceId + "' for the '" + requestPath + "' request. Their data entitlements are: " +
			userRoles.toString().toLowerCase() + ". Note that we will be " + 
			"sending an email about this issue" +
			(this.sendEmailInsteadOfReturning403 ? "." : " as well as returning to the caller a 403 response code."));
	}
	
	public FBCommonConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(FBCommonConfiguration configuration) {
		this.configuration = configuration;
	}

};