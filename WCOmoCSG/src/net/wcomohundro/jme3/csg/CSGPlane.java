/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2015, WCOmohundro
	All rights reserved.

	Redistribution and use in source and binary forms, with or without modification, are permitted 
	provided that the following conditions are met:

	1. 	Redistributions of source code must retain the above copyright notice, this list of conditions 
		and the following disclaimer.

	2. 	Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
		and the following disclaimer in the documentation and/or other materials provided with the distribution.
	
	3. 	Neither the name of the copyright holder nor the names of its contributors may be used to endorse 
		or promote products derived from this software without specific prior written permission.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
	WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
	PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
	ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
	LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
	INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
	OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN 
	IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;
import com.jme3.scene.plugins.blender.math.Vector3d;


/**  Constructive Solid Geometry (CSG)
 
  	A CSGPlane is quick representation of a flat surface, as defined by an arbitrary 'normal'. 
 	Its inherent 'dot' is computed and stored for quick access.
 	
	The following is a note I found on the web about what "dot" means in 3D graphics
		Backface culling
			When deciding if a polygon is facing the camera, you need only calculate the dot product 
			of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
			vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
			value is greater than zero, it is facing away from the camera. 
			
	which means that some simple comparisons of a Plane/Polygon 'dot' can give us useful positioning
	information.
	
	NOTE
		that a plane is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its normal or dot.  Therefore, a clone of a plane is just the plane itself.
 */
public abstract class CSGPlane<VectorT,VertexT>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPlaneRevision="$Rev$";
	public static final String sCSGPlaneDate="$Date$";

	/** Service routine that constructs an arbitrary plane that passes through a given point */
	public static CSGPlane fromCenter(
		Vector3f			pCenter
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGPlane aPlane;
		if ( pEnvironment.mDoublePrecision ) {
			// Working in DOUBLES
			Vector3d aPoint = pTempVars.vectd1.set( pCenter.x, pCenter.y, pCenter.z );
			Vector3d bPoint = pTempVars.vectd2.set( aPoint ).addLocal( 1.0, 1.0, 1.0 );
			Vector3d cPoint = pTempVars.vectd3.set( aPoint ).addLocal( -1.0, -1.0, -1.0 );
			aPlane = CSGPlaneDbl.fromPoints( aPoint, bPoint, cPoint, pTempVars, pEnvironment );
		} else {
			// Working in FLOATS
			Vector3f aPoint = pTempVars.vect1.set( pCenter.x, pCenter.y, pCenter.z );
			Vector3f bPoint = pTempVars.vect2.set( aPoint ).addLocal( 1.0f, 1.0f, 1.0f );
			Vector3f cPoint = pTempVars.vect3.set( aPoint ).addLocal( -1.0f, -1.0f, -1.0f );
			aPlane = CSGPlaneFlt.fromPoints( aPoint, bPoint, cPoint, pTempVars, pEnvironment );
		}
		return( aPlane );
	}

	/** A plane is defined by its 'normal' */
	protected VectorT		mSurfaceNormal;
	/** An arbitrary point on the plane */
	protected VectorT		mPointOnPlane;
	/** Quick access to the 'dot' value */
//	protected float/double	mDot;
	/** Arbitrary 'mark' value for external users of a given plane */
	protected int			mMark;
	
	/** Ensure we have something valid */
	public abstract boolean isValid();
	
	/** Return a copy */
	public abstract CSGPlane clone(
		boolean		pFlipIt
	);
	
	/** Accessor to the normal */
	public VectorT getNormal() { return mSurfaceNormal; }
	
	/** Accessor to the 'dot' value */
//	public float/double getDot() { return mDot; }
	
	/** Accessor to the mark value */
	public int getMark() { return mMark; }
	public void setMark( int pMarkValue ) { mMark = pMarkValue; }
	
	
}
