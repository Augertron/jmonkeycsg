/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * Copyright (c) 2018 - WCOmohundro
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
package com.jme3.export.xml;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.animation.Animation;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.effect.shapes.EmitterMeshConvexHullShape;
import com.jme3.effect.shapes.EmitterMeshFaceShape;
import com.jme3.effect.shapes.EmitterMeshVertexShape;
import com.jme3.effect.shapes.EmitterPointShape;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.export.NullSavable;
import com.jme3.export.Savable;
import com.jme3.material.MatParamTexture;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/** Mimic those services provided by SavableClassUtil needed by the
 	XMLInputCapsule.  We can then extend/modify those services with no
 	need to edit SavableClassUtil from the core jme.
 	
 	In particular, extend the remapping to include abbreviated names
 	translated into longer paths via a 'prefix' notation of _xxx 
 	becoming  com.somewhere.somehow.xxx
 */
public class XMLClassUtil 
{
    private final static HashMap<String, Class> CLASS_REMAPPINGS = new HashMap<>();
    private final static HashMap<String, String> CLASS_PREFIXES = new HashMap();
    
    public static void addRemapping( String oldClassName, Class<? extends Savable> newClass ){
        CLASS_REMAPPINGS.put( oldClassName, newClass );
    }
    public static void addPrefix( String pPrefix, String pFullPath ) {
    	CLASS_PREFIXES.put( "_" + pPrefix, pFullPath );
    }
    
    static {
// from SavableClassUtil
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
        addRemapping( "SavableString", SavableString.class );
        addRemapping( "SavableList", SavableList.class );
    }
    
    /** fromName() creates a new Savable from the provided class name. First registered modules
        are checked to handle special cases, if the modules do not handle the class name, the
        class is instantiated directly. 

        @throws InstantiationException thrown if the class does not have an empty constructor.
        @throws IllegalAccessException thrown if the class is not accessable.
        @throws ClassNotFoundException thrown if the class name is not in the classpath.
        @throws IOException when loading ctor parameters fails
     */
    public static Savable fromName(
    	String 				pClassName
    ,	Map<String,Class>	pExtensions
    ) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
    	Class aClass = classFromName( pClassName, pExtensions );
    	if ( aClass == null ) {
    		throw new ClassNotFoundException( "No such class: " + pClassName );
    	}
    	return fromClass( aClass );
    }
    public static Savable fromClass(
    	Class	pClass
    ) throws InstantiationException, IllegalAccessException, IOException {
        try {
            // Special case to explicitly call out a null
            if ( pClass == Void.class ) return( null );
            
            Savable aResult = (Savable)pClass.newInstance();
            return( aResult );
            
        } catch( InstantiationException ex ) {
            Logger.getLogger( XMLClassUtil.class.getName() ).log(
                    Level.SEVERE, "Could not access constructor of class ''{0}" + "''! \n", pClass.getName() );
            throw ex;
        } catch( IllegalAccessException ex ) {
            Logger.getLogger( XMLClassUtil.class.getName()).log( Level.SEVERE, "{0} \n", ex.getMessage());
            throw ex;
        }
    }
    public static Savable fromClassField(
    	Class	pClass
    ,	String	pPossibleFieldName
    ) throws NoSuchFieldException, IllegalAccessException {
    	Field aField = pClass.getField( pPossibleFieldName );
		return (Savable)aField.get( null );
    }

    /** Interpret a given class name, returning NULL if there is no such class */
    public static Class classFromName(
    	String 				pClassName
    ,	Map<String,Class>	pExtensions
    ) {
        Class aClass = CLASS_REMAPPINGS.get( pClassName );
        if ( aClass == null ) try {
        	int dotIndex;
        	// Transform "_prefix.x.y.z" to "some.real.path.x.y.z"
        	if ( pClassName.startsWith( "_" ) && ((dotIndex = pClassName.indexOf( '.', 1 )) > 1) ) {
        		String prefix = pClassName.substring( 0, dotIndex );
        		prefix = CLASS_PREFIXES.get( prefix );
        		if ( prefix != null ) {
        			aClass = Class.forName( prefix + pClassName.substring( dotIndex ) );
        		}
        	}
        	if ( (aClass == null) && (pExtensions != null) ) {
        		// Look in the provided extensions
        		aClass = pExtensions.get( pClassName );
        	}
        	if ( aClass == null ) {
	        	// Locate the class from the name as given
	            aClass = Class.forName( pClassName );
        	}
        } catch( ClassNotFoundException ex ) {
        	// No such class
        	return null;
        }
        return aClass;
    }
    
    
    public static int getSavedSavableVersion(
    	Object 						savable
    , 	Class<? extends Savable> 	desiredClass
    , 	int[] 						versions
    , 	int 						formatVersion
    ) {
        Class thisClass = savable.getClass();
        int count = 0;
        
        while( thisClass != desiredClass ) {
            thisClass = thisClass.getSuperclass();
            if ( (thisClass != null) && Savable.class.isAssignableFrom(thisClass) ) {
                count ++;
            } else {
                break;
            }
        }
        if ( thisClass == null ) {
            throw new IllegalArgumentException(savable.getClass().getName() + 
                                               " does not extend " + 
                                               desiredClass.getName() + "!");
        } else if ( count >= versions.length ){
            if ( formatVersion <= 1 ) {
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

}
