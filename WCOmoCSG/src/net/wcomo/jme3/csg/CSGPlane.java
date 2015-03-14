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
package net.wcomo.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;

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
	
	NOTE
		that a plane is expected to be immutable with no inernally moving parts.  Once created, you
		cannot alter its normal or dot.  Therefore, a clone of a plane is just the plane itself.
 */
public class CSGPlane 
	implements Savable 
{
	/** Define a 'tolerance' for when two planes are so close, they are effectively the same */
	public static final double EPSILON = 1e-5;
	
	/** Factory method to produce a plane from a minimal set of points */
	public static CSGPlane fromPoints(
		Vector3f	pA
	, 	Vector3f 	pB
	, 	Vector3f 	pC
	) {
		// Compute the normal vector
		Vector3f aNormal = pB.subtract( pA ).cross( pC.subtract( pA ) ).normalizeLocal();
		return new CSGPlane( aNormal, aNormal.dot( pA ) );
	}
	/** Factory method to produce a plane from a set of vertices */
	public static CSGPlane fromVertices(
		List<CSGVertex>		pVertices
	) {
		if ( pVertices.size() >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3f aVector = pVertices.get(0).getPosition();
			Vector3f bVector = pVertices.get(1).getPosition();
			Vector3f cVector = pVertices.get(2).getPosition();
			return( fromPoints( aVector, bVector, cVector ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}

	/** A plane is defined by its 'normal' */
	protected Vector3f	mNormal;
	/** Quick access to its DOT for comparison purposes */
	protected float		mDot;
	
	/** Standard null constructor */
	public CSGPlane(
	) {
		this( Vector3f.ZERO, 0f );
	}
	/** Internal constructor for a given normal and dot */
	protected CSGPlane(
		Vector3f	pNormal
	,	float		pDot
	) {
		mNormal = pNormal;
		mDot = pDot;
	}
	
	/** Return a copy */
	public CSGPlane clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Flipped copy
			return( new CSGPlane( mNormal.negate(), -mDot ) );
		} else {
			// Standard use of this immutable copy
			return( this );
		}
	}
	
	/** Provide a service that knows how to assign a given polygon to an appropriate 
	 	positional list based on its relationship to this plane.
	 	
	 	The options are:
	 		COPLANAR - the polygon is in the same plane as this one (within a given tolerance)
	 		FRONT - the polygon is in front of this plane
	 		BACK - the polygon is in back of this plane
	 		SPANNING - the polygon crosses the plane
	 		
	 	The resultant position is:
	 		COPLANER - 	same plane, but front/back based on which way it is facing
	 		FRONT -		front list
	 		BACK -		back list
	 		SPANNING - 	the polygon is split into two new polygons, with piece in front going
	 					into the front list, and the piece in back going into the back list
	 */
	private static final int COPLANAR = 0;
	private static final int FRONT = 1;
	private static final int BACK = 2;
	private static final int SPANNING = 3;
	public void splitPolygon(
		CSGPolygon 			pPolygon
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	) {
		// A note from the web about what "dot" means in 3D graphics
		// 		When deciding if a polygon is facing the camera, you need only calculate the dot product 
		// 		of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
		// 		vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
		// 		value is greater than zero, it is facing away from the camera. 
		List<CSGVertex> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		
		int polygonType = 0;
		int[] polygonTypes = new int[ vertexCount ];
		
		for( int i = 0; i < vertexCount; i += 1 ) {
			// How far away from this plane is the vertex?
			Vector3f aVertex = polygonVertices.get( i ).getPosition();
			float distanceToPlane = mNormal.dot( aVertex ) - mDot;
			
			// If within a given tolerance, it is the same plane
			int type = (distanceToPlane < -EPSILON) ? BACK : (distanceToPlane > EPSILON) ? FRONT : COPLANAR;
			polygonType |= type;
			polygonTypes[i] = type;
		}
		switch( polygonType ) {
		case COPLANAR:
			// The given polygon lies in this same plane, which way is it facing?
			(mNormal.dot( pPolygon.getPlane().mNormal ) > 0 ? pCoplanarFront : pCoplanarBack).add( pPolygon );
			break;
			
		case FRONT:
			// The given polygon is in front of this plane
			pFront.add( pPolygon );
			break;
			
		case BACK:
			// The given polygon is behind this plane
			pBack.add( pPolygon );
			break;
			
		case SPANNING:
			// The given polygon crosses this plane
			List<CSGVertex> beforeVertices = new ArrayList<CSGVertex>( vertexCount );
			List<CSGVertex> behindVertices = new ArrayList<CSGVertex>( vertexCount );
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Compare to 'next' vertex (wrapping around at the end)
				int j = (i + 1) % vertexCount;
				
				int iType = polygonTypes[i];
				int jType = polygonTypes[j];
				CSGVertex iVertex = polygonVertices.get(i);
				CSGVertex jVertex = polygonVertices.get(j);
				
				if ( iType != BACK ) {
					// If not in back, then must be in front
					beforeVertices.add( iVertex );
				}
				if ( iType != FRONT ) {
					// If not in front, then must be behind 
					// NOTE that we need a new clone if it was already added to 'before'
					behindVertices.add( (iType != BACK) ? iVertex.clone(false) : iVertex );
				}
				if ((iType | jType) == SPANNING ) {
					// If we cross the plane, then interpolate a new vertex on this plane
					float percent = (mDot - mNormal.dot( iVertex.getPosition() )) 
									/ mNormal.dot( jVertex.getPosition().subtract( iVertex.getPosition() ));
					CSGVertex onPlane = iVertex.interpolate( jVertex, percent );
					beforeVertices.add( onPlane );
					behindVertices.add( onPlane.clone( false ) );
				}
			}
			if ( beforeVertices.size() >= 3 ) {
				// We have a full shape in front of the plane
				pFront.add( new CSGPolygon( beforeVertices ) );
			}
			if ( behindVertices.size() >= 3 ) {
				// We have a full shape behind the plane
				pBack.add( new CSGPolygon( behindVertices ) );
			}
			break;
		}
	}

	/** Make the Plane 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mNormal, "Normal", Vector3f.ZERO );
		aCapsule.write( mDot, "Dot", 0f );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mDot = aCapsule.readFloat( "Dot", 0f );
		mNormal = (Vector3f)aCapsule.readSavable( "Normal", Vector3f.ZERO );
	}
	
	/** For DEBUG */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mNormal + "(" + mDot + ")" );
	}

}
