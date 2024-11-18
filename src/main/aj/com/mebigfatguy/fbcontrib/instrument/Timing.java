package com.mebigfatguy.fbcontrib.instrument;

import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import edu.umd.cs.findbugs.ba.ClassContext;

@Aspect
public class Timing {

	private static final Map<String, TimingInfo> timingInfo = new HashMap<>();
	
    @Around("execution(* com.mebigfatguy.fbcontrib.detect.*.visitClassContext(..)) && args(classContext)")
    public Object timings(ProceedingJoinPoint joinPoint,ClassContext classContext) throws Throwable {
    	long start = System.currentTimeMillis();
    	try {
            Object result = joinPoint.proceed();
            return result;
    	} finally {
    		long delta = System.currentTimeMillis() - start;
    		String detector = joinPoint.getSignature().getDeclaringTypeName();
    		TimingInfo info = timingInfo.get(detector);
    		if (info == null) {
    			info = new TimingInfo();
    			timingInfo.put(detector, info);
    		}
    		info.numberOfCalls++;
    		info.totalTime += delta;
    		if (delta > info.longestTime) {
    			info.longestTime = delta;
    			info.longestInput = classContext.getJavaClass().getClassName();
    		}
    	}
    }
    
    class TimingInfo {
    	long numberOfCalls;
    	long totalTime;
    	long longestTime;
    	String longestInput;
    }
}
