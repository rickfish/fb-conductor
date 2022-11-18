
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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "enabled",
    "supergroups",
    "endpoints"
})
public class EndpointConfigs {

    @JsonProperty("enabled")
    private Boolean enabled;
    @JsonProperty("supergroups")
    private List<String> superGroups = new ArrayList<String>();
    @JsonProperty("endpoints")
    private List<Endpoint> endpoints = new ArrayList<Endpoint>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("enabled")
    public Boolean getEnabled() {
        return enabled;
    }

    @JsonProperty("enabled")
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public EndpointConfigs withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @JsonProperty("superGroups")
    public List<String> getSuperGroups() {
        return superGroups;
    }

    @JsonProperty("superGroups")
    public void setSuperGroups(List<String> superGroups) {
        this.superGroups = superGroups;
    }

    public EndpointConfigs withSuperGroups(List<String> superGroups) {
        this.superGroups = superGroups;
        return this;
    }

    @JsonProperty("endpoints")
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    @JsonProperty("endpoints")
    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public EndpointConfigs withEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
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

    public EndpointConfigs withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(enabled).append(endpoints).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof EndpointConfigs) == false) {
            return false;
        }
        EndpointConfigs rhs = ((EndpointConfigs) other);
        return new EqualsBuilder().append(enabled, rhs.enabled).append(endpoints, rhs.endpoints).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
