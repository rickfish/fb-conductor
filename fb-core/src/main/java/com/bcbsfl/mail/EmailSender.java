package com.bcbsfl.mail;

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.filter.security.jwt.ThreadLocalUser;
import com.bcbsfl.util.RuntimeEnvironment;

/**
 * Sends standard emails to Conductor interested parties
 */
public class EmailSender extends BasicEmailSender {
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    /**
     * Maximum number of stacktrace lines printed for the first exception in the nested list of exceptions
     */
    private static final int MAX_FIRST_EXCEPTION_STACKELEMENTS = 30;
    /**
     * Maximum number of stacktrace lines printed for the 'caused by' exceptions
     */
    private static final int MAX_ADDL_EXCEPTION_STACKELEMENTS = 15;
    
	/**
	 * Recipients for only the emails that are for authorization errors
	 */
	private String authOnlyRecipients = null;
	/**
	 * Whether or not we are sending emails to interested parties rather than returning 403 errors to the users 
	 */
	private boolean sendEmailInsteadOfReturning403 = false;
	/**
	 * Contains information that will be printed out if we are sending emails for errors that have happened more than
	 * once in a specified time period
	 */
	private Map<String, DuplicateErrorInfo> errorMap = new HashMap<String, DuplicateErrorInfo>();
	private Long errorMapLock = new Long(0);
	
	/**
	 * Don't send an email for these errors
	 */
	private static final String[] ignoredErrors = {
         "com.netflix.conductor.core.execution.ApplicationException.com.netflix.conductor.core.orchestration.ExecutionDAOFacade.getWorkflowById"
	};
	
	public EmailSender() {
		super();
		this.authOnlyRecipients = getTrimmedString(config.getAuthErrorOnlyEmailAddress());
		this.sendEmailInsteadOfReturning403 = config.sendEmailInsteadOfReturning403();
	}
	
	public interface EmailBodyAdder {
		public void addBody(StringBuffer sb);
	}
	
	/**
	 * Send an email that tells the recipient(s) that there was an error in Conductor. For this email, all we have is the class
	 * and its method that generated the error.
	 * @param className the name of the class that generated the error
	 * @param methodName the name of the method where the error occurred
	 */
	public void sendErrorEmail(String className, String methodName) {
		/*
		 * Monitors.java will sometimes get an error() method call with a class name and method name equal to 'error'. In order to find
		 * out where the error was generated, we throw an exception so that the stack trace can show where it was originated. 
		 */
		if("error".equals(className) && "error".equals(methodName)) {
			try {
				throw new Exception("Printing stack trace from an 'error.error' error type");
			} catch(Exception e) {
				sendExceptionEmail("Got an 'error.error' error type. Stack Trace attached.", e);
			}
		} else {
			String errorKey = className + "." + methodName;
			sendThrottledEmail("Conductor Error: " + errorKey, errorKey, null, false, (sb) -> {
				if(ThreadLocalUser.getJwtUserId() != null) {
					sb.append("<div>User Id: <span style='font-weight:bold;'>" + ThreadLocalUser.getJwtUserId() + "</span></div>");
				}
				sb.append("<div>Class Name: <span style='font-weight:bold;'>" + className + "</span></div>");
				sb.append("<div>Method Name: <span style='font-weight:bold;'>" + methodName + "</span></div>");
			});
		}
	}

	/**
	 * Send an email that tells the recipient(s) that there was an exception in Conductor. For this email, we have the 
	 * error message to be displayed and the exception that was generated
	 * @param message a message to display in the email describing the exception context
	 * @param exception the exception
	 */
	public void sendExceptionEmail(String message, Throwable exception) {
		sendThrottledEmail("Conductor Exception: " + message, getExceptionMapKey(exception), null, false, (sb) -> {
			if(exception != null) {
				if(ThreadLocalUser.getJwtUserId() != null) {
					sb.append("<div>User Id: <span style='font-weight:bold;'>" + ThreadLocalUser.getJwtUserId() + "</span></div>");
				}
				sb.append("<div>Message: <span style='font-weight:bold;'>" + exception.getMessage() + "</span></div>");
				sb.append("<div style='text-decoration:underline;margin-top:5px;margin-bottom:2px;font-size:9px;font-weight:bold;font-family:Arial Black,Gadget,sans-serif'>STACK TRACE</div>");
				sb.append("<div style='font-size:10px;font-family:Courier New,Courier,monospace'>");
				addExceptionStackTrace(true, 1, sb, exception);
			} else {
				sb.append("<div>Message: <span style='font-weight:bold;'>" + message + "</span></div>");
			}
		});
	}

