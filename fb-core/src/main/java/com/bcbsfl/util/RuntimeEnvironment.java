package com.bcbsfl.util;

public enum RuntimeEnvironment {
	LOCAL,
    UNIT,
    TEST,
    STAGE,
    PROD;

    public static RuntimeEnvironment fromPipelineEnv(String pipelineEnv) {
        switch(pipelineEnv.toUpperCase()) {
        case "UNITA":
        case "UNITB":
        case "UNITC":
        	return UNIT;
        case "TESTA":
        case "TESTB":
        case "TESTC":
        	return TEST;
        case "STAGE":
        	return STAGE;
        case "PROD":
        	return PROD;
        default:
        	return LOCAL;
        }
    }
}
