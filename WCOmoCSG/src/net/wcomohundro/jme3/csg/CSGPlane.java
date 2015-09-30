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
	
	An important aspect of a plane is its ability to 'split' a given Polygon based on if the 
	polygon is in front of, behind, crosses, or lies on the plane itself. The polygon can then be
	included in an appropriate list of similar polygons.  In particular, a polygon that crosses the
	plane can be broken into two parts, the new polygon in front of the plane and the new polygon
	behind the plane.
	
	From the BSP FAQ paper: @see ftp://ftp.sgi.com/other/bspfaq/faq/bspfaq.html
-------------------------------------------------------------------------------------------------------
HOW DO YOU PARTITION A POLYGON WITH A PLANE?

Overview
	Partitioning a polygon with a plane is a matter of determining which side of the plane the polygon 
	is on. This is referred to as a front/back test, and is performed by testing each point in the 
	polygon against the plane. If all of the points lie to one side of the plane, then the entire 
	polygon is on that side and does not need to be split. If some points lie on both sides of the 
	plane, then the polygon is split into two or more pieces.

	The basic algorithm is to loop across all the edges of the polygon and find those for which one 
	vertex is on each side of the partition plane. The intersection points of these edges and 
	the plane are computed, and those points are used as new vertices for the resulting pieces.

Implementation notes
	Classifying a point with respect to a plane is done by passing the (x, y, z) values of the 
	point into the plane equation, Ax + By + Cz + D = 0. The result of this operation is the 
	distance from the plane to the point along the plane's normal vector. It will be positive 
	if the point is on the side of the plane pointed to by the normal vector, negative otherwise. 
	If the result is 0, the point is on the plane.

	For those not familiar with the plane equation, The values A, B, and C are the coordinate 
	values of the normal vector. D can be calculated by substituting a point known to be on 
	the plane for x, y, and z.

	Convex polygons are generally easier to deal with in BSP tree construction than concave 
	ones, because splitting them with a plane always results in exactly two convex pieces. 
	Furthermore, the algorithm for splitting convex polygons is straightforward and robust. 
	Splitting of concave polygons, especially self intersecting ones, is a significant problem 
	in its own right.
------------------------------------------------------------------------------------------------------	
	
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
	protected VectorT	mSurfaceNormal;
	/** An arbitrary point on the plane */
	protected VectorT	mPointOnPlane;
	/** Arbitrary 'mark' value for external users of a given plane */
	protected int		mMark;
	
	/** Ensure we have something valid */
	public abstract boolean isValid();
	
	/** Return a copy */
	public abstract CSGPlane clone(
		boolean		pFlipIt
	);
	
	/** Accessor to the normal */
	public VectorT getNormal() { return mSurfaceNormal; }
	
	/** Accessor to the mark value */
	public int getMark() { return mMark; }
	public void setMark( int pMarkValue ) { mMark = pMarkValue; }
	
	
}
