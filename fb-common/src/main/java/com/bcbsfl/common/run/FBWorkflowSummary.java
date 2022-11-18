/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.bcbsfl.common.run;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;


@JsonInclude(Include.NON_NULL)
public class FBWorkflowSummary extends WorkflowSummary{

	@JsonIgnore
	private Map<String, Object> tags = new LinkedHashMap<String, Object>();
	private ObjectMapper mapper = new ObjectMapper();
	private Workflow workflow;
	
	public FBWorkflowSummary() {
		super();
	}
	
	public FBWorkflowSummary(Workflow workflow) {
		super(workflow);
		this.workflow = workflow;
	}
	
	
	@Override
	@JsonRawValue
	public String getInput() {
		String input = workflow.getInput() == null ? "{}" : mapper.valueToTree(workflow.getInput()).toString();
		return input;
	}
	
	@Override
	@JsonRawValue
	public String getOutput() {
		String output = workflow.getOutput() == null ? "{}" : mapper.valueToTree(workflow.getOutput()).toString();
		return output;
	}

	public void addTag(String key, String value) {
		this.tags.put(key, value);
	}

	public Map<String, Object> getTags() {
		return tags;
	}
	@JsonProperty("tags")
	@JsonRawValue
	public String getTagsAsJson() {
		return mapper.valueToTree(this.tags).toString();
	}
}
