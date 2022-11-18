package com.netflix.conductor.security.jwt.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("admin")
public class Admin {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getMessage() {

		return "This is an Admin message";
	}
}