	/**
	 * Send an email that tells the recipient(s) that there was an error in Conductor.  
	 * @param shortMessage a short version of the error message to be displayed
	 * @param message error message to be displayed
	 */
	public void sendErrorMessageEmail(String shortMessage, String message) {
		sendThrottledEmail("Conductor Error: " + shortMessage, null, null, false, (sb) -> {
			sb.append("<div>Message: <span style='font-weight:bold;'>" + message + "</span></div>");
		});
	}

	/**
	 * Send an email that tells the recipient(s) that there was an authorization error in Conductor. 
	 * @param userId the user that tried to do something and got the auth error
	 * @param resourceName the name of the resource that the user was trying to access
	 * @param resourceType the type of the resource that the user was trying to access
	 * @param authorizationResponse the response that was given to the user
	 */
	public void sendAuthErrorEmail(String userId, String resourceName, String resourceType, String authorizationResponse) {
		sendAuthErrorEmail(userId, null, resourceName, resourceType, authorizationResponse);
	}

	/**
	 * Send an email that tells the recipient(s) that there was an authorization error in Conductor. 
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param resourceName the name of the resource that the user was trying to access
	 * @param resourceType the type of the resource that the user was trying to access
	 * @param authorizationResponse the response that was given to the user
	 */
	public void sendAuthErrorEmail(String userId, String loggedInUserId, String resourceName, String resourceType, String authorizationResponse) {
		sendThrottledEmail("Conductor Auth Error for user '" + (loggedInUserId == null ? userId : loggedInUserId) + "' and resource '" + resourceName + "'", 
			userId + "_" + resourceName + "_" + resourceType, this.authOnlyRecipients, true, (sb) -> {
				if(loggedInUserId != null) {
					sb.append("<div>Loggged-in User Id: <span style='font-weight:bold;'>" + loggedInUserId + "</span></div>");
				}
				sb.append("<div>User Id making the request: <span style='font-weight:bold;'>" + userId + "</span></div>");
				sb.append("<div>Resource Name: <span style='font-weight:bold;'>" + resourceName + "</span></div>");
				sb.append("<div>Resource Type: <span style='font-weight:bold;'>" + resourceType + "</span></div>");
				sb.append("<div style='text-decoration:underline;margin-top:5px;margin-bottom:2px;font-size:9px;font-weight:bold;font-family:Arial Black,Gadget,sans-serif'>MESSAGE FROM AUTHORIZATION SERVER</div>");
				sb.append("<div style='font-size:12px;font-family:Arial Black,Gadget,sans-serif'>" + authorizationResponse + "</div>");
		});
	}

	/**
	 * Send an email that tells the recipient(s) that there was a data entitlement error in Conductor. 
	 * @param userId the user that made the request that got the auth error (probably a service account)
	 * @param resourcePath the path of the resource that the user was trying to access
	 * @param resourceType the type of the resource that the user was trying to access
	 * @param resourcesWithIssues the resources that the user was trying to access when they got the error
	 * @param dataEntitlements the data entitlements that the user has
	 */
	public void sendDataEntitlementErrorEmail(String userId, String requestPath, String resourceType, List<String> resourcesWithIssues, List<String> dataEntitlements) {
		sendDataEntitlementErrorEmail(userId, null, requestPath, resourceType, resourcesWithIssues, dataEntitlements);
	}

