package com.bcbsfl.filter.security.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class JWTLogger {

	static JWTLogger mylogger = new JWTLogger();

	public static JWTLogger getInstance(){
		if(mylogger == null){
			return new JWTLogger();
		}else{
			return mylogger;
		}
	}
	
	Level logLevel;
	Boolean toSysOut = false;
	
	public Level getLevel(){
		return logLevel;
	}

	public Boolean getToSysOut() {
		return toSysOut;
	}

	public void setToSysOut(Boolean toSysOut) {
		this.toSysOut = toSysOut;
	}

	public void setLevel(String level){
		switch (level.toLowerCase()) {
		case "all":
			logLevel = Level.ALL;
			break;
		case "debug":
			logLevel = Level.FINE;
			break;
		case "error":
			logLevel = Level.SEVERE;
			break;
		case "fatal":
			logLevel = (Level.SEVERE);
			break;
		case "info":
			logLevel = (Level.INFO);
			break;
		case "off":
			logLevel = (Level.OFF);
			break;
		case "trace":
			logLevel = (Level.FINEST);
			break;
		case "warn":
			logLevel = (Level.WARNING);
			break;
		default:
			logLevel = (Level.INFO);
			break;
		}
	}
	
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isTraceEnabled() {
		// TODO Auto-generated method stub
		if(logLevel == Level.FINEST)
			return true;
		return false;
	}

	public void trace(String msg) {
		// TODO Auto-generated method stub
		if(isTraceEnabled() && getToSysOut()){
			logMessage(Level.FINEST, msg);
		}else{
//			LOGGER.trace(msg);
		}
	}

	/*public void trace(String format, Object arg) {
		// TODO Auto-generated method stub
		
	}

	public void trace(String format, Object arg1, Object arg2) {
		// TODO Auto-generated method stub
		
	}

	public void trace(String format, Object... arguments) {
		// TODO Auto-generated method stub
		
	}

	public void trace(String msg, Throwable t) {
		// TODO Auto-generated method stub
		
	}

	public boolean isTraceEnabled(Marker marker) {
		// TODO Auto-generated method stub
		return false;
	}

	public void trace(Marker marker, String msg) {
		// TODO Auto-generated method stub
		
	}

	public void trace(Marker marker, String format, Object arg) {
		// TODO Auto-generated method stub
		
	}

	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		// TODO Auto-generated method stub
		
	}

	public void trace(Marker marker, String format, Object... argArray) {
		// TODO Auto-generated method stub
		
	}

	public void trace(Marker marker, String msg, Throwable t) {
		// TODO Auto-generated method stub
		
	}*/

	public boolean isDebugEnabled() {
		// TODO Auto-generated method stub
		if(logLevel == Level.FINE)
			return true;
		return false;
	}

	public void debug(String msg) {
		// TODO Auto-generated method stub
		if(isDebugEnabled() && getToSysOut()){
			logMessage(Level.FINE,msg);
		}else{
//			LOGGER.debug(msg);
		}
		
	}

	final static String dateFormat = "yyy-MM-dd HH:mm:ss";
	SimpleDateFormat df = new SimpleDateFormat(dateFormat);
	private void logMessage(Level level, String msg) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		
		//2017-08-10 10:54:18 DEBUG helpers.JWTHelper - Entered Method GetTokenFromRequest
		sb.append(df.format(new Date().getTime()))
			.append(" ")
			.append(level)
			.append(" - ")
//			.append(Thread.currentThread().getStackTrace()[1].getClassName())
//			.append(" - ")
//			.append(Thread.currentThread().getStackTrace()[2].getClassName())
//			.append(" - ")
			.append(Thread.currentThread().getStackTrace()[3].getClassName())
			.append(" - ")
			.append(msg);
		
		System.out.println(sb.toString());
		
		
	}

	public boolean isInfoEnabled() {
		// TODO Auto-generated method stub
		if(logLevel == Level.INFO)
			return true;
		return false;
	}

	public void info(String msg) {
		// TODO Auto-generated method stub
		if(isInfoEnabled() && getToSysOut()){
			logMessage(Level.INFO,msg);
		}else{
//			LOGGER.info(msg);
		}
	}

	public boolean isWarnEnabled() {
		// TODO Auto-generated method stub
		if(logLevel == Level.WARNING)
			return true;
		return false;
	}

	public void warn(String msg) {
		// TODO Auto-generated method stub
		if(isWarnEnabled() && getToSysOut()){
			logMessage(Level.WARNING,msg);
		}else{
//			LOGGER.warn(msg);
		}
		
	}

	public boolean isErrorEnabled() {
		// TODO Auto-generated method stub
		if(logLevel == Level.SEVERE)
			return true;
		return false;
	}

	public void error(String msg) {
		// TODO Auto-generated method stub
		if(isErrorEnabled() && getToSysOut()){
			logMessage(Level.SEVERE,msg);
		}else{
//			LOGGER.error(msg);
		}
		
	}

	public void setLevel(Level level) {
		// TODO Auto-generated method stub
		setLevel(level.toString());
		
	}

}
