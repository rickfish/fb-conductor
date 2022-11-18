package com.bcbsfl.filter.security.jwt;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bcbsfl.filter.security.helpers.JWTLogger;
import com.bcbsfl.filter.security.jwt.error.JwtCustomError;
import com.bcbsfl.filter.security.jwt.error.JwtErrorResponse;
import com.google.inject.Singleton;

@Singleton
public class NetflixDomainFilter implements Filter {

	static final JWTLogger LOGGER = JWTLogger.getInstance();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// TODO Auto-generated method stub

	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		try {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			String domainFromURL = httpRequest.getParameter("domain");
			String domainFromJWT = (String) request.getAttribute("domain");
			if (domainFromURL == null)
				throw new JwtCustomError("domain name passed in the URL can't be null");
			else if (domainFromJWT == null)
				throw new JwtCustomError("There was not domain set in the JWT claim");
			else if (!domainFromURL.equalsIgnoreCase(domainFromJWT)) {
				throw new JwtCustomError("domain name passed in the URL " + domainFromURL
						+ " does not match with the domain set on JWT claim " + domainFromJWT);
			}

		} catch (Exception e) {
			unAuthorizedResponse(response, e.getMessage());
			return;
		}
		try {
			chain.doFilter(request, response);
		} catch (Throwable e) {
			// Do Nothing if the application thows an error
		}

	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

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
			JwtErrorResponse error = new JwtErrorResponse();
			error.setCode("403");
			error.setMessage(errorMessage);
			httpResponse.getWriter().append(error.getJSON());
			httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
			httpResponse.flushBuffer();

		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

}