	/**
	 * Send an email that tells the recipient(s) that there was a data entitlement error in Conductor. 
	 * @param userId the user that made the request (probably a service account) but could be a RACF id
	 * @param loggedInUserId the user that is logged in when the request was made (not always available - if it is, probably a RACF id)
	 * @param resourcePath the path of the resource that the user was trying to access
	 * @param resourceType the type of the resource that the user was trying to access
	 * @param resourcesWithIssues the resources that the user was trying to access when they got the error
	 * @param dataEntitlements the data entitlements that the user has
	 */
	public void sendDataEntitlementErrorEmail(String userId, String loggedInUserId, String requestPath, String resourceType, List<String> resourcesWithIssues, List<String> dataEntitlements) {
		sendThrottledEmail("Conductor Data Entitlement Error for user '" +  (loggedInUserId == null ? userId : loggedInUserId) + "' and uri '" + requestPath + "'", 
			userId + "_" + requestPath, this.authOnlyRecipients, true, (sb) -> {
				if(loggedInUserId != null) {
					sb.append("<div>Loggged-in User Id: <span style='font-weight:bold;'>" + loggedInUserId + "</span></div>");
				}
				sb.append("<div>User Id making the request: <span style='font-weight:bold;'>" + userId + "</span></div>");
				sb.append("<div>Request path: <span style='font-weight:bold;'>" + requestPath + "</span></div>");
				sb.append("<div>Resource type: <span style='font-weight:bold;'>" + resourceType + "</span></div>");
				sb.append("<div style='text-decoration:underline;margin-top:5px;margin-bottom:2px;font-size:9px;font-weight:bold;font-family:Arial Black,Gadget,sans-serif'>DATA ENTITLEMENT VIOLATIONS</div>");
				if(resourcesWithIssues != null && resourcesWithIssues.size() > 0) {
					sb.append("<table>");
					sb.append("<tr style='margin-top:5px;margin-bottom:2px;font-size:9px;font-weight:bold;font-family:Arial Black,Gadget,sans-serif;color:blue'><td style='margin-right:150px'>" + resourceType.toUpperCase() + " ID</td><td>REQUIRED DATA ENTITLEMENT</td></tr>");
					resourcesWithIssues.forEach(resourceWithIssue -> {
						String[] components = resourceWithIssue.split("\\|");
						if(components.length > 0) {
							sb.append("<tr style='font-size:9px;font-weight:normal;font-family:Arial Black,Gadget,sans-serif;color:black'><td>" + components[0] + "</td>");
							if(components.length > 1) {
								sb.append("<td>" + components[1] + "</td>");
							}
							sb.append("</tr>");
						}
					});
					sb.append("</table>");
				}
				sb.append("<div style='text-decoration:underline;margin-top:5px;margin-bottom:2px;font-size:9px;font-weight:bold;font-family:Arial Black,Gadget,sans-serif'>USER DATA ENTITLEMENTS</div>");
				if(dataEntitlements != null && dataEntitlements.size() > 0) {
					dataEntitlements.forEach(entitlement -> {
						sb.append("<div>" + entitlement + "</div>");
					});
				}
		});
	}

