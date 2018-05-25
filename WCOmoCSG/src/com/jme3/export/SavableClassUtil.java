/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.export;

import com.jme3.animation.Animation;
import com.jme3.effect.shapes.*;
import com.jme3.export.NullSavable;
import com.jme3.export.Savable;
import com.jme3.material.MatParamTexture;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <code>SavableClassUtil</code> contains various utilities to handle
 * Savable classes. The methods are general enough to not be specific to any
 * particular implementation.
 * Currently it will remap any classes from old paths to new paths
 * so that old J3O models can still be loaded.
 *
 * @author mpowell
 * @author Kirill Vainer
 */
public class SavableClassUtil {

    private final static HashMap<String, String> CLASS_REMAPPINGS = new HashMap<>();
// wco 24May2018 - synch with 7913
// wco add 'prefix' concept
    private final static HashMap<String, String> CLASS_PREFIXES = new HashMap();
// wco
    public static void addRemapping(String oldClass, Class<? extends Savable> newClass){
        CLASS_REMAPPINGS.put(oldClass, newClass.getName());
    }
    public static void addPrefix( String pPrefix, String pFullPath ) {
    	CLASS_PREFIXES.put( "_" + pPrefix, pFullPath );
    }
    
    static {
        addRemapping("com.jme3.effect.EmitterSphereShape", EmitterSphereShape.class);
        addRemapping("com.jme3.effect.EmitterBoxShape", EmitterBoxShape.class);
        addRemapping("com.jme3.effect.EmitterMeshConvexHullShape", EmitterMeshConvexHullShape.class);
        addRemapping("com.jme3.effect.EmitterMeshFaceShape", EmitterMeshFaceShape.class);
        addRemapping("com.jme3.effect.EmitterMeshVertexShape", EmitterMeshVertexShape.class);
        addRemapping("com.jme3.effect.EmitterPointShape", EmitterPointShape.class);
        addRemapping("com.jme3.material.Material$MatParamTexture", MatParamTexture.class);
        addRemapping("com.jme3.animation.BoneAnimation", Animation.class);
        addRemapping("com.jme3.animation.SpatialAnimation", Animation.class);
        addRemapping("com.jme3.scene.plugins.blender.objects.Properties", NullSavable.class);
        
// wco	Add some standards to eliminate long names in the XML
        addRemapping( "ColorRGBA", ColorRGBA.class );
        addRemapping( "Vector3f", Vector3f.class );
        addRemapping( "Vector2f", Vector2f.class );
        addRemapping( "Transform", Transform.class );
    }
 
// wco add prefix processing
    private static String remapClass(String className) throws ClassNotFoundException {
        String result = CLASS_REMAPPINGS.get(className);
        if (result == null) {
        	int dotIndex;
        	// Transform "_prefix.x.y.z" to "some.real.path.x.y.z"
        	if ( className.startsWith( "_" ) && ((dotIndex = className.indexOf( '.', 1 )) > 1) ) {
        		String prefix = className.substring( 0, dotIndex );
        		prefix = CLASS_PREFIXES.get( prefix );
        		if ( prefix != null ) {
        			className = prefix + className.substring( dotIndex );
        		}
        	}
            return className;
        } else {
            return result;
        }
    }
    
    public static boolean isImplementingSavable(Class clazz){
        boolean result = Savable.class.isAssignableFrom(clazz);
        return result;
    }

    public static int[] getSavableVersions(Class<? extends Savable> clazz) throws IOException{
        ArrayList<Integer> versionList = new ArrayList<Integer>();
        Class superclass = clazz;
        do {
            versionList.add(getSavableVersion(superclass));
            superclass = superclass.getSuperclass();
        } while (superclass != null && SavableClassUtil.isImplementingSavable(superclass));
        
        int[] versions = new int[versionList.size()];
        for (int i = 0; i < versionList.size(); i++){
            versions[i] = versionList.get(i);
        }
        return versions;
    }
    
    public static int getSavableVersion(Class<? extends Savable> clazz) throws IOException{
        try {
            Field field = clazz.getField("SAVABLE_VERSION");
            Class<? extends Savable> declaringClass = (Class<? extends Savable>) field.getDeclaringClass();
            if (declaringClass == clazz){
                return field.getInt(null); 
            }else{
                return 0; // This class doesn't declare this field, e.g. version == 0
            }
        } catch (IllegalAccessException ex) {
            IOException ioEx = new IOException();
            ioEx.initCause(ex);
            throw ioEx;
        } catch (IllegalArgumentException ex) {
            throw ex; // can happen if SAVABLE_VERSION is not static
        } catch (NoSuchFieldException ex) {
            return 0; // not using versions
        }
    }
    
