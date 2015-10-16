/** Copyright (c) ????, Danilo Balby Silva Castanheira (danbalby@yahoo.com)
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
	
	Logic and Inspiration taken from:
		D. H. Laidlaw, W. B. Trumbore, and J. F. Hughes.  
 		"Constructive Solid Geometry for Polyhedral Objects" 
 		SIGGRAPH Proceedings, 1986, p.161. 
**/
package net.wcomohundro.jme3.csg.iob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jme3.math.Vector2f;
import com.jme3.scene.plugins.blender.math.Vector3d;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGPlaneDbl;
import net.wcomohundro.jme3.csg.CSGPolygonDbl;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB.CSGVertexStatus;

/** A "FACE" describes a triangular surface on a solid, as defined by 3 vertices.  In addition,
 	every face tracks its own status about being inside, outside, or on the boundary of another
 	surface.
 	
 	CSGPolygon is used as the basis for a face, limiting its vertex list to 3 vertices, each
 	expected to be a CSGVertexIOB.
 	
 	Since the status of a face can change, cloning will produce new instances.
 */
public class CSGFace 
	extends CSGPolygonDbl
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGFaceRevision="$Rev$";
	public static final String sCSGFaceDate="$Date$";
	
	//private static final double TOL = 1e-10f;


	/** Status of this face in relationship to a solid */
	public static enum CSGFaceStatus {
		UNKNOWN			// Not Yet Classified
	,	INSIDE			// Inside a solid
	,	OUTSIDE			// Outside a solid
	,	SAME 			// Coincident with a solid (same orientation)
	,	OPPOSITE		// Coincident with a solid (opposite orientation)
	}
	
	/** Status of a point in relationship to a line */
	protected static enum CSGPointStatus {
		UP				// Point status if UP relative to an edge
	,	DOWN 			// Point status if DOWN relative to an edge
	,	ON 				// Point status if ON an edge
	,	NONE			// Point status if none of the above
	}
	
	/** Surface collision selector 
	 	When determining how two faces (ie planes) collide - along a line - the
	 	following are the possibilities of how the line travels across the face.
	 	The line will have two collision points, a start and an end.
	 */
	public static enum CSGFaceCollision {
		NONE(0)			// Nothing collided
		
	,	V1(1)			// Intersection with vertex 1
	,	V2(2)			// Intersection with vertex 2
	,	V3(4)			// Intersection with vertex3
	
	,	EDGE12(8)		// Intersection with edge between V1 and V2
	,	EDGE23(16)		// intersection with edge between V2 and V3
	,	EDGE31(32)   	// Intersection with edge between V3 and V1
	
	,	INTERIOR(16)	// Intersection not along an edge or vertex, somewhere in the center
	;
		private int		mValue;
		private CSGFaceCollision( int pValue ) { mValue = pValue; }
		
		/** Given two edges, return the vertex in common */
		public CSGFaceCollision getVertex(
			CSGFaceCollision	pOtherEdge
		) {
			switch( this.mValue + pOtherEdge.mValue ) {
			case 24:	return( V2 );		// EDGE12 + EDGE23
			case 48:	return( V3 );		// EDGE23 + EDGE31
			case 40:	return( V1 );		// EDGE31 + EDGE12
			
			default:	return( NONE );		// no other combination of edges
			}
		}
		
		/** Given a starting collision and ending collision status for a single line,
		 	determine if we are forced to an edge.
		 	An edge results only when the collision points for both start and end
		 	are different vertices;
		 */
		public CSGFaceCollision getEdge(
			CSGFaceCollision	pOther
		) {
			switch( this.mValue + pOther.mValue ) {
			case 3:		return( EDGE12 );	// V1 + V2
			case 6:		return( EDGE23 );	// V2 + V3
			case 5:		return( EDGE31 ); 	// V3 + V1
			
			default:	return( INTERIOR );	// Anything else is not an edge
			}
		}
		
		/** Check if this point (a vertex) is on a given edge */
		public boolean vertexOnEdge(
			CSGFaceCollision	pEdge
		) {
			switch( this ) {
			case V1:
				return( (pEdge == EDGE12) || (pEdge == EDGE31) );
			case V2:
				return( (pEdge == EDGE12) || (pEdge == EDGE23) );
			case V3:
				return( (pEdge == EDGE23) || (pEdge == EDGE31) );
				
			default:
				throw new IllegalArgumentException( "Not a Vertex: " + this );
			}
		}
		
		/** Check for a collision with a vertex */
		public boolean isVertex() { return( (this.mValue <= V3.mValue) && (this.mValue >= V1.mValue) ); }
		
		/** Check for a collision with an edge */
		public boolean isEdge() { return( (this.mValue >= EDGE12.mValue) && (this.mValue <= EDGE31.mValue) ); }
	}
	

	/** Gets the position of a point relative to a line in the x plane */
	protected static CSGPointStatus linePositionInX(
		Vector3d 		pPoint
	, 	Vector3d 		pPointLine1
	, 	Vector3d 		pPointLine2
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonBetweenPointsDbl; // TOL;
		
		if ( (Math.abs( pPointLine1.y - pPointLine2.y ) > tolerance )
		&& ( ((pPoint.y >= pPointLine1.y) && (pPoint.y <= pPointLine2.y))
			 || ((pPoint.y <= pPointLine1.y) && (pPoint.y >= pPointLine2.y)) ) ) {
			double a = (pPointLine2.z - pPointLine1.z) / (pPointLine2.y - pPointLine1.y);
			double b = pPointLine1.z - a * pPointLine1.y;
			double z = a * pPoint.y + b;
			if ( z > pPoint.z + tolerance ) {
				return CSGPointStatus.UP;			
			} else if ( z < pPoint.z - tolerance ) {
				return CSGPointStatus.DOWN;
			} else {
				return CSGPointStatus.ON;
			}
		} else {
			return CSGPointStatus.NONE;
		}
	}
	/** Gets the position of a point relative to a line in the y plane */
	protected static CSGPointStatus linePositionInY(
		Vector3d 		pPoint
	, 	Vector3d 		pPointLine1
	, 	Vector3d 		pPointLine2
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonBetweenPointsDbl; // TOL;

		if ( (Math.abs( pPointLine1.x - pPointLine2.x ) > tolerance)
		&& ( ((pPoint.x >= pPointLine1.x) && (pPoint.x <= pPointLine2.x))
			 || ((pPoint.x <= pPointLine1.x) && (pPoint.x >= pPointLine2.x)) ) ) {
			double a = (pPointLine2.z - pPointLine1.z) / (pPointLine2.x - pPointLine1.x);
			double b = pPointLine1.z - a * pPointLine1.x;
			double z = a * pPoint.x + b;
			if ( z > pPoint.z + tolerance ) {
				return CSGPointStatus.UP;			
			} else if ( z < pPoint.z - tolerance ) {
				return CSGPointStatus.DOWN;
			} else {
				return CSGPointStatus.ON;
			}
		} else {
			return CSGPointStatus.NONE;
		}
	}
	/** Gets the position of a point relative to a line in the z plane */
	protected static CSGPointStatus linePositionInZ(
		Vector3d 		pPoint
	, 	Vector3d 		pPointLine1
	, 	Vector3d 		pPointLine2
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonBetweenPointsDbl; // TOL;

		if ( (Math.abs( pPointLine1.x - pPointLine2.x ) > tolerance )
		&& ( ((pPoint.x >= pPointLine1.x) && (pPoint.x <= pPointLine2.x))
			 || ((pPoint.x <= pPointLine1.x) && (pPoint.x >= pPointLine2.x)) ) ) {
			double a = (pPointLine2.y - pPointLine1.y) / (pPointLine2.x - pPointLine1.x);
			double b = pPointLine1.y - a * pPointLine1.x;
			double y = a * pPoint.x + b;
			if ( y > pPoint.y + tolerance ) {
				return CSGPointStatus.UP;			
			} else if ( y < pPoint.y - tolerance ) {
				return CSGPointStatus.DOWN;
			} else {
				return CSGPointStatus.ON;
			}
		} else {
			return CSGPointStatus.NONE;
		}
	}

	/** Bounds of this face */
	protected CSGBounds			mBounds;
	/** Status of this Face */
	protected CSGFaceStatus		mStatus;

	
	/** Standard null constructor */
	public CSGFace(
	) {
		super( new ArrayList( 3 ), null, 0 );
		mStatus = CSGFaceStatus.UNKNOWN;
	}
	/** Constructor based on a set of vertices */
	public CSGFace(
		CSGVertexIOB 	pV1
	, 	CSGVertexIOB 	pV2
	, 	CSGVertexIOB 	pV3
	, 	int 			pMaterialIndex 
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		super( new ArrayList( 3 ), null, pMaterialIndex );
		mVertices.add( pV1 );
		mVertices.add( pV2 );
		mVertices.add( pV3 );
		
		mStatus = CSGFaceStatus.UNKNOWN;
		
		mPlane = CSGPlaneDbl.fromVertices( mVertices, pTempVars, pEnvironment );
	}
	
	/** Clones the face object */
	public CSGFace clone(
		boolean		pInvert
	) {
		CSGFace aCopy = new CSGFace();
		aCopy.mStatus = this.mStatus;
		aCopy.mMaterialIndex = this.mMaterialIndex;
		
		if ( pInvert ) {
			// Flip the order of the vertices
			aCopy.mVertices.add( v3().clone( pInvert ) );
			aCopy.mVertices.add( v2().clone( pInvert ) );
			aCopy.mVertices.add( v1().clone( pInvert ) );
			
		} else {
			// Simple copy
			aCopy.mVertices.add( v1().clone( pInvert ) );
			aCopy.mVertices.add( v2().clone( pInvert ) );
			aCopy.mVertices.add( v3().clone( pInvert ) );
		}
		aCopy.mPlane = this.mPlane.clone( pInvert );
		return aCopy;
	}
	
	/** OVERRIDE: for debug report */
	@Override
	public String toString(
	) {
		return( "Face:\t" + v1().toString() + "\n\t" + v2().toString() + "\n\t" + v3().toString() );
	}
	
	/**
	 * Checks if a face is equal to another. To be equal, they have to have equal
	 * vertices in the same order
	 * 
	 * @param anObject the other face to be tested
	 * @return true if they are equal, false otherwise. 
	 */
	@Override
	public boolean equals(
		Object	pOtherObject
	) {
		if ( this == pOtherObject ) {
			return( true );
		} else if( pOtherObject instanceof CSGFace ) {
			CSGFace otherFace = (CSGFace)pOtherObject;
			if ( (v1().equals( otherFace.v1() ) && v2().equals( otherFace.v2() ) && v3().equals( otherFace.v3() )) 
			|| (v1().equals( otherFace.v2() ) && v2().equals( otherFace.v3() ) && v3().equals( otherFace.v1() )) 
			|| (v1().equals( otherFace.v3() ) && v2().equals( otherFace.v1() ) && v3().equals( otherFace.v2() )) ) {
				return( true );
			} else {
				return( false );
			} 	 			
		} else {
			return( super.equals( pOtherObject ) );
		}
	}
	
	/** Accessors */
	public int getMaterialIndex() { return mMaterialIndex; }
	
	public CSGVertexIOB v1() { return (CSGVertexIOB)mVertices.get(0); }
	public CSGVertexIOB v2() { return (CSGVertexIOB)mVertices.get(1); }
	public CSGVertexIOB v3() { return (CSGVertexIOB)mVertices.get(2); }
	public List<CSGVertex> getVertices() { return mVertices; }
	public CSGVertexIOB getVertex( int pWhich ) { return (CSGVertexIOB)mVertices.get( pWhich ); }
	
	public void v1( CSGVertexIOB pVertex ) { mVertices.set( 0, pVertex ); }
	public void v2( CSGVertexIOB pVertex ) { mVertices.set( 1, pVertex ); }
	public void v3( CSGVertexIOB pVertex ) { mVertices.set( 2, pVertex ); }
	
	/** Gets the face bound */
	public CSGBounds getBound(
	) {
		if ( mBounds == null ) {
			mBounds = new CSGBounds( this );
		}
		return( mBounds );
	}
	
	/** Gets the face normal */
	public Vector3d getNormal() { return mPlane.getNormal(); }
	
	/** Gets the face status */ 
	public CSGFaceStatus getStatus() { return mStatus; }
	public void resetStatus(
	) {
		if ( mStatus != CSGFaceStatus.UNKNOWN ) {
			mStatus = CSGFaceStatus.UNKNOWN;
			
			// Reset the vertices as well
			v1().setStatus( CSGVertexStatus.UNKNOWN );
			v2().setStatus( CSGVertexStatus.UNKNOWN );
			v3().setStatus( CSGVertexStatus.UNKNOWN );
		}
	}
	

    /** Produce a new Vertex for this face, based upon a given position and
        'collision' status.
        
        ****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
    */
	public CSGVertexIOB extrapolate( 
		Vector3d			pNewPosition
	,	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB newVertex;
		
		// If we are along an edge, the new vertex is a straight forward calculation based
		// on a percentage of the distance the NewPosition is along the given edge.
		switch( pEdge ) {
		case EDGE12:
			newVertex = new CSGVertexIOB( v1(), v2(), pNewPosition, pTempVars, pEnvironment );
			break;
		case EDGE23:
			newVertex = new CSGVertexIOB( v2(), v3(), pNewPosition, pTempVars, pEnvironment );
			break;
		case EDGE31:
			newVertex = new CSGVertexIOB( v3(), v1(), pNewPosition, pTempVars, pEnvironment );
			break;
		default:
			// If not on an edge, then project a point onto an edge, and go from there.
			// For now, intersect V1:V2 with newV:V3
			Vector3d pointOnEdge 
				= CSGRay.lineIntersection( v1().getPosition(), v2().getPosition()
											, pNewPosition, v3().getPosition()
											, pTempVars.vectd6
											, pTempVars, pEnvironment );
			// Figure out the vertex on the edge
			newVertex = new CSGVertexIOB( v1(), v2(), pointOnEdge, pTempVars, pEnvironment );
			
			// Figure out the vertex at the original point
			newVertex = new CSGVertexIOB( newVertex, v3(), pNewPosition, pTempVars, pEnvironment );
			break;
		}
		return( newVertex );
	}
	
	/**  Computes closest distance from a vertex to this face */
	public double computeDistance(
		CSGVertexIOB 		pVertex
	) {
		Vector3d vPosition = pVertex.getPosition();
		return( vPosition.dot( this.getNormal() ) - mPlane.getDot() );
	}
	
	/**  Computes the relative position of a vertex to this face */
	public int computePosition(
		CSGVertexIOB 		pVertex
	,	double				pTolerance
	) {
		Vector3d vPosition = pVertex.getPosition();
		double aDistance = vPosition.dot( this.getNormal() ) - mPlane.getDot();
		if ( aDistance > pTolerance ) return 1;
		else if ( aDistance < -pTolerance ) return -1;
		else return 0;
	}


	//------------------------------------CLASSIFIERS-------------------------------//
	
	/** Classifies the face if one of its vertices are classified as INSIDE or OUTSIDE
	 
	 	@return true if the face could be classified, false otherwise 
	 */
	public boolean simpleClassify(
	) {
		CSGVertexStatus status1 = v1().getStatus();
		CSGVertexStatus status2 = v2().getStatus();
		CSGVertexStatus status3 = v3().getStatus();
			
		if ( (status1 == CSGVertexStatus.INSIDE) 
		|| (status2 == CSGVertexStatus.INSIDE) 
		|| (status3 == CSGVertexStatus.INSIDE) ) {
			this.mStatus = CSGFaceStatus.INSIDE;
			return true;
		} else if ( (status1 == CSGVertexStatus.OUTSIDE) 
		|| (status2 == CSGVertexStatus.OUTSIDE) 
		|| (status3 == CSGVertexStatus.OUTSIDE) ) {
			this.mStatus = CSGFaceStatus.OUTSIDE;
			return true; 
		} else {
			return false;
		}
	}
	
	/** Classifies the face based on the ray trace technique

 		****** TempVars used:  vectd4, vectd5, vectd6
	 */
	public void rayTraceClassify(
		CSGSolid 		pSolid
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// creating a ray starting starting at the face baricenter going to the normal direction
		Vector3d position1 = v1().getPosition();
		Vector3d position2 = v2().getPosition();
		Vector3d position3 = v3().getPosition();

		Vector3d p0 = pTempVars.vectd4;
		p0.x = (position1.x + position2.x + position3.x) / 3.0;
		p0.y = (position1.y + position2.y + position3.y) / 3.0;
		p0.z = (position1.z + position2.z + position3.z) / 3.0;
		CSGRay ray = new CSGRay( getNormal(), p0 );
		
		boolean itersection;
		double dotProduct, distance; 
		Vector3d intersectionPoint;
		CSGFace closestFace = null;
		double closestDistance; 
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;
									
		do {
			// Assume something touches
			itersection = true;
			closestDistance = Double.MAX_VALUE;
			
			// Match each face in the other solid
			for( CSGFace otherFace : pSolid.getFaces() ) {
				dotProduct = otherFace.getNormal().dot( ray.getDirection() );
				intersectionPoint = ray.computePlaneIntersection( otherFace.getNormal()
																, otherFace.getPlane().getDot()
																, pTempVars.vectd5
																, pTempVars
																, pEnvironment );
								
				// Check if ray intersects the plane...  
				if ( intersectionPoint != null ) {
					distance = ray.computePointToPointDistance( intersectionPoint
																, pTempVars
																, pEnvironment);
					
					// Check if the ray lies in plane...
					if ( (Math.abs(distance) < tolerance) && (Math.abs( dotProduct ) < tolerance) ) {
						// Disturb the ray in order to not lie into another plane 
						ray.perturbDirection();
						itersection = false;	// Try the test again
						break;
					}
					// Check if the ray starts in plane...
					if ( (Math.abs( distance ) < tolerance) && (Math.abs( dotProduct ) > tolerance) ) {
						// Check if the ray intersects the face...
						if ( otherFace.hasPoint( intersectionPoint, pEnvironment ) ) {
							// The faces do touch
							closestFace = otherFace;
							closestDistance = 0;
							break;
						}
					} else if ( (Math.abs( dotProduct ) > tolerance) && (distance > tolerance) ) {
						// The ray intersects the plane
						if( distance < closestDistance ) {
							// Check if the ray intersects the face;
							if ( otherFace.hasPoint( intersectionPoint, pEnvironment ) ) {
								// This face is now the closest
								closestDistance = distance;
								closestFace = otherFace;
							}
						}
					}
				}
			}
		} while( itersection == false );
		
		// If no face found: outside face
		if ( closestFace == null ) {
			mStatus = CSGFaceStatus.OUTSIDE;
		} else {
			// If a face was found, then the DOT tells us which side
			dotProduct = closestFace.getNormal().dot( ray.getDirection() );
			
			// If distance = 0: coplanar faces
			if ( Math.abs( closestDistance ) < tolerance ) {
				if ( dotProduct > tolerance ) {
					mStatus = CSGFaceStatus.SAME;
				} else if ( dotProduct < -tolerance ) {
					mStatus = CSGFaceStatus.OPPOSITE;
				}
			}
			// If dot product > 0 (same direction): inside face
			else if ( dotProduct > tolerance ) {
				mStatus = CSGFaceStatus.INSIDE;
			}
			// If dot product < 0 (opposite direction): outside face
			else if ( dotProduct < -tolerance ) {
				mStatus = CSGFaceStatus.OUTSIDE;
			}
		}
	}
	
	//------------------------------------PRIVATES----------------------------------//
	
	/**
	 * Checks if the the face contains a point
	 * 
	 * @param point to be tested
	 * @param true if the face contains the point, false otherwise 
	 */	
	private boolean hasPoint(
		Vector3d 		pPoint
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonBetweenPointsDbl; // TOL;
		
		CSGPointStatus result1, result2, result3;
		Vector3d normal = getNormal(); 
	
		// Check if x is constant...	
		if ( Math.abs(normal.x) > tolerance )  {
			// tests on the x plane
			result1 = linePositionInX( pPoint, v1().getPosition(), v2().getPosition(), pEnvironment );
			result2 = linePositionInX( pPoint, v2().getPosition(), v3().getPosition(), pEnvironment );
			result3 = linePositionInX( pPoint, v3().getPosition(), v1().getPosition(), pEnvironment );
		}
		// Check if y is constant...
		else if( Math.abs(normal.y) > tolerance ) {
			// tests on the y plane
			result1 = linePositionInY( pPoint, v1().getPosition(), v2().getPosition(), pEnvironment );
			result2 = linePositionInY( pPoint, v2().getPosition(), v3().getPosition(), pEnvironment );
			result3 = linePositionInY( pPoint, v3().getPosition(), v1().getPosition(), pEnvironment );
		} else {
			// tests on the z plane
			result1 = linePositionInZ( pPoint, v1().getPosition(), v2().getPosition(), pEnvironment );
			result2 = linePositionInZ( pPoint, v2().getPosition(), v3().getPosition(), pEnvironment );
			result3 = linePositionInZ( pPoint, v3().getPosition(), v1().getPosition(), pEnvironment );
		}
		// Check if the point is up and down two lines...		
		if( ((result1 == CSGPointStatus.UP)
				||(result2 == CSGPointStatus.UP)
				||(result3 == CSGPointStatus.UP))
		&& ((result1 == CSGPointStatus.DOWN)
				|| (result2 == CSGPointStatus.DOWN)
				|| (result3 == CSGPointStatus.DOWN)) ) {
			return true;
		}
		// Check if the point is on of the lines...
		else if ( (result1== CSGPointStatus.ON) 
		|| (result2 == CSGPointStatus.ON) 
		|| (result3 == CSGPointStatus.ON) ) {
			return true;
		} else {
			return false;
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGFaceRevision
													, sCSGFaceDate
													, pBuffer ) );
	}

}
