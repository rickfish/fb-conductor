
package com.bcbsfl.filter.security.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "endpoint",
    "method",
    "protection",
    "protectionNonProd",
    "secRoleLocation",
    "resourceType",
    "resourceNameLoc"
})
public class Endpoint {
	static public final String ENVIRONMENT_PROD = "PROD";

	public enum ProtectionType {
		RESTRICTED("RESTRICTED"), 
		SUPERGROUPS("SUPERGROUPS"), 
		AUTHORIZED("AUTHORIZED"), 
		INVALID("");

		private final String realName;
		
	    private ProtectionType(String realName) {
	        this.realName = realName;
	    }
	    public String getRealName() {
	        return realName;
	    }
	    static public ProtectionType fromRealName(String realName) {
	    	for(ProtectionType value: values()) {
	    		if(value.realName.equals(realName)) {
	    			return value;
	    		}
	    	}
	    	return INVALID;
	    }
	}

	public enum SecRoleLocation {
		INPUT_OF_ALL_TASKS("INPUT_OF_ALL_TASKS"), 
		INPUT_OF_ALL_WORKFLOWS_TASKS("INPUT_OF_ALL_WORKFLOWS_TASKS"), 
		REQUEST_BODY_INPUT("REQUEST_BODY_INPUT"), 
		REQUEST_BODY("REQUEST_BODY"), 
		INVALID("");

		private final String realName;
		
	    private SecRoleLocation(String realName) {
	        this.realName = realName;
	    }
	    public String getRealName() {
	        return realName;
	    }
	    static public SecRoleLocation fromRealName(String realName) {
	    	for(SecRoleLocation value: values()) {
	    		if(value.realName.equals(realName)) {
	    			return value;
	    		}
	    	}
	    	return INVALID;
	    }
	}
	
	public enum ResourceType {
		WORKFLOW, TASK, WORKFLOWTASK, METADATA
	}
	
	public enum ResourceNameLocation {
		PATH, BODY, ID_LOOKUP, ARRAY_OF_IDS
	}

	public enum HttpMethodType {
		GET, PUT, POST, DELETE, PATCH, OPTIONS
	}

	/**
	 * We will need this if there is a different protection scheme in non-prod versus prod environments
	 */
	private static String environment = System.getenv("environment");
	static {
		if(environment != null) environment = environment.toUpperCase();
	}

	@JsonProperty("endpoint")
    private String endpoint;
    @JsonProperty("method")
    private List<HttpMethodType> method = new ArrayList<HttpMethodType>();
    /**
     * These will be constructed from the String in the setter() method
     */
    private ProtectionType protection;
    private ProtectionType protectionNonProd;
    
    private SecRoleLocation secRoleLocation;

    @JsonProperty("resourceType")
    private ResourceType resourceType;
    @JsonProperty("resourceNameLoc")
    private ResourceNameLoc resourceNameLoc;
    
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("endpoint")
    public String getEndpoint() {
        return endpoint;
    }

    @JsonProperty("endpoint")
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Endpoint withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @JsonProperty("method")
    public List<HttpMethodType> getMethod() {
        return method;
    }

    @JsonProperty("method")
    public void setMethod(List<HttpMethodType> method) {
        this.method = method;
    }

    public Endpoint withMethod(List<HttpMethodType> method) {
        this.method = method;
        return this;
    }

    public ProtectionType getEnvProtection() {
    	ProtectionType envProtection = this.protection;
    	if(this.protectionNonProd != null && !ENVIRONMENT_PROD.equals(environment)) {
    		envProtection = this.protectionNonProd;
    	}
    	return envProtection;
    }
    
    @JsonProperty("protection")
    public ProtectionType getProtection() {
        return protection;
    }

    @JsonProperty("protection")
    public void setProtection(String protection) {
        this.protection = ProtectionType.fromRealName(protection);
    }

    public Endpoint withProtection(ProtectionType protection) {
        this.protection = protection;
        return this;
    }

