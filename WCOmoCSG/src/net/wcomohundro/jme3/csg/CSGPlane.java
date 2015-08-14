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

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;

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
public class CSGPlane 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPlaneRevision="$Rev$";
	public static final String sCSGPlaneDate="$Date$";

	/** Factory method to produce a plane from a minimal set of points */
	public static CSGPlane fromPoints(
		Vector3f	pA
	, 	Vector3f 	pB
	, 	Vector3f 	pC
	) {
		// Compute the normal vector
		Vector3f aNormal = pB.subtract( pA ).cross( pC.subtract( pA ) ).normalizeLocal();
		float normalDot = aNormal.dot( pA );
		if ( normalDot != 0.0f ) {
			return new CSGPlane( aNormal, pA, aNormal.dot( pA ), -1 );
		} else {
			// A normal dot of zero indicates that two of the points overlap, so no plane can be defined
			return( null );
		}
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
	protected Vector3f	mSurfaceNormal;
	/** Quick access to its DOT for comparison purposes */
	protected float		mDot;
	/** An arbitrary point on the plane */
	protected Vector3f	mPointOnPlane;
	/** Arbitrary 'mark' value for external users of a given plane */
	protected int		mMark;
	
	/** Standard null constructor */
	public CSGPlane(
	) {
		mSurfaceNormal = Vector3f.ZERO;
		mPointOnPlane = Vector3f.ZERO;
		mDot = Float.NaN;
		mMark = -1;
	}
	/** Constructor based on a given normal and point on the plane */
	public CSGPlane(
		Vector3f	pNormal
	,	Vector3f	pPointOnPlane
	) {
		// From the BSP FAQ paper, the 'D' value is calculated from the normal and a point on the plane.
		this( pNormal, pPointOnPlane, pNormal.dot( pPointOnPlane ), -1 );
	}
	/** Internal constructor for a given normal and dot */
	protected CSGPlane(
		Vector3f	pNormal
	,	Vector3f	pPointOnPlane
	,	float		pDot
	,	int			pMarkValue
	) {
		// Think about this as an Assert....
		if ( true ) {
			// Remember that NaN always returns false for any comparison, so structure the logic accordingly
			float normalLength = pNormal.length();
			if ( !(
			   (Math.abs( pNormal.x ) <= 1) && (Math.abs( pNormal.y ) <= 1) && (Math.abs( pNormal.z ) <= 1) 
			&& (normalLength < 1.0f + EPSILON_NEAR_ZERO) && (normalLength > 1.0f - EPSILON_NEAR_ZERO)
			&& Float.isFinite( pDot )
			) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Plane: " + pNormal + ", " + normalLength + ", " + pDot );
				pDot =  Float.NaN;
			}
		}
		mSurfaceNormal = pNormal;
		mPointOnPlane = pPointOnPlane;
		mDot = pDot;
		mMark = pMarkValue;
	}
	
	/** Ensure we have something valid */
	public boolean isValid() { return( Float.isFinite( mDot ) ); }
	
	/** Return a copy */
	public CSGPlane clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Flipped copy
			return( new CSGPlane( mSurfaceNormal.negate(), mPointOnPlane, -mDot, -1 ) );
		} else {
			// Standard use of this immutable copy
			return( this );
		}
	}
	
	/** Accessor to the mark value */
	public int getMark() { return mMark; }
	public void setMark( int pMarkValue ) { mMark = pMarkValue; }
	
	/** Check if a given point is in 'front' or 'behind' this plane  */
	public float pointDistance(
		Vector3f	pPoint
	) {
		// How far away is the given point
		float distanceToPlane = mSurfaceNormal.dot( pPoint ) - mDot;
		return( distanceToPlane );
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane.
	 	@return -  -1 if behind
	 				0 if on the plane
	 			   +1 if in front
	 */
	public int pointPosition(
		Vector3f	pPoint
	,	float		pTolerance
	) {
		// How far away is the given point
		float distanceToPlane = mSurfaceNormal.dot( pPoint ) - mDot;
		
		// If within a given tolerance, it is the same plane
		int aPosition = (distanceToPlane < -pTolerance) ? -1 : (distanceToPlane > pTolerance) ? 1 : 0;
		return( aPosition );
	}
	
	/** Find the projection of a given point onto this plane */
	public Vector3f pointProjection(
		Vector3f	pPoint
	,	Vector3f	pPointStore
	) {
		// Digging around the web, I found the following:
		//		q_proj = q - dot(q - p, n) * n
		// And
		//		a = point_x*normal_dx + point_y*normal_dy + point_z*normal_dz - c;
		//		planar_x = point_x - a*normal_dx;
		//		planar_y = point_y - a*normal_dy;
		//		planar_z = point_z - a*normal_dz;
		float aFactor = pPoint.dot( mSurfaceNormal ) - mDot;
		pPointStore = mSurfaceNormal.mult( aFactor, pPointStore );
		pPointStore.set( pPoint.x - pPointStore.x, pPoint.y - pPointStore.y, pPoint.z - pPointStore.z );
		return( pPointStore );
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
	private static final int SAMEPLANE = -1;
	private static final int COPLANAR = 0;
	private static final int FRONT = 1;
	private static final int BACK = 2;
	private static final int SPANNING = 3;
	public boolean splitPolygon(
		CSGPolygon 			pPolygon
	,	float				pTolerance
	,	int					pHierarchyLevel
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	Vector3f			pTemp3f
	,	Vector2f			pTemp2f
	,	CSGEnvironment		pEnvironment
	) {
		if ( !pPolygon.isValid() ) {
			// This polygon is not playing the game properly, ignore it
			return( false );
		}
		// A note from the web about what "dot" means in 3D graphics
		// 		When deciding if a polygon is facing the camera, you need only calculate the dot product 
		// 		of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
		// 		vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
		// 		value is greater than zero, it is facing away from the camera. 
		List<CSGVertex> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		CSGPlane polygonPlane = pPolygon.getPlane();
		
		int polygonType = COPLANAR;
		int[] polygonTypes = null;
		float[] vertexDot = null;
		
		// Which way is the polygon facing?
		List<CSGPolygon> coplaneList 
			= (mSurfaceNormal.dot( polygonPlane.mSurfaceNormal ) > 0) ? pCoplanarFront : pCoplanarBack;
		
		// NOTE that CSGPlane.equals() checks for near-misses
		if ( polygonPlane.equals( this ) ) {
			// By definition, we are in the plane
			polygonType = SAMEPLANE;
		} else {
			// Check every vertex against the plane
			polygonTypes = new int[ vertexCount ];
			vertexDot = new float[ vertexCount ];
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Where is this vertex in relation to the plane?
				// Compare the vertex dot to the inherent plane dot
				Vector3f aVertexPosition = polygonVertices.get( i ).getPosition();
				float aVertexDot = vertexDot[i] = mSurfaceNormal.dot( aVertexPosition );
				aVertexDot -= mDot;
				if ( Float.isFinite( aVertexDot ) ) {
					// If within a given tolerance, it is the same plane
					// See the discussion from the BSP FAQ paper about the distance of a point to the plane
					int type = (aVertexDot < -pTolerance) ? BACK : (aVertexDot > pTolerance) ? FRONT : COPLANAR;
					polygonType |= type;
					polygonTypes[i] = type;
				} else {
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Vertex: " + aVertexPosition );					
				}
			}
		}
		switch( polygonType ) {
		case SAMEPLANE:
			// The given polygon lies in this same plane
			pCoplanarFront.add( pPolygon );
			break;

		case COPLANAR:
			// Force the polygon onto the plane
			CSGPolygon aPolygon = null;
			if ( FORCE_POINT_ON_PLANE ) {
				// Force the polygon to be truely on the plane
				aPolygon
					= CSGPolygon.createPolygon( polygonVertices, this, pPolygon.getMaterialIndex(), pEnvironment );
			} else {
				// Use it as is
				aPolygon = pPolygon;
			}
			if ( aPolygon != null ) {
				coplaneList.add( aPolygon );
			} else {
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
				, "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );				
			}
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
					// If not in back, then must be in front (or COPLANAR)
					beforeVertices.add( iVertex );
				}
				if ( iType != FRONT ) {
					// If not in front, then must be behind (or COPLANAR)
					// NOTE that we need a new clone if it was already added to 'before'
					behindVertices.add( (iType != BACK) ? iVertex.clone( false ) : iVertex );
				}
				if ((iType | jType) == SPANNING ) {
					// If we cross the plane between these two vertices, then interpolate 
					// a new vertex on this plane itself, which is both before and behind.
					pTemp3f.set( jVertex.getPosition() ).subtractLocal( iVertex.getPosition() );
					float percent = (mDot - vertexDot[i]) / mSurfaceNormal.dot( pTemp3f );
					CSGVertex onPlane = iVertex.interpolate( jVertex, percent, this, pTemp3f, pTemp2f );
					if ( onPlane != null ) {
						beforeVertices.add( onPlane );
						behindVertices.add( onPlane.clone( false ) );
					}
				}
			}
			// What comes in front of the given plane?
			CSGPolygon beforePolygon 
				= CSGPolygon.createPolygon( beforeVertices, pPolygon.getMaterialIndex(), pEnvironment );

			// What comes behind the given plane?
			CSGPolygon behindPolygon 
				= CSGPolygon.createPolygon( behindVertices, pPolygon.getMaterialIndex(), pEnvironment );
			
/** Retry the split with more forgiving tolerance
			if ( (beforePolygon == null) && !beforeVertices.isEmpty() ) {
				// Not enough distinct vertices
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
					, "Discarding front vertices[" + pHierarchyLevel + "] " + beforeVertices.size() + "/" + vertexCount );
				behindPolygon = null;
			}
			if ( (behindPolygon == null) && !behindVertices.isEmpty() ) {
				// Not enough distinct vertices
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
					, "Discarding back vertices[" + pHierarchyLevel + "] "+ behindVertices.size() + "/" + vertexCount );
				beforePolygon = null;
			}
			if ( beforePolygon != null ) {
				pFront.add( beforePolygon  );
			}
			if ( behindPolygon != null ) {
				pBack.add( behindPolygon );
			} else if ( beforePolygon == null ) {
				// The polygon did not split well, try again with more tolerance
				splitPolygon(	pPolygon
								,	pTolerance * 2.0f
								,   pHierarchyLevel + 1
								, 	pCoplanarFront
								, 	pCoplanarBack
								, 	pFront
								, 	pBack
								,	pTemp3f
								,	pTemp2f
								,	pEnvironment
								);
				return( false );
			}
**/
			if ( beforePolygon != null ) {
				pFront.add( beforePolygon  );
			}
			if ( behindPolygon != null ) {
				pBack.add( behindPolygon );
			}
			if ( beforePolygon == null ) {
				if ( behindPolygon == null ) {
					// We did not split the polygon at all, treat it as COPLANAR
					//pPolygon = CSGPolygon.createPolygon( polygonVertices, this, pPolygon.getMaterialIndex(), pEnvironment );
					//if ( pPolygon == null ) {
						// I am not quite sure how I would get here
						//return( false );
					//} else {
						pCoplanarFront.add( pPolygon );
						ConstructiveSolidGeometry.sLogger.log( Level.INFO, "Coopting planar vertices: " + vertexCount );
						return( false );
					//}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					ConstructiveSolidGeometry.sLogger.log( Level.INFO, "Coopting front vertices: " + beforeVertices.size() + "/" + vertexCount );
					return( false );
				}
			} else if ( (behindPolygon == null) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				ConstructiveSolidGeometry.sLogger.log( Level.INFO, "Coopting back vertices: "+ behindVertices.size() + "/" + vertexCount );
				return( false );
			}
			break;
		}
		return( true );
	}

	/** Make the Plane 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mSurfaceNormal, "Normal", Vector3f.ZERO );
		aCapsule.write( mDot, "Dot", 0f );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mDot = aCapsule.readFloat( "Dot", 0f );
		mSurfaceNormal = (Vector3f)aCapsule.readSavable( "Normal", Vector3f.ZERO );
	}
	
	/** For DEBUG */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mSurfaceNormal + "(" + mDot + ")" );
	}
	
	/** OVERRIDE to treat two planes as equal if they happen to be close */
	@Override
	public boolean equals(
		Object		pOther
	) {
		if ( pOther == this ) {
			// By definition, if the plane is the same
			return true;
		} else if ( pOther instanceof CSGPlane ) {
			// Two planes that are close are equal
			if ( this.mDot != ((CSGPlane)pOther).mDot ) {
				return( false );
			} else if ( ConstructiveSolidGeometry.equalVector3f( this.mSurfaceNormal
														, ((CSGPlane)pOther).mSurfaceNormal
														, EPSILON_ONPLANE ) ) {
				return( true );
			} else {
				return( false );
			}
		} else {
			// Let the super handle the error case
			return( super.equals( pOther ) );
		}
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGPlaneRevision
													, sCSGPlaneDate
													, pBuffer ) );
	}

}
