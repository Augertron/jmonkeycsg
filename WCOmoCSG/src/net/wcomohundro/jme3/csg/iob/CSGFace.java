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
import java.util.Map;
import java.util.logging.Level;

import com.jme3.math.Transform;
import com.jme3.math.Vector2f;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB.CSGVertexStatus;
import net.wcomohundro.jme3.csg.math.CSGPlaneDbl;
import net.wcomohundro.jme3.csg.math.CSGPolygonDbl;
import net.wcomohundro.jme3.csg.math.CSGVertex;
import net.wcomohundro.jme3.math.Vector3d;

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
		/** Check if this given element is in the set */
		public boolean hasElement(
			int		pElementMask
		) {
			// Mask off the desired bit -- which must result in the desired element being on
			return( (pElementMask & this.mValue) == this.mValue );
		}
		/** Remove this element from the set */
		public int removeElement(
			int		pElementMask
		) {
			pElementMask &= ~this.mValue;
			return( pElementMask );
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
		) throws CSGConstructionException {
			switch( this ) {
			case V1:
				return( (pEdge == EDGE12) || (pEdge == EDGE31) );
			case V2:
				return( (pEdge == EDGE12) || (pEdge == EDGE23) );
			case V3:
				return( (pEdge == EDGE23) || (pEdge == EDGE31) );
				
			default:
				throw new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
												,	"CSGFaceCollision - not a Vertex: " + this );
			}
		}
		
		/** Check for a collision with a vertex */
		public boolean isVertex() { return( (this.mValue <= V3.mValue) && (this.mValue >= V1.mValue) ); }
		
		/** Check for a collision with an edge */
		public boolean isEdge() { return( (this.mValue >= EDGE12.mValue) && (this.mValue <= EDGE31.mValue) ); }
	}
	
	
	/** Bounds of this face */
	protected CSGBounds			mBounds;
	/** Status of this Face */
	protected CSGFaceStatus		mStatus;
	/** Where to start a face-to-face scan */
	protected int				mScanStartIndex;

	
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
	,	int				pMeshIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		super( new ArrayList( 3 ), null, pMeshIndex );
		mStatus = CSGFaceStatus.UNKNOWN;

		mVertices.add( pV1 );
		mVertices.add( pV2 );
		mVertices.add( pV3 );
		
		// Confirm the vertices
		if ( CSGEnvironment
				.equalVector3d( pV1.getPosition(), pV2.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
			|| CSGEnvironment
				.equalVector3d( pV2.getPosition(), pV3.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
			|| CSGEnvironment
				.equalVector3d( pV3.getPosition(), pV1.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )) {
			// Not a meaningful face, do not bother with checking the plane
			if ( pEnvironment.mStructuralDebug ) {
				pEnvironment.log( Level.WARNING, "Degenerate CSGFace: " + this );
			}
			return;
		}
		mPlane = CSGPlaneDbl.fromVertices( mVertices, pTempVars, pEnvironment );
		if ( pEnvironment.mStructuralDebug && (mPlane == null) ) {
			// The given points do not produce a valid plane
			pEnvironment.log( Level.WARNING, "Invalid CSGFace: " + this );
		}			
	}
	public CSGFace(
		CSGVertexIOB 	pV1
	, 	CSGVertexIOB 	pV2
	, 	CSGVertexIOB 	pV3
	,	CSGFace			pSourceFace
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		super( new ArrayList( 3 ), null, pSourceFace.getMeshIndex() );
		mStatus = CSGFaceStatus.UNKNOWN;

		// A new face based on vertices from an old face must clone them
		// to keep the status unique as needed.
		// @todo -  I am still not 100% convinced that a clone is needed here...
		//		    If the vertex represents the same position, shouldn't its 
		//			status always be the same????
		mVertices.add( pV1.clone( false, pEnvironment ) );
		mVertices.add( pV2.clone( false, pEnvironment ) );
		mVertices.add( pV3.clone( false, pEnvironment ) );
		
		// BY DEFINITION, a new face based upon a source face MUST share the same plane
		mPlane = pSourceFace.getPlane();
		if ( pEnvironment.mStructuralDebug ) {
			// Confirm that the given points reside on the plane
			int v1=0, v2=0, v3=0;
			if ( ((v1 = mPlane.pointPosition( pV1, pEnvironment )) != 0)
			||	 ((v2 = mPlane.pointPosition( pV2, pEnvironment )) != 0)
			||	 ((v3 = mPlane.pointPosition( pV3, pEnvironment )) != 0) ) {
				pEnvironment.log( Level.WARNING, "Invalid CSGFace: point " + v1 + v2 + v3 + " not on plane:\n" + this );
				mPlane = null;
			}
		}			
	}
	
	/** Clones the face object */
	@Override
	public CSGFace clone(
		boolean		pInvert
	) {
		return( clone( pInvert, CSGEnvironment.resolveEnvironment() ) );
	}	
	public CSGFace clone(
		boolean			pInvert
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace aCopy = new CSGFace();
		aCopy.mStatus = this.mStatus;
		aCopy.mMeshIndex = this.mMeshIndex;
		aCopy.mScanStartIndex = 0;
		
		if ( pInvert ) {
			// Flip the order of the vertices
			aCopy.mVertices.add( v3().clone( pInvert, pEnvironment ) );
			aCopy.mVertices.add( v2().clone( pInvert, pEnvironment ) );
			aCopy.mVertices.add( v1().clone( pInvert, pEnvironment ) );
			
		} else {
			// Simple copy
			aCopy.mVertices.add( v1().clone( pInvert, pEnvironment ) );
			aCopy.mVertices.add( v2().clone( pInvert, pEnvironment ) );
			aCopy.mVertices.add( v3().clone( pInvert, pEnvironment ) );
		}
		aCopy.mPlane = this.mPlane.clone( pInvert );
		return aCopy;
	}
	
	/** Apply a transform to this face */
	public void applyTransform(
		Transform		pTransform
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Reconfigure the vertices
		v1( v1().applyTransform( pTransform, pTempVars, pEnvironment ) );
		v2( v2().applyTransform( pTransform, pTempVars, pEnvironment ) );
		v3( v3().applyTransform( pTransform, pTempVars, pEnvironment ) );
		
		// Reset the plane
		mPlane = mPlane.applyTransform( pTransform, pTempVars, pEnvironment );
		
		// The bounds must be recalculated
		mBounds = null;
	}
	
	/** Accessor to the scan start point */
	public int getScanStartIndex() { return mScanStartIndex; }
	public void setScanStartIndex( int pIndex ) { mScanStartIndex = pIndex; }
	
	
	/** Match this given face to a list of other vertices, looking for any vertex that
	 	shares its position.  Such vertices can be linked together since their 'status'
	 	will be shared.  We only care about the position.  Vertices with shared
	 	position can still retain their own unique normal/texture.
	 	
	 	NOTE
	 		That an attempt to scan through a list of other faces was too slow to be 
	 		useful.  Therefore, we will live with the overhead of the HashMap.
	 */
	public void matchVertices(
		Map<Vector3d,CSGVertexIOB>	pVertexList
	,	CSGEnvironment				pEnvironment
	) {
		for( CSGVertex aVertex : this.mVertices ) {
			CSGVertexIOB thisVertex = (CSGVertexIOB)aVertex;
			if ( thisVertex.hasSame() ) {
				// This vertex has already been processed and is attached to another vertex
				continue;
			}
			Vector3d thisPosition = thisVertex.getPosition();
			CSGVertexIOB otherVertex = pVertexList.get( thisPosition );
			
			if ( otherVertex == null ) {
				// No overlap, so add this vertex to the list
				pVertexList.put( thisPosition, thisVertex );
			} else {
				// Same position as another vertex
				otherVertex.samePosition( thisVertex );
			}
		}
	}
	/** Match any vertex in this face against a given position */
	public void matchVertex(
		CSGVertexIOB		pOtherVertex
	,	CSGEnvironment		pEnvironment
	) {
		Vector3d aPosition = pOtherVertex.getPosition();
		
		for( CSGVertex aVertex : this.mVertices ) {
			CSGVertexIOB thisVertex = (CSGVertexIOB)aVertex;
			if ( thisVertex.matchPosition( aPosition, pEnvironment ) ) {
				// Link together vertices with the same position
				thisVertex.samePosition( pOtherVertex );
				
				// And we should not match two vertices in the same face
				break;
			}
		}
	}
	/** Look if this faces matches a given position 
	 	(you may not find any 'active' references to this method in the codebase, but
	 	 it is sure handy to have while debugging, looking to match errant triangles)
	 */
	public boolean matchPosition(
		List<Vector3d>	pPosition
	,	CSGEnvironment	pEnvironment
	) {
		// Match every position in any order
		int matchMask = 0;
		for( int i = 0; i < 3; i += 1 ) {
			Vector3d facePosition = ((CSGVertexIOB)mVertices.get(i)).getPosition();
			for( int j = 0, k = 1; j < 3; j += 1, k <<= 1 ) {
				if ( (matchMask & k) == 0 ) {
					Vector3d givenPosition = pPosition.get( j );
					if ( CSGEnvironment.equalVector3d( facePosition
														,	givenPosition
														,	pEnvironment.mEpsilonBetweenPointsDbl ) ) {
						matchMask |= k;
						break;
					}
				}
			}
		}
		// Check what matched
		return( matchMask == 7 );
	}
	
	/** OVERRIDE: for debug report */
	@Override
	public String toString(
	) {
		StringBuilder aBuffer = new StringBuilder( 256 );
		aBuffer.append( "Face:\t" )
				.append( v1().toString() )
				.append( "\n\t" )
				.append( v2().toString() )
				.append( "\n\t" )
				.append( v3().toString() );
		if ( mPlane == null ) {
			aBuffer.append( "\n\t INVALID PLANE" );
		} else {
			aBuffer.append( "\n\t" );
			mPlane.toString( aBuffer );
		}
		return( aBuffer.toString() );
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
		mStatus = CSGFaceStatus.UNKNOWN;
		
		// Reset the vertices as well
		v1().mark( this, null );
		v2().mark( this, null );
		v3().mark( this, null );
	}
	

    /** Produce a new Vertex for this face, based upon a given position and
        'collision' status.
        
        ****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
    */
	public CSGVertexIOB extrapolate( 
		Vector3d			pNewPosition
	,	CSGVertexStatus		pNewStatus
	,	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB newVertex;
		
		// If we are along an edge, the new vertex is a straight forward calculation based
		// on a percentage of the distance the NewPosition is along the given edge.
		switch( pEdge ) {
		case EDGE12:
			newVertex = new CSGVertexIOB( v1(), v2(), pNewPosition, pNewStatus, pTempVars, pEnvironment );
			break;
		case EDGE23:
			newVertex = new CSGVertexIOB( v2(), v3(), pNewPosition, pNewStatus, pTempVars, pEnvironment );
			break;
		case EDGE31:
			newVertex = new CSGVertexIOB( v3(), v1(), pNewPosition, pNewStatus, pTempVars, pEnvironment );
			break;
		default:
			// If not on an edge, then project a point onto an edge, and go from there.
			// For now, intersect V1:V2 with newV:V3
			Vector3d pointOnEdge 
				= CSGRay.lineIntersection( v1().getPosition(), v2().getPosition()
											, pNewPosition, v3().getPosition()
											, pTempVars.vectd6
											, pTempVars, pEnvironment );
			if ( pointOnEdge == null ) {
				// Something not right about this intersection... Possibly things are too close
				if ( pEnvironment.mStructuralDebug ) {
					// NOTE that .lineIntersection() may well have logged this problem as well
					pEnvironment.log( Level.INFO, "CSGFace: no extrapolation: " + pNewPosition + "\n" + this );
				}
				return( null );
			}
			// Figure out the vertex on the edge
			newVertex = new CSGVertexIOB( v1(), v2(), pointOnEdge, null, pTempVars, pEnvironment );
			
			// Figure out the vertex at the original point
			newVertex = new CSGVertexIOB( newVertex, v3(), pNewPosition, pNewStatus, pTempVars, pEnvironment );
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
	
	/** Classifies the face if one of its vertices are classified as INSIDE or OUTSIDE.
	 
	 	Since any faces that overlap have been 'split' to eliminate any such overlap,
	 	then if we find any vertex that is either Inside or Outside, the entire
	 	face can be treated as Inside/Outside.
	 
	 	@return true if the face could be classified, false otherwise 
	 */
	public boolean simpleClassify(
		CSGEnvironment		pEnvironment
	) {
		CSGFaceStatus faceStatus = null;
		CSGVertexStatus status1 = v1().getStatus();
		CSGVertexStatus status2 = v2().getStatus();
		CSGVertexStatus status3 = v3().getStatus();
			
		if ( (status1 == CSGVertexStatus.INSIDE) 
		|| (status2 == CSGVertexStatus.INSIDE) 
		|| (status3 == CSGVertexStatus.INSIDE) ) {
			// Something is on the inside
			faceStatus = CSGFaceStatus.INSIDE;
		} 
		if ( (status1 == CSGVertexStatus.OUTSIDE) 
		|| (status2 == CSGVertexStatus.OUTSIDE) 
		|| (status3 == CSGVertexStatus.OUTSIDE) ) {
			// Something is on the outside
			if ( faceStatus == null ) {
				faceStatus = CSGFaceStatus.OUTSIDE;
			} else {
				// Both inside and outside???
				// Attribute it to rounding issues where two faces are very very
				// close but just different enough
				if ( pEnvironment.mStructuralDebug ) {
					pEnvironment.log( Level.WARNING
									, "CSGFace.simpleClassify - inconsistent vertex status:\n" + this );
				}
				// Once we detect an inconsistency, then all vertices for this face
				// are suspect, and must not influence any subsequent classification
				v1().setStatus( CSGVertexStatus.BOUNDARY, true );
				v2().setStatus( CSGVertexStatus.BOUNDARY, true );
				v3().setStatus( CSGVertexStatus.BOUNDARY, true );
				
				// There is no 'simple' classification for this face
				faceStatus = null;
			}
		}
		if ( faceStatus == null ) {
			// Nothing simple about it
			return false;
		} else {
			// Use what we know
			this.mStatus = faceStatus;
			return true;
		}
	}
	
	/** Classifies the face based on the ray trace technique

 		****** TempVars used:  vectd4, vectd5, vectd6
	 */
	public CSGFaceStatus rayTraceClassify(
		CSGSolid 		pOtherSolid
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) throws CSGConstructionException {
		// creating a ray starting starting at the face baricenter going to the normal direction
		Vector3d position1 = v1().getPosition();
		Vector3d position2 = v2().getPosition();
		Vector3d position3 = v3().getPosition();

		Vector3d p0 = pTempVars.vectd4;
		p0.x = (position1.x + position2.x + position3.x) / 3.0;
		p0.y = (position1.y + position2.y + position3.y) / 3.0;
		p0.z = (position1.z + position2.z + position3.z) / 3.0;
		CSGRay ray = new CSGRay( getNormal(), p0 );
		
		double dotProduct, distance; 
		Vector3d intersectionPoint = null;
		CSGFace closestFace = null;
		double closestDistance; 
		double zeroTolerance = pEnvironment.mEpsilonNearZeroDbl;
		double planeTolerance = pEnvironment.mEpsilonOnPlaneDbl;
				
		int deadmanSwitch = 100;
outer:	while( deadmanSwitch-- > 0 ) {
			// Assume something touches
			closestDistance = Double.MAX_VALUE;
											
			// Match each face in the other solid
			for( CSGFace otherFace : pOtherSolid.getFaces() ) {
				// Allow an outside monitor to abort long running construction
				if ( Thread.interrupted() ) {
					// NOTE use of .interrupted() (which clears the interrupted status) versus
					//		currentThread.isInterrupted()  (which leaves the interrupted status alone)
					CSGConstructionException anError
						= new CSGConstructionException( CSGErrorCode.INTERRUPTED
														,	"CSGFace.rayTraceClassify - interrupted" );
					throw anError;
				}
				double absDotProduct = Math.abs( otherFace.getNormal().dot( ray.getDirection() ) );
				intersectionPoint = ray.computePlaneIntersection( otherFace.getNormal()
																, otherFace.getPlane().getDot()
																, pTempVars.vectd5
																, pTempVars
																, pEnvironment );
								
				// Check if ray intersects the plane, which happens a lot unless the
				// ray is absolutely parallel to the plane
				if ( intersectionPoint != null ) {
					distance = ray.computePointToPointDistance( intersectionPoint
																, pTempVars
																, pEnvironment );
					
					// Check if the ray lies in plane...
					if ( (Math.abs(distance) < planeTolerance) && (absDotProduct < zeroTolerance) ) {
						// Disturb the ray in order to not lie within the other plane 
						ray.perturbDirection();
						
						// Try the test again
						continue outer;
					}
					// Check if the ray starts in plane...
					if ( absDotProduct > zeroTolerance ) {
						// The ray intersects the plane
						if ( Math.abs( distance ) < planeTolerance ) {
							// Check if the ray intersects the other face as well
							if ( otherFace.hasPoint( ray, intersectionPoint, pTempVars, pEnvironment ) ) {
								// The faces do touch, so it must be the closest, no need to check more
								closestFace = otherFace;
								closestDistance = 0;
								break;
							} else {
								// No need to check otherFace.hasPoint() again, which is why
								// the following is part of the ELSE
							}
						} else if ( (distance > 0) && (distance < closestDistance) ) {
							// The ray intersects the plane, facing the same direction
							// Check if the ray intersects the other face;
							if ( otherFace.hasPoint( ray, intersectionPoint, pTempVars, pEnvironment ) ) {
								// This face is now the closest, but keep checking the rest
								closestDistance = distance;
								closestFace = otherFace;
							}						
						}
					}
				}
			}
			// We have matched against every other face		
			if ( closestFace == null ) {
				// If no closest face found: outside face
				mStatus = CSGFaceStatus.OUTSIDE;
			} else {
				// If a face was found, then the DOT tells us which side
				dotProduct = closestFace.getNormal().dot( ray.getDirection() );
				
				// If distance = 0, then coplanar faces
				//  (remembering that only positive distances are tracked in 'closest')
				if ( closestDistance < planeTolerance ) {
					// Same plane, but which way do we face?
					if ( dotProduct > 0 ) {
						mStatus = CSGFaceStatus.SAME;
					} else {
						mStatus = CSGFaceStatus.OPPOSITE;
					}
				}
				// If dot product > 0 (same direction), then inside face
				else if ( dotProduct > 0 ) {
					mStatus = CSGFaceStatus.INSIDE;
				}
				// If dot product < 0 (opposite direction), then outside face
				else {
					mStatus = CSGFaceStatus.OUTSIDE;
				}
			}
			return( mStatus );
		}
		// The deadman switch expired
		mStatus = CSGFaceStatus.UNKNOWN;
		if ( pEnvironment.mStructuralDebug ) {
			CSGEnvironment.sLogger.log( Level.WARNING, "CSGFace.rayTrace failed to converge: " + this );
		}
		return( null );
	}
	
	/** Checks if the face contains a point based on an intersecting ray */
	protected boolean hasPoint(
		CSGRay			pRay
	,	Vector3d		pPoint
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Quick check on the extremes
		Vector3d v1 = this.v1().getPosition();
		Vector3d v2 = this.v2().getPosition();
		Vector3d v3 = this.v3().getPosition();
/***
		if ( ((pPoint.x < v1.x) && (pPoint.x < v2.x) && (pPoint.x < v3.x)) 
		||	 ((pPoint.x > v1.x) && (pPoint.x > v2.x) && (pPoint.x > v3.x))
		||   ((pPoint.y < v1.y) && (pPoint.y < v2.y) && (pPoint.y < v3.y)) 
		||	 ((pPoint.y > v1.y) && (pPoint.y > v2.y) && (pPoint.y > v3.y))
		||   ((pPoint.z < v1.z) && (pPoint.z < v2.z) && (pPoint.z < v3.z)) 
		||	 ((pPoint.z > v1.z) && (pPoint.z > v2.z) && (pPoint.z > v3.z)) ) {
			// No possible intersection
			// @todo why does this not work????
			return( false );
		}
***/
		// Full fledged, computed intersection check
		boolean pointInTriangle 
			= pRay.intersectsTriangle( v1, v2, v3, pEnvironment.mEpsilonOnPlaneDbl );
//			= pRay.intersectsTriangle( this.v1().getPosition()
//										, this.v2().getPosition()
//										, this.v3().getPosition()
//										, null
//										, true
//										, pTempVars
//										, pEnvironment.mEpsilonOnPlaneDbl );
		return( pointInTriangle );
	}

	
	/** Checks if the the face contains a point */	
/** The following is from the original unboolean code.  I really do not understand what it is
 	doing, but it seems to fail when the values are close in 2 coordinates but differ in the third

	protected boolean hasPoint(
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
****/
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGFaceRevision
													, sCSGFaceDate
													, pBuffer ) );
	}

}