	/**
	 * Send an email. If the email has already occurred within a specified time period, don't send another one until the time
	 * period has expired but indicate in the email how many times the error has occurred in that time period
	 * @param subject subject of the email
	 * @param errorKey and error key that uniquely identifies the error
	 * @param additionalRecipients additional recipients list
	 * @param addAuthMessage whether or not to add an 'authorization' message to the bottom of the email
	 * @param bodyAdder a lambda function that will create the non-generic body of the email
	 */
	private void sendThrottledEmail(String subject, String errorKey, String additionalRecipients, boolean addAuthMessage, EmailBodyAdder bodyAdder) {
		if(StringUtils.isEmpty(errorKey) || shouldEmailBeSent(errorKey)) {
			try {
				MimeMessage msg = getMessage(additionalRecipients);
				msg.setSubject("[" + getEnvironment() + "] " + subject, "UTF-8");
				StringBuffer sb = new StringBuffer("<html><body><table><div style='font-family:Arial,Helvetica,sans-serif;font-size:11px;'>");
				/*
				 * Add a message at the end of the email if this is an authorization error email
				 */
				if(addAuthMessage) {
					addAuthEmailUserMessage(sb);
				}
				/*
				 * If the error for this email has happened multiple times in a specified time period, put something in the 
				 * email indicating this.
				 */
				DuplicateErrorInfo dei = null;
				synchronized(this.errorMapLock) {
					dei = this.errorMap.get(errorKey);
				}
				if(dei != null && dei.errorCount.get() > 1) {
					sb.append("<div style='color:red;margin-top:10px;margin-bottom:10px;'>" + 
						"This error has occurred " + dei.errorCount.get() + 
						" times since the last time we sent an email for it</div>");
					dei.intervalStartTimeMs = System.currentTimeMillis();
					dei.errorCount.set(0);
				}
				sb.append("<div>Host: <span style='font-weight:bold;'>" + InetAddress.getLocalHost().getHostName() + "</span></div>");
				/*
				 * Have the lambda function add the specific email message
				 */
				if(bodyAdder != null) {bodyAdder.addBody(sb);}
				sb.append("</div><div style='color:black;font-size:11px;margin-top:25px;font-family:Arial,Helvetica,sans-serif;font-style:italic'><span style='font-weight:bold'>Note that</span>" + 
					"<span style='font-weight:normal'> the next email for this same eerror (if another happens) won't be sent for at least " + this.config.getEmailDuplicateIntervalMinutes() + " minutes</span></div>");
				sb.append("</div></body></html>");
				msg.setText(sb.toString(), "utf-8", "html");
				Transport.send(msg);
			} catch(Exception e) {
				if(!RuntimeEnvironment.LOCAL.name().equals(getEnvironment())) {
					logger.error("Error sending throttled email for error key '" + errorKey + "'. Got this exception", e);
				}
			}
		}
	}

	/**
	 * Determine if the email should be sent based on a unique key for this type of email
	 * @param errorKey a unique key for this type of email
	 * @return whether or not the email should be sent
	 */
	private boolean shouldEmailBeSent(String errorKey) {
		boolean sendEmail = true;
		if(StringUtils.isNotEmpty(getSmtpHost()) && StringUtils.isNotEmpty(this.getTo())) {
			/*
			 * Don't send an email for errors that we want to ignore
			 */
			for(String ignored : ignoredErrors) {
				if(ignored.equals(errorKey)) {
					sendEmail = false;
					break;
				}
			}
			if(sendEmail) {
				/*
				 * Check if we have already sent this email in a specified time period. If we have, check to see if the time
				 * period has expired. If not, don't send the email.
				 */
				synchronized(this.errorMapLock) {
					DuplicateErrorInfo dei = this.errorMap.get(errorKey);
					if(dei == null) {
						this.errorMap.put(errorKey, new DuplicateErrorInfo());
					} else {
						if(System.currentTimeMillis() - dei.intervalStartTimeMs 
							< (this.config.getEmailDuplicateIntervalMinutes() * 60000)) {
							dei.errorCount.incrementAndGet();
							sendEmail = false;
						}
					}
				}
			}
		} else {
			sendEmail = false;
		}
		return sendEmail;
	}

	/**
	 * Add authorization text into the body of the email
	 * @param sb a StringBuffer containing the body of the email
	 */
	private void addAuthEmailUserMessage(StringBuffer sb) {
		sb.append("<div style='margin-bottom:15px;'>");
		sb.append("<span style='font-family:Arial,Helvetica,sans-serif;font-size:12px;font-style:italic;font-weight:bold;");
		if(this.sendEmailInsteadOfReturning403) {
			sb.append("color:black;'>");
			sb.append("Note that we will not be returning an UNAUTHORIZED response to the caller. Therefore you must interact with " +
				"the team that is causing this issue and resolve it.");
		} else {
			sb.append("color:red;'>");
			sb.append("Note that we have sent an UNAUTHORIZED response to the caller");
		}
		sb.append("</span>");
		sb.append("</div>");
	}
	
