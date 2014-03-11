package com.mebigfatguy.fbcontrib.utils;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;

public class CollectionUtils {

    private static JavaClass LIST_CLASS = null;
    private static JavaClass SET_CLASS = null;
    private static JavaClass MAP_CLASS = null;

    static {
        try {
            LIST_CLASS = Repository.lookupClass("java.util.List");
            SET_CLASS = Repository.lookupClass("java.util.Set");
            MAP_CLASS = Repository.lookupClass("java.util.Map");
        } catch (ClassNotFoundException cnfe) {
        }
    }
    
    private CollectionUtils() {       
    }
    
    
    /**
     * determines if the current class name is derived from List, Set or Map
     * 
     * @param clsName the class to determine it's parentage
     * @return if the class is a List, Set or Map
     */
    public static boolean isListSetMap(String clsName) throws ClassNotFoundException {
        JavaClass cls = Repository.lookupClass(clsName);
        return (cls.implementationOf(LIST_CLASS) || cls.implementationOf(SET_CLASS) || cls.implementationOf(MAP_CLASS));
    }
}
