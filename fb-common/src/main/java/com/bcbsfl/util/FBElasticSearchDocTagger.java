package com.bcbsfl.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FBElasticSearchDocTagger {

	private static Logger logger = LoggerFactory.getLogger(FBElasticSearchDocTagger.class);
	
	/**
	 * The list of Florida Blue attribute names that will be moved from inputData, outputData to a 'tags'
	 * section of the document sent to ElasticSearch
	 */
	private List<String> fbTags = null;
			
	public FBElasticSearchDocTagger() {
		loadFBTags();
	}

    /**
     * Load the fb_attrs.json file which contains Florida Blue attribute names
     * that will be pulled out of the inputData and outputData sections of the
     * conductor JSONs and placed in a root-level 'tags' section.
     */
    private void loadFBTags() {
    	if(this.fbTags == null) {
    		try {
	    		InputStream fbTagsStream = this.getClass().getResourceAsStream("/fb_tags.json");
	    		if(fbTagsStream != null) {
	    			this.fbTags = new ArrayList<String>();
		    		ObjectMapper mapper = new ObjectMapper();
		    		JsonNode tree = mapper.readTree(fbTagsStream);
		    		/*
		    		 * There are one or more nodes in the JSON, get all of the string arrays 
		    		 * in each node and load them all into the tags we look for. The JSON looks
		    		 * something like this:
		    		 * 
					 * {
					 *     	"enterprise" : [
					 * 			"memberId",
					 * 			"providerId"
					 * 		],
					 * 		"domain" : [
					 * 			"claimId"
					 * 		]
					 * }
					 * 
					 * The 'enterprise' node contains all the enterprise-wide attributes, all other
					 * nodes are domain specific.
					 */
		    		tree.forEach(node -> {
			    		node.forEach(attr -> {
			    			this.fbTags.add(attr.asText());
			    		});
		    		});
	    		}
    		} catch(Throwable t) {
    			fbTags = null;
    			logger.error("Failed to load fbAttrs.json", t);
    		}
    	}
/*    	
    	JsonObject json = getJsonObjectFromFile("/errorWorkflow.json");
    	Map<String, Object> tags = new HashMap<String, Object>();
    	Map<String, Object> input = JsonUtils.toMap(json.get("input").getAsJsonObject());
    	Map<String, Object> output = new HashMap<String, Object>();
    	this.populateFBTags(tags, input, output);
    	System.out.println("done");
*/    	
    }
    
    private JsonObject getJsonObjectFromFile(String filepath) {
    	try {
    		JsonElement jsonElement = getJsonElementFromFile(filepath);
    		if(jsonElement != null) {
        		JsonObject jsonObject = jsonElement.getAsJsonObject();
        		return jsonObject;
    		}
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    private JsonArray getJsonArrayFromFile(String filepath) {
    	try {
    		JsonElement jsonElement = getJsonElementFromFile(filepath);
    		if(jsonElement != null) {
        		JsonArray jsonArray = jsonElement.getAsJsonArray();
        		return jsonArray;
    		}
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    private JsonElement getJsonElementFromFile(String filepath) {
    	try {
    		JsonParser parser = new JsonParser();
    		InputStream stream = this.getClass().getResourceAsStream(filepath);
    		JsonElement jsonElement = parser.parse(new InputStreamReader(stream));
    		return jsonElement;
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    public void populateFBTags(Map<String, Object> tags, Map<String, Object> input, Map<String, Object> output) {
		if(this.fbTags != null && this.fbTags.size() >  0) {
			if(input != null && input.size() > 0) {
				populateFBTagsFromMap(tags, input); 
			}
			if(output != null && output.size() > 0) {
				populateFBTagsFromMap(tags, output); 
			}
		}
	}
	
	private void populateFBTagsFromMap(Map<String, Object> tags, Map<String, Object> theMap) {
		theMap.keySet().forEach(key -> {
			processAttrValue(tags, key, theMap.get(key)); 
		});
	}
	
	@SuppressWarnings("unchecked")
	private void processAttrValue(Map<String, Object> tags, String tag, Object attrValue) {
		if(attrValue != null) {
			if(attrValue instanceof Map) {
				populateFBTagsFromMap(tags, (Map<String, Object>) attrValue);
			} else if(attrValue.getClass().isArray()) {
				Object[] theArray = ((Object[]) attrValue);
				for(int i = 0; i < theArray.length; i++) {
					if(theArray[i] instanceof Map) {
						populateFBTagsFromMap(tags, (Map<String, Object>) attrValue);
					} else {
						addTagIfFbTag(tags, tag, theArray[i].toString());
					}						
				}
			} else if(attrValue instanceof List) {
				List<Object> theList = (List<Object>) attrValue;
				theList.forEach(listEntry -> {
					if(listEntry instanceof Map) {
						populateFBTagsFromMap(tags, (Map<String, Object>) listEntry);
					} else {
						addTagIfFbTag(tags, tag, (listEntry == null ? null : listEntry.toString()));
					}						
				});
			} else {
				addTagIfFbTag(tags, tag, attrValue.toString());
			}
		}
	}

	private void addTagIfFbTag(Map<String, Object> tags, String key, String value) {
		if(this.fbTags.contains(key)) {
			/*
			 * There should only be one value for a tag
			 */
			if(!tags.containsKey(key) && StringUtils.isNotBlank(value)) {
				tags.put(key, value);
			}
		}
	}
}