    @JsonProperty("protectionNonProd")
    public ProtectionType getProtectionNonProd() {
        return protectionNonProd;
    }

    @JsonProperty("protectionNonProd")
    public void setProtectionNonProd(String protectionNonProd) {
        this.protectionNonProd = ProtectionType.fromRealName(protectionNonProd);
    }

    public Endpoint withProtectionNonProd(ProtectionType protectionNonProd) {
        this.protectionNonProd = protectionNonProd;
        return this;
    }

    @JsonProperty("secRoleLocation")
    public SecRoleLocation getSecRoleLocation() {
        return secRoleLocation;
    }

    @JsonProperty("secRoleLocation")
    public void setSecRoleLocation(String secRoleLocation) {
        this.secRoleLocation = SecRoleLocation.fromRealName(secRoleLocation);
    }

    public Endpoint withSecRoleLocation(SecRoleLocation secRoleLocation) {
        this.secRoleLocation = secRoleLocation;
        return this;
    }

    @JsonProperty("resourceType")
    public ResourceType getResourceType() {
        return resourceType;
    }

    @JsonProperty("resourceType")
    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public Endpoint withResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    @JsonProperty("resourceNameLoc")
    public ResourceNameLoc getResourceNameLoc() {
        return resourceNameLoc;
    }

    @JsonProperty("resourceNameLoc")
    public void setResourceNameLoc(ResourceNameLoc resourceNameLoc) {
        this.resourceNameLoc = resourceNameLoc;
    }

    public Endpoint withResourceNameLoc(ResourceNameLoc resourceNameLoc) {
        this.resourceNameLoc = resourceNameLoc;
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Endpoint withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(endpoint).append(method).append(protection).append(resourceNameLoc).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Endpoint) == false) {
            return false;
        }
        Endpoint rhs = ((Endpoint) other);
        return new EqualsBuilder().append(endpoint, rhs.endpoint).append(method, rhs.method).append(protection, rhs.protection).append(resourceNameLoc, rhs.resourceNameLoc).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceNameLoc {
        @JsonProperty("location")
        private ResourceNameLocation location;
        @JsonProperty("arrayName")
        private String arrayName;
        @JsonProperty("attributeName")
        private String attributeName;
        @JsonProperty("pathSegment")
        private Integer pathSegment;
        
        @JsonProperty("location")
        public ResourceNameLocation getLocation() {
            return location;
        }

        @JsonProperty("location")
        public void setLocation(ResourceNameLocation location) {
            this.location = location;
        }

        public ResourceNameLoc withLocation(ResourceNameLocation location) {
            this.location = location;
            return this;
        }

        
        @JsonProperty("arrayName")
        public String getArrayName() {
            return arrayName;
        }

        @JsonProperty("arrayName")
        public void setArrayName(String arrayName) {
            this.arrayName = arrayName;
        }

        public ResourceNameLoc withArrayName(String arrayName) {
            this.arrayName = arrayName;
            return this;
        }

        
        @JsonProperty("attributeName")
        public String getAttributeName() {
            return attributeName;
        }

        @JsonProperty("attributeName")
        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        public ResourceNameLoc withAttributeName(String attributeName) {
            this.attributeName = attributeName;
            return this;
        }

        
        @JsonProperty("pathSegment")
        public Integer getPathSegment() {
            return pathSegment;
        }

        @JsonProperty("pathSegment")
        public void setPathSegment(Integer pathSegment) {
            this.pathSegment = pathSegment;
        }

        public ResourceNameLoc withPathSegment(Integer pathSegment) {
            this.pathSegment = pathSegment;
            return this;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(location).append(arrayName).append(attributeName).append(pathSegment).toHashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if ((other instanceof ResourceNameLoc) == false) {
                return false;
            }
            ResourceNameLoc rhs = ((ResourceNameLoc) other);
            return new EqualsBuilder().append(location, rhs.location).append(arrayName, rhs.arrayName).append(attributeName, rhs.attributeName).append(pathSegment, rhs.pathSegment).isEquals();
        }
    }
}
