package com.mebigfatguy.fbcontrib.instrument;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import edu.umd.cs.findbugs.ba.ClassContext;

@Aspect
public class Timing {

	private static AtomicReference<Map<String, TimingInfo>> timingInfo = new AtomicReference<>();
	private static AtomicLong updateTime = new AtomicLong(System.currentTimeMillis());
	static {
	    Thread t = new Thread(() -> {
	    	try {
		    	while (!Thread.interrupted()) {
		    		Thread.sleep(15000L);
		    		if (System.currentTimeMillis() - updateTime.get() > 15000L) {
		    			Map<String, TimingInfo> infoMap = timingInfo.getAndSet(null);
		    			if (infoMap != null && !infoMap.isEmpty()) {
		    				Path p = Files.createTempFile("sb-contrib-timings",".csv");
		    				try (PrintWriter pr = new PrintWriter(Files.newBufferedWriter(p))) {
		    					pr.println("detector,number of calls,total time,longest time,longest input");
		    					for (Map.Entry<String, TimingInfo> entry: infoMap.entrySet()) {
		    						TimingInfo info = entry.getValue();
		    						pr.println(entry.getKey() + "," + info.numberOfCalls + "," + info.totalTime+ "," + info.longestTime + "," + (info.longestInput == null ? "--none--" : info.longestInput));
		    					}
		    				}
		    			}
		    		}
		    	}
	    	} catch (Exception e) {
	    	}
	    });
	    t.setDaemon(true);
	    t.setPriority(Thread.MIN_PRIORITY+1);
	    t.start();
	}
	
    @Around("execution(* com.mebigfatguy.fbcontrib.detect.*.visitClassContext(..)) && args(classContext)")
    public Object timings(ProceedingJoinPoint joinPoint,ClassContext classContext) throws Throwable {
    	long start = System.currentTimeMillis();
		updateTime.set(start);
    	try {
            Object result = joinPoint.proceed();
            return result;
    	} finally {
    		long delta = System.currentTimeMillis() - start;
    		String detector = joinPoint.getSignature().getDeclaringTypeName();
    		Map<String, TimingInfo> infoMap = timingInfo.get();
    		if (infoMap == null) {
    			infoMap = new TreeMap<>();
    			timingInfo.set(infoMap);
    		}
    		TimingInfo info = infoMap.get(detector);
    		if (info == null) {
    			info = new TimingInfo();
    			infoMap.put(detector, info);
    		}
    		info.numberOfCalls++;
    		info.totalTime += delta;
    		if (delta > info.longestTime) {
    			info.longestTime = delta;
    			info.longestInput = classContext.getJavaClass().getClassName();
    		}
    	}
    }
    
    static class TimingInfo {
    	long numberOfCalls;
    	long totalTime;
    	long longestTime;
    	String longestInput;
    }
}