    public static int getSavedSavableVersion(Object savable, Class<? extends Savable> desiredClass, int[] versions, int formatVersion){
        Class thisClass = savable.getClass();
        int count = 0;
        
        while (thisClass != desiredClass) {
            thisClass = thisClass.getSuperclass();
            if (thisClass != null && SavableClassUtil.isImplementingSavable(thisClass)){
                count ++;
            }else{
                break;
            }
        }

        if (thisClass == null){
            throw new IllegalArgumentException(savable.getClass().getName() + 
                                               " does not extend " + 
                                               desiredClass.getName() + "!");
        }else if (count >= versions.length){
            if (formatVersion <= 1){
                return 0; // for buggy versions of j3o
            }else{
                throw new IllegalArgumentException(savable.getClass().getName() + 
                                                   " cannot access version of " +
                                                   desiredClass.getName() + 
                                                   " because it doesn't implement Savable");
            }
        }
        return versions[count];
    }
    
    /**
     * fromName creates a new Savable from the provided class name. First registered modules
     * are checked to handle special cases, if the modules do not handle the class name, the
     * class is instantiated directly. 
     * @param className the class name to create.
     * @return the Savable instance of the class.
     * @throws InstantiationException thrown if the class does not have an empty constructor.
     * @throws IllegalAccessException thrown if the class is not accessable.
     * @throws ClassNotFoundException thrown if the class name is not in the classpath.
     * @throws IOException when loading ctor parameters fails
     */
    public static Savable fromName(String className) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException, IOException {
    	return( fromNameExtended( className, null ) );
    }
// wco provide lookup for custom constructor
    static Class[] sConstructorParamTypes = new Class[] { JmeImporter.class };
    public static Savable fromNameExtended(
    	String		pClassName
    ,	JmeImporter	pExtensionArg
    ) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
    	pClassName = remapClass( pClassName );
        try {
        	Savable aResult = null;
            Class aClass = Class.forName( pClassName );
            
            // Special case to explicitly call out null
            if ( aClass == Void.class ) return( null );
            
            if ( pExtensionArg != null ) try {
            	Constructor<Savable> aConstructor = aClass.getDeclaredConstructor( sConstructorParamTypes );
            	Object[] paramArgs = new Object[] { pExtensionArg };
            	aResult = aConstructor.newInstance( paramArgs );
            } catch( Exception ex ) {
            	// No extended constructor
            	aResult = null;
            }
            if ( aResult == null ) {
            	// No special constructor, so use the standard
            	aResult = (Savable)aClass.newInstance();
            }
            return( aResult );
            
        } catch (InstantiationException ex) {
            Logger.getLogger(SavableClassUtil.class.getName()).log(
                    Level.SEVERE, "Could not access constructor of class ''{0}" + "''! \n"
                    + "Some types need to have the BinaryImporter set up in a special way. Please doublecheck the setup.", pClassName);
            throw ex;
        } catch (IllegalAccessException ex) {
            Logger.getLogger(SavableClassUtil.class.getName()).log(
                    Level.SEVERE, "{0} \n"
                    + "Some types need to have the BinaryImporter set up in a special way. Please doublecheck the setup.", ex.getMessage());
            throw ex;
        } catch( ClassNotFoundException ex ) {
        	throw ex;
        }
    }

    public static Savable fromName(String className, List<ClassLoader> loaders) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException, IOException {
        if (loaders == null) {
            return fromName(className);
        }
        
        String newClassName = remapClass(className);
        synchronized(loaders) {
            for (ClassLoader classLoader : loaders){
                final Class<?> loadedClass;
                try {
                    loadedClass = classLoader.loadClass(newClassName);
                } catch (final ClassNotFoundException e) {
                    continue;
                }
                try {
                    return (Savable) loadedClass.newInstance();
                } catch (InstantiationException e) {
                } catch (IllegalAccessException e) {
                }

            }
        }

        return fromName(className);
    }
    
// wco - access to class lookup
    public static Class classFromName(
    	String		pClassName
    )  throws ClassNotFoundException {
        pClassName = remapClass( pClassName );
        Class aClass = Class.forName( pClassName );
        return( aClass );
    }
}
