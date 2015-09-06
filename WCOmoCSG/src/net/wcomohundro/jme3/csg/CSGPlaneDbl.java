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
import net.wcomohundro.jme3.math.Vector3d;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector2f;

/**  Constructive Solid Geometry (CSG)
 
  	A CSGPlaneDbl is the DOUBLE variant of CSGPlane
 */
public class CSGPlaneDbl 
	extends CSGPlane<Vector3d,CSGVertexDbl>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPlaneDblRevision="$Rev: 71 $";
	public static final String sCSGPlaneDblDate="$Date: 2015-08-18 12:48:56 -0500 (Tue, 18 Aug 2015) $";

	/** Factory method to produce a plane from a minimal set of points 
	 * 
	 	TempVars Usage:
	 		vect4
	 		vect5
	 */
	public static CSGPlaneDbl fromPoints(
		Vector3d		pA
	, 	Vector3d 		pB
	, 	Vector3d 		pC
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Compute the normal vector
		Vector3d temp1 = pB.subtract( pA, pTempVars.vectd4 );
		Vector3d temp2 = pC.subtract( pA, pTempVars.vectd5 );
		Vector3d aNormal = temp1.cross( temp2 ).normalizeLocal();
		//Vector3d aNormal = pB.subtract( pA ).cross( pC.subtract( pA ) ).normalizeLocal();
		
		// I am defintely NOT understanding something here...
		// I had thought that a normalDot of zero was indicating congruent points.  But
		// apparently, the pattern (x, y, 0) (-x, y, 0) (0, 0, z) produces a valid normal
		// but with a normalDot of 0.  So check for a zero normal vector instead, which
		// indicates all points on a straight line
		if ( aNormal.equals( Vector3d.ZERO ) ) {
			// Not a valid normal
			return( null );
		}
		double normalDot = aNormal.dot( pA );
		CSGPlaneDbl aPlane = new CSGPlaneDbl( aNormal, pA, normalDot, -1, pEnvironment );
		if ( pEnvironment.mStructuralDebug ) {
			double aDistance = aPlane.pointDistance( pA );
			double bDistance = aPlane.pointDistance( pB );
			double cDistance = aPlane.pointDistance( pC );
			if ( aDistance + bDistance + cDistance > pEnvironment.mEpsilonNearZero ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
				, pEnvironment.mShapeName + "Points NOT on plane: " + aPlane );				
			}
		}
		return( aPlane );
	}
	/** Factory method to produce a plane from a set of vertices */
	public static CSGPlaneDbl fromVertices(
		List<CSGVertex>		pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.size() >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3d aVector = ((CSGVertexDbl)pVertices.get(0)).getPosition();
			Vector3d bVector = ((CSGVertexDbl)pVertices.get(1)).getPosition();
			Vector3d cVector = ((CSGVertexDbl)pVertices.get(2)).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}
	public static CSGPlaneDbl fromVertices(
		CSGVertex[]			pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.length >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3d aVector = ((CSGVertexDbl)pVertices[ 0 ]).getPosition();
			Vector3d bVector = ((CSGVertexDbl)pVertices[ 1 ]).getPosition();
			Vector3d cVector = ((CSGVertexDbl)pVertices[ 2 ]).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}

	/** Standard null constructor */
	public CSGPlaneDbl(
	) {
		mSurfaceNormal = Vector3d.ZERO;
		mPointOnPlane = Vector3d.ZERO;
		mDot = Double.NaN;
		mMark = -1;
	}
	/** Constructor based on a given normal and point on the plane */
	public CSGPlaneDbl(
		Vector3d	pNormal
	,	Vector3d	pPointOnPlane
	) {
		// From the BSP FAQ paper, the 'D' value is calculated from the normal and a point on the plane.
		this( pNormal, pPointOnPlane, pNormal.dot( pPointOnPlane ), -1, CSGEnvironment.sStandardEnvironment );
	}
	/** Internal constructor for a given normal and dot */
	protected CSGPlaneDbl(
		Vector3d		pNormal
	,	Vector3d		pPointOnPlane
	,	double			pDot
	,	int				pMarkValue
	,	CSGEnvironment	pEnvironment
	) {
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			// Remember that NaN always returns false for any comparison, so structure the logic accordingly
			double normalLength = pNormal.length();
			if ( !(
			   (Math.abs( pNormal.x ) <= 1) && (Math.abs( pNormal.y ) <= 1) && (Math.abs( pNormal.z ) <= 1) 
			&& (normalLength < 1.0f + pEnvironment.mEpsilonNearZero) 
			&& (normalLength > 1.0f - pEnvironment.mEpsilonNearZero)
			&& Double.isFinite( pDot )
			) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
				, pEnvironment.mShapeName + "Bogus Plane: " + pNormal + ", " + normalLength + ", " + pDot );
				pDot =  Double.NaN;
			}
		}
		mSurfaceNormal = pNormal;
		mPointOnPlane = pPointOnPlane;
		mDot = pDot;
		mMark = pMarkValue;
	}
	
	/** Return a copy */
	public CSGPlaneDbl clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Flipped copy
			return( new CSGPlaneDbl( mSurfaceNormal.negate(), mPointOnPlane, -mDot, -1, null ) );
		} else {
			// Standard use of this immutable copy
			return( this );
		}
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane  */
	public double pointDistance(
		Vector3d	pPoint
	) {
		// How far away is the given point
		double distanceToPlane = mSurfaceNormal.dot( pPoint ) - (double)mDot;
		return( distanceToPlane );
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane.
	 	@return -  -1 if behind
	 				0 if on the plane
	 			   +1 if in front
	 */
	public int pointPosition(
		Vector3d	pPoint
	,	double		pTolerance
	) {
		// How far away is the given point
		double distanceToPlane = mSurfaceNormal.dot( pPoint ) - mDot;
		
		// If within a given tolerance, it is the same plane
		int aPosition = (distanceToPlane < -pTolerance) ? -1 : (distanceToPlane > pTolerance) ? 1 : 0;
		return( aPosition );
	}
	
	/** Find the projection of a given point onto this plane */
	public Vector3d pointProjection(
		Vector3d	pPoint
	,	Vector3d	pPointStore
	) {
		// Digging around the web, I found the following:
		//		q_proj = q - dot(q - p, n) * n
		// And
		//		a = point_x*normal_dx + point_y*normal_dy + point_z*normal_dz - c;
		//		planar_x = point_x - a*normal_dx;
		//		planar_y = point_y - a*normal_dy;
		//		planar_z = point_z - a*normal_dz;
		double aFactor = pPoint.dot( mSurfaceNormal ) - mDot;
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
	 					
	 	TempVars usage:
	 		-- Span Processing --
		 		vect1
		 		vect2
		 		vect2d
		 	-- CSGPolygon.createPolygon -- 
		 		vect5
	 			vect6

	 */
	@Override
	public int splitPolygon(
		CSGPolygon 			pPolygon
	,	double				pTolerance
	,	int					pHierarchyLevel
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		List<CSGVertexDbl> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		CSGPlaneDbl polygonPlane = (CSGPlaneDbl)pPolygon.getPlane();
		
		if ( !pPolygon.isValid() ) {
			// This polygon is not playing the game properly, ignore it
			return( vertexCount );
		}
		// A note from the web about what "dot" means in 3D graphics
		// 		When deciding if a polygon is facing the camera, you need only calculate the dot product 
		// 		of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
		// 		vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
		// 		value is greater than zero, it is facing away from the camera. 
		int polygonType = COPLANAR;
		int[] polygonTypes = null;
		double[] vertexDot = null;
		
		// Which way is the polygon facing?
		List<CSGPolygon> coplaneList 
			= (mSurfaceNormal.dot( polygonPlane.mSurfaceNormal ) >= 0) ? pCoplanarFront : pCoplanarBack;
		
		// NOTE that CSGPlane.equals() checks for near-misses
		//		I am going to try suppressing the check on the plane to account for those
		//		polygons that may be 'using' a given plane without being exactly on it....
		if ( false ) { //polygonPlane == this ) {
			polygonType = SAMEPLANE;
//		} else if ( polygonPlane.equals( this, 0 ) ) { // pTolerance ) ) { //pEnvironment.mEpsilonOnPlane ) ) {
//			// By definition, we are close enough to be in the same plane
//			polygonType = COPLANAR;
		} else { 
			// Check every vertex against the plane
			polygonTypes = new int[ vertexCount ];
			vertexDot = new double[ vertexCount ];
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Where is this vertex in relation to the plane?
				// Compare the vertex dot to the inherent plane dot
				Vector3d aVertexPosition = polygonVertices.get( i ).getPosition();
				double aVertexDot = vertexDot[i] = (mSurfaceNormal.dot( aVertexPosition ) - mDot);
				if ( Double.isFinite( aVertexDot ) ) {
					// If within a given tolerance, it is the same plane
					// See the discussion from the BSP FAQ paper about the distance of a point to the plane
					//int type = (aVertexDot < -pTolerance) ? BACK : (aVertexDot > pTolerance) ? FRONT : COPLANAR;
					int type;
					if ( (aVertexDot < 0.0) && (aVertexDot < -pEnvironment.mEpsilonNearZero) ) {
						// Somewhere in the back
						type = (aVertexDot < -pTolerance) ? BACK : BACKISH;
					} else if ( (aVertexDot > 0.0) && (aVertexDot > pEnvironment.mEpsilonNearZero) ) {
						// Somewhere in the front
						type = (aVertexDot > pTolerance) ? FRONT : FRONTISH;
					} else {
						type = COPLANAR;
					}
					polygonType |= type;
					polygonTypes[i] = type;
				} else {
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
					, pEnvironment.mShapeName + "Bogus Vertex: " + aVertexPosition );					
				}
			}
			if ( pEnvironment.mStructuralDebug && (polygonPlane == this) && (polygonType != COPLANAR) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
				, pEnvironment.mShapeName + "Bogus polygon plane[" + pHierarchyLevel + "] " + polygonType );				
			}
		}
		switch( polygonType ) {
		default:
			ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
			, pEnvironment.mShapeName + "Bogus polygon split[" + pHierarchyLevel + "] " + polygonType );
			return( vertexCount );

		case SAMEPLANE:
			// The given polygon lies in this exact same plane
			coplaneList.add( pPolygon );
			break;

		case COPLANAR:
		case FRONTISH:
			// If coplanar, then we are for-sure on the plane.  But if strictly frontish, then we
			// are very close, so treat it like being on the plane as well.  This should prevent us
			// for subsequent split processing on the front.
			int coplaneCount
				= CSGPolygonDbl.addPolygon( coplaneList, pPolygon, pTempVars, pEnvironment );
			if ( coplaneCount < 1 ) {
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
				, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
				return( vertexCount );
			}
			break;
			
		case FRONT:
		case FRONT | FRONTISH:
			// The given polygon is in front of this plane
			pFront.add( pPolygon );
			break;
			
		case BACK:
		case BACKISH:
		case BACK | BACKISH:
			// The given polygon is behind this plane
			// (and if frontish, not far enough in front to make a difference)
			pBack.add( pPolygon );
			break;
			
		case FRONT | BACK:
		case FRONT | BACK | FRONTISH:
		case FRONT | BACK | BACKISH:
		case FRONT | BACK | FRONTISH | BACKISH:
			
		case FRONT | BACKISH:
		case FRONT | FRONTISH | BACKISH:
			
		case BACK | FRONTISH:
		case BACK | BACKISH | FRONTISH:
			
		case FRONTISH | BACKISH:

			// The given polygon crosses this plane
			List<CSGVertex> beforeVertices = new ArrayList<CSGVertex>( vertexCount );
			List<CSGVertex> behindVertices = new ArrayList<CSGVertex>( vertexCount );		
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Compare to 'next' vertex (wrapping around at the end)
				int j = (i + 1) % vertexCount;
				
				int iType = polygonTypes[i];
				double iDot = vertexDot[i];
				CSGVertexDbl iVertex = polygonVertices.get(i);
				
				int jType = polygonTypes[j];
				double jDot = vertexDot[j];
				CSGVertexDbl jVertex = polygonVertices.get(j);
				
				switch( iType ) {
				case FRONT:
					// This vertex strictly in front
					beforeVertices.add( iVertex );
					break;
				case BACK:
					// This vertex strictly behind
					behindVertices.add( iVertex );
					break;
				case COPLANAR:
					// This vertex will be included on both sides
					beforeVertices.add( iVertex );
					behindVertices.add( iVertex.clone( false ) );
					break;
				case FRONTISH:
					// Sortof in front, but very close to the plane
					switch( jType ) {
					case FRONT:
					case FRONTISH:
						// Really in front
						beforeVertices.add( iVertex );
						break;
					case COPLANAR:
						// Treat as coplanar
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					case BACK:
						// I am only sortof in front, but the next is really in back
						behindVertices.add( iVertex );
						break;
					case BACKISH:
						// I am sortof in front, the next is sortof in back, but we are
						// both really close to the plane ????
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					}
					break;
				case BACKISH:
					// Sortof in back
					switch( jType ) {
					case BACK:
					case BACKISH:
						// Really in back
						behindVertices.add( iVertex );
						break;
					case COPLANAR:
						// Treat as coplanar
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					case FRONT:
						// I am only sortof in back, the the next is really in front
						beforeVertices.add( iVertex );
						break;
					case FRONTISH:
						// I am sortof in back, the next is sortof in front, but we are
						// both really close to the plane  ????
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					}
					break;
				}
				if ((iType | jType) == (FRONT | BACK) ) {
					// If we cross the plane between these two vertices, then interpolate 
					// a new vertex on this plane itself, which is both before and behind.
					Vector3d pointA = iVertex.getPosition();
					Vector3d pointB = jVertex.getPosition();
					Vector3d intersectPoint 
						= intersectLine( pointA, pointB, pTempVars, pEnvironment );
					double percentage = intersectPoint.distance( pointA ) / pointB.distance( pointA );
					
					CSGVertexDbl onPlane 
						= iVertex.interpolate( jVertex
						, percentage
						, intersectPoint
						, pTempVars
						, pEnvironment );
					if ( onPlane != null ) {
						beforeVertices.add( onPlane );
						behindVertices.add( onPlane.clone( false ) );
					}
				}
			}
			// What comes in front of the plane?
			int beforeCount
				= CSGPolygonDbl.addPolygon( pFront, beforeVertices, pPolygon.getMaterialIndex()
											, pTempVars, pEnvironment );

			// What comes behind the given plane?
			int behindCount 
				= CSGPolygonDbl.addPolygon( pBack, behindVertices, pPolygon.getMaterialIndex()
											, pTempVars, pEnvironment );

			if ( beforeCount == 0 ) {
				if ( behindCount == 0 ) {
					// We did not split the polygon at all, treat it as COPLANAR
					coplaneCount
						= CSGPolygonDbl.addPolygon( coplaneList, pPolygon, pTempVars, pEnvironment );
					if ( coplaneCount < 1 ) {
						ConstructiveSolidGeometry.sLogger.log( Level.WARNING
						, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
						return( vertexCount );
					}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					//pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					ConstructiveSolidGeometry.sLogger.log( Level.INFO
					, pEnvironment.mShapeName + "Discarding front vertices: " + beforeVertices.size() + "/" + vertexCount );
					return( vertexCount - beforeVertices.size() );
				}
			} else if ( (behindCount == 0) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				ConstructiveSolidGeometry.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices: "+ behindVertices.size() + "/" + vertexCount );
				return( vertexCount - behindVertices.size() );
			}
			break;
		}
		return( 0 );
	}
	
	/** Intersection of a line and this plane 
	 	Based on code fragment from: 
	 		http://math.stackexchange.com/questions/83990/line-and-plane-intersection-in-3d
	 */
	protected Vector3d intersectLine(
		Vector3d		pPointA
	,	Vector3d		pPointB
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Use the temp
		Vector3d lineFrag = pPointB.subtract( pPointA, pTempVars.vectd1 );
		double nDotA = mSurfaceNormal.dot( pPointA );
		double nDotFrag = mSurfaceNormal.dot( lineFrag );
		
		double distance = (mDot - nDotA) / nDotFrag;
		lineFrag.multLocal( distance );
		
		// Produce a new vector for subsequent use
		Vector3d intersection = pPointA.add( lineFrag );
		
		if ( pEnvironment.mStructuralDebug ) {
			double confirmDistance = pointDistance( intersection );
			if ( confirmDistance > pEnvironment.mEpsilonNearZero ) {
				// Try to force back onto the plane
				Vector3d pointOnPlane = this.pointProjection( intersection, null );
				intersection = pointOnPlane;
				
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
				, pEnvironment.mShapeName + "line intersect failed: "+ confirmDistance );
			}
		}
		return( intersection );
	}

	/** Make the Plane 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mSurfaceNormal, "Normal", Vector3d.ZERO );
		super.write( pExporter );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mSurfaceNormal = (Vector3d)aCapsule.readSavable( "Normal", Vector3d.ZERO );
		super.read( pImporter );
	}
	
	/** Treat two planes as equal if they happen to be close */
	public boolean equals(
		Object		pOther
	,	double		pTolerance
	) {
		if ( pOther == this ) {
			// By definition, if the plane is the same
			return true;
		} else if ( pOther instanceof CSGPlaneDbl ) {
			// Two planes that are close are equal
			if ( this.mDot != ((CSGPlaneDbl)pOther).mDot ) {
				return( false );
			} else if ( ConstructiveSolidGeometry.equalVector3d( this.mSurfaceNormal
														, ((CSGPlaneDbl)pOther).mSurfaceNormal
														, pTolerance ) ) {
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
													, sCSGPlaneDblRevision
													, sCSGPlaneDblDate
													, pBuffer ) );
	}

}
