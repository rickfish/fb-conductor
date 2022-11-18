package com.bcbsfl.mail;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.config.FBCoreConfiguration;
import com.bcbsfl.config.FBSystemPropertiesCoreConfiguration;
import com.bcbsfl.util.RuntimeEnvironment;

/**
 * Basic Email sender meant to be extended in order to add business logic. Extended classes will make use of the  
 * basic email functionality in this class to just send the email using Java Mail.
 */
public class BasicEmailSender {
    private static final Logger logger = LoggerFactory.getLogger(BasicEmailSender.class);
	private Session mailSession = null;
	private Long mailSessionLock = new Long(0);
	private String smtpHost = null;
	private String from = null;
	private String replyTo = null;
	private String to = null;
	private String environment = RuntimeEnvironment.LOCAL.name();
	protected FBCoreConfiguration config = new FBSystemPropertiesCoreConfiguration();
	
    public BasicEmailSender() {
		this.smtpHost = getTrimmedString(config.getEmailSmtpHost());
		this.from = getTrimmedString(config.getEmailAddressFrom());
		this.replyTo = getTrimmedString(config.getEmailAddressReplyTo());
		this.to = getTrimmedString(config.getEmailAddressTo());
		Properties props = System.getProperties();
		props.put("mail.smtp.host", this.smtpHost);
		this.mailSession = Session.getInstance(props, null);
		this.environment = this.config.getPipelinedEnvironmentName();

		attemptToGetMailSession();
		if(this.smtpHost != null) {
			logger.info("JavaMail Session for {} created with the {} SMTP host", this.getClass().getName(), this.smtpHost);
		}
		if(this.from != null) {
			logger.info("{} will use {} as its 'from' email address", this.getClass().getName(), this.from);
		}
		if(this.replyTo != null) {
			logger.info("{} will use {} as its 'reply-to' email address", this.getClass().getName(), this.replyTo);
		}
    }

    public void sendEmail(String recipient, String additionalRecipients, String subject, String body) {
    	if(!isEmailAvailable()) {
    		return;
    	}
    	try {
        	MimeMessage message = getMessage(recipient, additionalRecipients);
        	message.setSubject(subject, "UTF-8");
			message.setText(body, "utf-8", "html");
        	Transport.send(message);
    	} catch(Exception e) {
			if(!RuntimeEnvironment.LOCAL.name().equals(getEnvironment())) {
	    		logger.error("While trying to send email, got this error", e);
			}
    	}
    }
    
    protected boolean isEmailAvailable() {
    	boolean available = false; 
		if(StringUtils.isNotBlank(this.smtpHost)) {
	    	synchronized(this.mailSessionLock) {
	    		available = this.mailSession != null; 
		    	if(!available) {
	    			attemptToGetMailSession();
	    			available = this.mailSession != null;
		    	}
	    	}
		}
    	return available;
    }
    
    protected Session getMailSession() {
    	Session session = null;
    	synchronized(this.mailSessionLock) {
    		session = this.mailSession;
    	}
    	return session;
    }
    
    private void attemptToGetMailSession() {
		Properties props = System.getProperties();
		props.put("mail.smtp.host", this.smtpHost);
		try {
			this.mailSession = Session.getInstance(props, null);
		} catch(Exception e) {
			logger.info("Exception creating the JavaMail Session with the '{}' property (value: {}) so emails will not be sent", FBCoreConfiguration.EMAIL_SMTP_HOST_PROPERTY_NAME, this.smtpHost);
			e.printStackTrace();
		}
    }
    
	private MimeMessage getMessage(String recipient, String additionalRecipients) throws Exception {
		Session session = getMailSession();
		if(session == null) {
			throw new Exception("Trying to send email but the mail Session is null");
		}
		MimeMessage msg = new MimeMessage(this.mailSession);
		msg.addHeader("format", "flowed");
		msg.addHeader("Content-Transfer-Encoding", "8bit");
		if(StringUtils.isNotEmpty(this.from)) {
			msg.setFrom(new InternetAddress(this.from));
		}
		if(StringUtils.isNotEmpty(this.replyTo)) {
			msg.setReplyTo(InternetAddress.parse(this.replyTo, false));
		}
		String recipients = recipient;
		if(StringUtils.isNotEmpty(additionalRecipients)) {
			if(StringUtils.isNotEmpty(recipients)) {
				recipients += ",";
			}
			recipients += additionalRecipients;
		}
		msg.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
		return msg;
	}

	public String getTrimmedString(String s) {
		return s == null ? null : s.trim();
	}

	public String getSmtpHost() {
		return smtpHost;
	}
	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}
	public void setMailSession(Session mailSession) {
		this.mailSession = mailSession;
	}

	public String getFrom() {
		return from;
	}
	public void setFrom(String from) {
		this.from = from;
		if(this.from != null) {
			logger.info("{} will use {} as its 'from' email address", this.getClass().getName(), this.from);
		}
	}
	public String getReplyTo() {
		return replyTo;
	}
	public void setReplyTo(String replyTo) {
		this.replyTo = replyTo;
		if(this.replyTo != null) {
			logger.info("{} will use {} as its 'reply-to' email address", this.getClass().getName(), this.replyTo);
		}
	}
	public String getTo() {
		return to;
	}
	public void setTo(String to) {
		this.to = to;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}
}