	/**
	 * Print a stacktrace in the body of the email
	 * @param first whether or not this is the first email in a specified time period
	 * @param exceptionCount the number of exceptions of this type that occurred in the time period
	 * @param sb the StringBuffer containing the body of the email
	 * @param e the exception for which to print the stack trace
	 */
	private void addExceptionStackTrace(boolean first, int exceptionCount, StringBuffer sb, Throwable e) {
		if(e != null) {
			StackTraceElement steArray[] = e.getStackTrace();
			int elementsLeft = steArray.length;
			int elementNumber = 0;
			int maxElements = (first ? MAX_FIRST_EXCEPTION_STACKELEMENTS : MAX_ADDL_EXCEPTION_STACKELEMENTS);
			sb.append("<span style='font-weight: bold;color:red'>");
			if(!first) {
				sb.append("<span style='font-weight: bold;color:red'>Caused by: </span>");
			}
			sb.append("<span style='font-weight: bold;color:blue'>" + e.getClass().getName() + "</span>");
			if(e.getMessage() != null) {
				sb.append("<span style='font-weight: bold;color:red'>: " + e.getMessage() + "</span>");
			}
			sb.append("</br>");
			for(StackTraceElement ste: steArray) {
				elementNumber++;
				elementsLeft--;
				sb.append("<span style='color:red;font-weight:normal'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;at " + this.getFullStackTraceElementMethodName(ste) 
					+ "(</span><span style='color:blue;font-weight:normal'>" + ste.getFileName());
				if(ste.getLineNumber() > 0) {
					sb.append(":" + ste.getLineNumber());
				}
				sb.append("</span><span style='color:red;font-weight:normal'>)</span></br>");
				if(elementNumber > maxElements && elementsLeft > 0) {
					sb.append("<span style='color:red;font-weight:normal'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;... " + elementsLeft + " more</span></br>");
					break;
				}
			}
			if(exceptionCount < 5 && e.getCause() != null) {
				addExceptionStackTrace(false, exceptionCount + 1, sb, e.getCause());
			}
		}
	}
	
	/**
	 * Get a unique key that will uniquely identify the exception as a 'type' of email so we know if we got a duplicate
	 * @param exception the exception
	 * @return a unique key that will uniquely identify the exception as a 'type' of email so we know if we got a duplicate
	 */
	private String getExceptionMapKey(Throwable exception) {
		if(exception != null) {
			String exceptionKey = exception.getClass().getName();
			StackTraceElement ste[] = exception.getStackTrace();
			if(ste != null && ste.length > 0) {
				exceptionKey += ("." + getFullStackTraceElementMethodName(ste[0]));
			}
			return exceptionKey;
		}
		return "";
	}
	
	private String getFullStackTraceElementMethodName(StackTraceElement ste) {
		String methodName = StringUtils.isNotBlank(ste.getMethodName()) ? ste.getMethodName().replace("<", "&lt;") : "";
		methodName = methodName.replace(">",  "&gt;");
		return ste.getClassName() + (StringUtils.isNotBlank(methodName) ? "." + methodName : "") ;
	}
	
	private MimeMessage getMessage(String additionalRecipients) throws Exception {
		MimeMessage msg = new MimeMessage(getMailSession());
		msg.addHeader("format", "flowed");
		msg.addHeader("Content-Transfer-Encoding", "8bit");
		if(StringUtils.isNotEmpty(getFrom())) {
			msg.setFrom(new InternetAddress(getFrom()));
		}
		if(StringUtils.isNotEmpty(getReplyTo())) {
			msg.setReplyTo(InternetAddress.parse(getReplyTo(), false));
		}
		msg.setSentDate(new Date());
		String recipients = getTo();
		if(StringUtils.isNotEmpty(additionalRecipients)) {
			if(StringUtils.isNotEmpty(recipients)) {
				recipients += ",";
			}
			recipients += additionalRecipients;
		}
		msg.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
		return msg;
	}

    private class DuplicateErrorInfo {
    	public AtomicInteger errorCount = new AtomicInteger(0);
    	public long intervalStartTimeMs = System.currentTimeMillis();
    }
}
