package com.bcbsfl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.netflix.conductor.common.metadata.tasks.Task;

/**
 * Utilities used to process JSON objects
 *
 */
public class JsonUtils {

	/**
	 * Get a string that is the value for an attribute within
	 * a Task's inputData map
	 * @param task
	 * @param propertyName
	 * @return
	 */
	public static String getInputDataString(Task task, String propertyName) {
		Object o = getInputDataProperty(task, propertyName);
		return (o == null ? "" : o.toString());
	}
	
	/**
	 * Get a list of strings that is the value for an attribute within
	 * a Task's inputData map
	 * @param task
	 * @param propertyName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static List<String> getInputDataStringList(Task task, String propertyName) {
		Object o = getInputDataProperty(task, propertyName);
		return (o == null ? new ArrayList<String>() : (List<String>) o);
	}
	
	/**
	 * Get an Object that is the value for an attribute within
	 * a Task's inputData map
	 * @param task
	 * @param propertyName
	 * @return
	 */
	public static Object getInputDataProperty(Task task, String propertyName) {
		Object o = null;
		if(task.getInputData() != null) {
			if(task.getInputData().get(propertyName) != null) {
				o = task.getInputData().get(propertyName);
			}
		}
		return o;
	}

	/**
	 * Convert a JsonObject into a Java Map object
	 * @param jsonobj
	 * @return
	 */
    public static List<Map<String, Object>> toListOfMaps(JsonArray jsonArray) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        jsonArray.forEach(jsonElement -> {
            list.add(toMap(jsonElement.getAsJsonObject()));
        });
        return list;
    }

	/**
	 * Convert a JsonObject into a Java Map object
	 * @param jsonobj
	 * @return
	 */
    public static Map<String, Object> toMap(JsonObject jsonobj) {
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<Map.Entry<String, JsonElement>> entries = jsonobj.entrySet().iterator();
        while(entries.hasNext()) {
            Map.Entry<String, JsonElement> entry = entries.next();
            Object value = entry.getValue();
            if (value instanceof JsonArray) {
                value = toList((JsonArray) value);
            } else if (value instanceof JsonObject) {
                value = toMap((JsonObject) value);
            } else if (value instanceof JsonPrimitive) {
            	value = ((JsonPrimitive) value).getAsString();
            }
            map.put(entry.getKey(), value);
        }   
        return map;
    }

    /**
     * Convert a JsonArray object into a Java List of objects
     * @param array
     * @return
     */
    public static List<Object> toList(JsonArray array) {
        List<Object> list = new ArrayList<Object>();
        for(int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            if (value instanceof JsonArray) {
                value = toList((JsonArray) value);
            } else if (value instanceof JsonPrimitive) {
            	JsonPrimitive p = (JsonPrimitive) value;
            	if(p.isBoolean()) {
            		value = p.getAsBoolean();
            	} else if(p.isNumber()) {
            		value = p.getAsNumber();
            	} else {
            		value = p.getAsString();
            	}
            } else if (value instanceof JsonObject) {
                value = toMap((JsonObject) value);
            }
            list.add(value);
        }   
        return list;
    }
}
