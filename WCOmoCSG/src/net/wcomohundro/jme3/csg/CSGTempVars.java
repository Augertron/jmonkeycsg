/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * Copyright (c) 2015, WCOmohundro
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
package net.wcomohundro.jme3.csg;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import java.util.Stack;

import net.wcomohundro.jme3.math.Vector3d;


/** Variation of jme3 TempVars that includes Vector3d elements.
 	This is NOT intended for thread level caching, but instances are statically cached.
 */
public class CSGTempVars 
{
	/** Version tracking support */
	public static final String sCSGTempVarsRevision="$Rev$";
	public static final String sCSGTempVarsDate="$Date$";

	
	/** Simple static cache */
	protected static Stack<CSGTempVars> sTempVars = new Stack<CSGTempVars>();
	
	/** Accessor */
	public static CSGTempVars get(
	) {
		synchronized( sTempVars ) {
			if ( sTempVars.isEmpty() ) {
				return( new CSGTempVars() );
			} else {
				return( sTempVars.pop() );
			}
		}
	}
	
	/** Vectors */
	public Vector3f		vect1 = new Vector3f();
	public Vector3f		vect2 = new Vector3f();
	public Vector3f		vect3 = new Vector3f();
	public Vector3f		vect4 = new Vector3f();
	public Vector3f		vect5 = new Vector3f();
	
	public Vector2f		vect2d1 = new Vector2f();
	public Vector2f		vect2d2 = new Vector2f();
	public Vector2f		vect2d3 = new Vector2f();
	public Vector2f		vect2d4 = new Vector2f();
	public Vector2f		vect2d5 = new Vector2f();
	public Vector2f		vect2d6 = new Vector2f();
	public Vector2f		vect2d7 = new Vector2f();
	
	public Vector3d		vectd1 = new Vector3d();
	public Vector3d		vectd2 = new Vector3d();
	public Vector3d		vectd3 = new Vector3d();
	public Vector3d		vectd4 = new Vector3d();
	public Vector3d		vectd5 = new Vector3d();
	public Vector3d		vectd6 = new Vector3d();
	
	/** Release for reuse */
	public void release(
	) {
		synchronized( sTempVars ) {
			sTempVars.push( this );
		}
	}
}
