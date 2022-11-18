package com.bcbsfl.filter.security.jwt;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

class EntitlementResponseWrapper extends HttpServletResponseWrapper {

	protected StringWriter stringWriter;

	public ByteArrayOutputStream output;
	
	public ByteArrayOutputStream getOutput() {
		return output;
	}

	public void setOutput(ByteArrayOutputStream output) {
		this.output = output;
	}

	protected boolean getOutputStreamCalled;

	protected boolean getWriterCalled;

	public EntitlementResponseWrapper(HttpServletResponse response) {
		super(response);
		output=new ByteArrayOutputStream();
	}
	
	 public byte[] getData() { 
		    return output.toByteArray(); 
		  } 

	public ServletOutputStream getOutputStream() { 
	    return new CustomOutputStream(output); 
	  } 
	
	public PrintWriter getWriter() { 
	    return new PrintWriter(getOutputStream(),true); 
	  } 

	/*@Override
	public PrintWriter getWriter() throws IOException {
		if (getOutputStreamCalled) {
            throw new IllegalStateException("The getOutputStream() is already called.");
        }
 
        this.stringWriter = new StringWriter();
 
        return new PrintWriter(this.stringWriter);
	}*/

	public String getResponseContent() {
        if (this.stringWriter != null) {
            return this.stringWriter.toString();
        }
        return "";
    }
}