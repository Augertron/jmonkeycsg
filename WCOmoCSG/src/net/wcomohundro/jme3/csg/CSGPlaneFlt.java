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
import com.jme3.util.TempVars;

/**  Constructive Solid Geometry (CSG)
 
  	A CSGPlaneFlt is the FLOAT variant of CSGPlane
 */
public class CSGPlaneFlt 
	extends CSGPlane<Vector3f,CSGVertexFlt>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPlaneFltRevision="$Rev: 71 $";
	public static final String sCSGPlaneFltDate="$Date: 2015-08-18 12:48:56 -0500 (Tue, 18 Aug 2015) $";

	/** Factory method to produce a plane from a minimal set of points 
	 * 
	 	TempVars Usage:
	 		vect4
	 		vect5
	 */
	public static CSGPlaneFlt fromPoints(
		Vector3f		pA
	, 	Vector3f 		pB
	, 	Vector3f 		pC
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Compute the normal vector
		Vector3f temp1 = pB.subtract( pA, pTempVars.vect4 );
		Vector3f temp2 = pC.subtract( pA, pTempVars.vect5 );
		Vector3f aNormal = temp1.cross( temp2 ).normalizeLocal();
		//Vector3f aNormal = pB.subtract( pA ).cross( pC.subtract( pA ) ).normalizeLocal();
		
		// I am defintely NOT understanding something here...
		// I had thought that a normalDot of zero was indicating congruent points.  But
		// apparently, the pattern (x, y, 0) (-x, y, 0) (0, 0, z) produces a valid normal
		// but with a normalDot of 0.  So check for a zero normal vector instead,
		// which indicates 3 points in a line
		if ( aNormal.equals( Vector3f.ZERO ) ) {
			// Not a valid normal
			return( null );
		}
		float normalDot = aNormal.dot( pA );
		return new CSGPlaneFlt( aNormal, pA, normalDot, -1, pEnvironment );
	}
	/** Factory method to produce a plane from a set of vertices */
	public static CSGPlaneFlt fromVertices(
		List<CSGVertex>		pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.size() >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3f aVector = ((CSGVertexFlt)pVertices.get(0)).getPosition();
			Vector3f bVector = ((CSGVertexFlt)pVertices.get(1)).getPosition();
			Vector3f cVector = ((CSGVertexFlt)pVertices.get(2)).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}
	public static CSGPlaneFlt fromVertices(
		CSGVertex[]			pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.length >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3f aVector = ((CSGVertexFlt)pVertices[ 0 ]).getPosition();
			Vector3f bVector = ((CSGVertexFlt)pVertices[ 1 ]).getPosition();
			Vector3f cVector = ((CSGVertexFlt)pVertices[ 2 ]).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}

	/** Standard null constructor */
	public CSGPlaneFlt(
	) {
		mSurfaceNormal = Vector3f.ZERO;
		mPointOnPlane = Vector3f.ZERO;
		mDot = Double.NaN;
		mMark = -1;
	}
	/** Constructor based on a given normal and point on the plane */
	public CSGPlaneFlt(
		Vector3f	pNormal
	,	Vector3f	pPointOnPlane
	) {
		// From the BSP FAQ paper, the 'D' value is calculated from the normal and a point on the plane.
		this( pNormal, pPointOnPlane, pNormal.dot( pPointOnPlane ), -1, CSGEnvironment.sStandardEnvironment );
	}
	/** Internal constructor for a given normal and dot */
	protected CSGPlaneFlt(
		Vector3f		pNormal
	,	Vector3f		pPointOnPlane
	,	double			pDot
	,	int				pMarkValue
	,	CSGEnvironment	pEnvironment
	) {
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			// Remember that NaN always returns false for any comparison, so structure the logic accordingly
			float normalLength = pNormal.length();
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
	public CSGPlaneFlt clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Flipped copy
			return( new CSGPlaneFlt( mSurfaceNormal.negate(), mPointOnPlane, -mDot, -1, null ) );
		} else {
			// Standard use of this immutable copy
			return( this );
		}
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane  */
	public float pointDistance(
		Vector3f	pPoint
	) {
		// How far away is the given point
		float distanceToPlane = mSurfaceNormal.dot( pPoint ) - (float)mDot;
		return( distanceToPlane );
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane.
	 	@return -  -1 if behind
	 				0 if on the plane
	 			   +1 if in front
	 */
	public int pointPosition(
		Vector3f	pPoint
	,	double		pTolerance
	) {
		// How far away is the given point
		float distanceToPlane = mSurfaceNormal.dot( pPoint ) - ((float)mDot);
		
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
		float aFactor = pPoint.dot( mSurfaceNormal ) - ((float)mDot);
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
		CSGPolygon	 		pPolygon
	,	double				pTolerance
	,	int					pHierarchyLevel
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		List<CSGVertexFlt> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		CSGPlaneFlt polygonPlane = (CSGPlaneFlt)pPolygon.getPlane();
		
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
		float[] vertexDot = null;
		
		// Which way is the polygon facing?
		List<CSGPolygon> coplaneList 
			= (mSurfaceNormal.dot( polygonPlane.mSurfaceNormal ) > 0) ? pCoplanarFront : pCoplanarBack;
		
		// NOTE that CSGPlane.equals() checks for near-misses
		if ( polygonPlane == this ) {
			polygonType = SAMEPLANE;
		} else if ( polygonPlane.equals( this, pTolerance ) ) { //pEnvironment.mEpsilonOnPlane ) ) {
			// By definition, we are close enough to be in the same plane
			polygonType = COPLANAR;
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
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
					, pEnvironment.mShapeName + "Bogus Vertex: " + aVertexPosition );					
				}
			}
		}
		switch( polygonType ) {
		case SAMEPLANE:
			// The given polygon lies in this exact same plane
			pCoplanarFront.add( pPolygon );
			break;

		case COPLANAR:
			// Force the polygon onto the plane as needed (working from a mutable copy of the poly list)
			List<CSGVertex> polygonCopy = new ArrayList<CSGVertex>( polygonVertices );
			int coplaneCount
				= CSGPolygonFlt.addPolygon( coplaneList, polygonCopy, this, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );
			if ( coplaneCount < 1 ) {
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
				, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
				return( vertexCount );
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
				CSGVertexFlt iVertex = polygonVertices.get(i);
				CSGVertexFlt jVertex = polygonVertices.get(j);
				
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
					Vector3f pointA = iVertex.getPosition();
					Vector3f pointB = jVertex.getPosition();
					Vector3f intersectPoint 
						= intersectLine( pointA, pointB, pTempVars.vect2, pEnvironment );
					float percentage = intersectPoint.distance( pointA ) / pointB.distance( pointA );
					
					CSGVertexFlt onPlane = iVertex.interpolate( jVertex
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
/***
			// What comes in front of the given plane?
			CSGPolygon beforePolygon
				= CSGPolygon.createPolygon( beforeVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );

			// What comes behind the given plane?
			CSGPolygon behindPolygon
				= CSGPolygon.createPolygon( behindVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );
			
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
/***** when operating on non-triangular polygons
			if ( beforePolygon != null ) {
				pFront.add( beforePolygon  );
			}
			if ( behindPolygon != null ) {
				pBack.add( behindPolygon );
			}
			if ( beforePolygon == null ) {
				if ( behindPolygon == null ) {
					// We did not split the polygon at all, treat it as COPLANAR
					polygonCopy = new ArrayList<CSGVertexFlt>( polygonVertices );
					pPolygon = CSGPolygon.createPolygon( polygonCopy, this, pPolygon.getMaterialIndex()
														, pTempVars, pEnvironment );
					if ( pPolygon != null ) {
						pCoplanarFront.add( pPolygon );
						ConstructiveSolidGeometry.sLogger.log( Level.INFO
						, pEnvironment.mShapeName + "Coopting planar vertices: " + vertexCount );
						return( false );
					}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					//pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					ConstructiveSolidGeometry.sLogger.log( Level.INFO
					, pEnvironment.mShapeName + "Discarding front vertices: " + beforeVertices.size() + "/" + vertexCount );
					return( false );
				}
			} else if ( (behindPolygon == null) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				ConstructiveSolidGeometry.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices: "+ behindVertices.size() + "/" + vertexCount );
				return( false );
			}
****/
/*** when operating on possibly triangular polygons ***/
			int beforeCount
				= CSGPolygonFlt.addPolygon( pFront, beforeVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );

			// What comes behind the given plane?
			int behindCount 
				= CSGPolygonFlt.addPolygon( pBack, behindVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );

			if ( beforeCount == 0 ) {
				if ( behindCount == 0 ) {
					// We did not split the polygon at all, treat it as COPLANAR
					polygonCopy = new ArrayList<CSGVertex>( polygonVertices );
					coplaneCount = CSGPolygonFlt.addPolygon( pCoplanarFront, polygonCopy, this, pPolygon.getMaterialIndex()
														, pTempVars, pEnvironment );
					if ( coplaneCount > 0 ) {
						ConstructiveSolidGeometry.sLogger.log( Level.INFO
						, pEnvironment.mShapeName + "Coopting planar vertices[" + pHierarchyLevel + "] " + vertexCount );
					} else {
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
					, pEnvironment.mShapeName + "Discarding front vertices[" + pHierarchyLevel + "] " + beforeVertices.size() + "/" + vertexCount );
					return( vertexCount - beforeVertices.size() );
				}
			} else if ( (behindCount == 0) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				ConstructiveSolidGeometry.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices[" + pHierarchyLevel + "] " + behindVertices.size() + "/" + vertexCount );
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
	protected Vector3f intersectLine(
		Vector3f		pPointA
	,	Vector3f		pPointB
	,	Vector3f		pTemp
	,	CSGEnvironment	pEnvironment
	) {
		// Use the temp
		Vector3f lineFrag = pPointB.subtract( pPointA, pTemp );
		float nDotA = mSurfaceNormal.dot( pPointA );
		float nDotFrag = mSurfaceNormal.dot( lineFrag );
		
		float distance = (((float)mDot) - nDotA) / nDotFrag;
		lineFrag.multLocal( distance );
		
		// Produce a new vector for subsequent use
		Vector3f intersection = pPointA.add( lineFrag );
		
		if ( pEnvironment.mStructuralDebug ) {
			float confirmDistance = pointDistance( intersection );
			if ( confirmDistance > pEnvironment.mEpsilonNearZero ) {
				// Try to force back onto the plane
				Vector3f pointOnPlane = this.pointProjection( intersection, null );
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
		aCapsule.write( mSurfaceNormal, "Normal", Vector3f.ZERO );
		super.write( pExporter );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mSurfaceNormal = (Vector3f)aCapsule.readSavable( "Normal", Vector3f.ZERO );
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
		} else if ( pOther instanceof CSGPlaneFlt ) {
			// Two planes that are close are equal
			if ( this.mDot != ((CSGPlaneFlt)pOther).mDot ) {
				return( false );
			} else if ( ConstructiveSolidGeometry.equalVector3f( this.mSurfaceNormal
														, ((CSGPlaneFlt)pOther).mSurfaceNormal
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
													, sCSGPlaneFltRevision
													, sCSGPlaneFltDate
													, pBuffer ) );
	}

}
