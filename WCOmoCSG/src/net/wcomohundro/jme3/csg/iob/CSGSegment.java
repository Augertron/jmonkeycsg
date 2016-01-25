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

import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.CSGVertexDbl;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceCollision;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB.CSGVertexStatus;

import com.jme3.scene.plugins.blender.math.Vector3d;


/** While a Ray represents the intersection of the two planes associated with two faces, 
 	the ray itself is not constrained by the bounds of the face.
 	
 	A Segment represents that portion of the line that fits within the constraints of the
 	face.  The options are:
 	1)	The line is totally outside the bounds of the face and does not intersect at all
 	2)	The line passes through a single vertex of the face
 	3)	The line passes through two vertices of the face
 	4)	The line passes through a single vertex and a single edge of the face
 	5)	The line passes through two edges of the face
 	
 	From the conditions above, you can see that only cases (4) and (5) represent a true
 	intersection of a face and a line.  Everything else misses entirely or intersects only
 	along an edge. Case (3) is still interesting in split processing if the intersection 
 	does not pass through both vertices of the edge.  The edge may still need to b split.
  */
public class CSGSegment 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSegmentRevision="$Rev$";
	public static final String sCSGSegmentDate="$Date$";
	
	//private static final double TOL = 1e-10f;
	
	/** The face this segment applies to */
	protected CSGFace			mFace;
	/** Line resulting from the two planes intersection */
	protected CSGRay			mLine;
	
	/** Distance from the segment starting point to the point defining the plane */
	protected double 			mStartDist;
	/** Distance from the segment ending point to the point defining the plane */
	protected double 			mEndDist;
	
	/** Start point collision status */
	protected CSGFaceCollision	mStartCollision;
	/** End point collision status */
	protected CSGFaceCollision	mEndCollision;
	
	/** Actual starting position, possibly interpolated from other vertices */
	protected Vector3d			mStartPosition;
	/** Actual ending position, possibly interpolated from other vertices */
	protected Vector3d			mEndPosition;
	
	/** Null constructor */
	public CSGSegment(
	) {
	}
	/** Basic constructor based on a given face
	    The 'signs' of the 3 vertices represent their position relative to the OTHER face
	    that produced the Ray.  Zero means the vertex lies on the plane of the other face,
	    with +/- representing a given side.
	    
	 	NOTE
	 		that any vertex tracked by this segment are == to the vertices taken from the face
	  */
	public CSGSegment(
		CSGRay			pLine
	, 	CSGFace 		pFace
	, 	int 			pSign1
	, 	int 			pSign2
	, 	int 			pSign3
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		mFace = pFace;
		mLine = pLine;
		int anIndex = 0;
		
		if ( pSign1 == 0 ) {
			// V1 lies on the other plane, so it is a VERTEX
			anIndex = setVertex( anIndex, CSGFaceCollision.V1, pFace.v1(), pTempVars, pEnvironment );
			
			if ( pSign2 == pSign3 ) {
				// V2 and V3 are on the same side of the other plane, so this segment
				// has no length
				setVertex( -anIndex, CSGFaceCollision.NONE, null, pTempVars, pEnvironment );
				return;
			}
		}
		// If VERTEX is an end
		if ( pSign2 == 0 ) {
			// V2 lies on the other plane, so it is a VERTEX
			anIndex = setVertex( anIndex, CSGFaceCollision.V2, pFace.v2(), pTempVars, pEnvironment );
			
			if ( pSign1 == pSign3 ) {
				// V1 and V3 are on the same side of the other plane, so this segment
				// has no length
				setVertex( -anIndex, CSGFaceCollision.NONE, null, pTempVars, pEnvironment );
				return;
			}
		}
		// If VERTEX is an end
		if ( pSign3 == 0 ) {
			// V3 lies on the other plane, so it is a VERTEX
			anIndex = setVertex( anIndex, CSGFaceCollision.V3, pFace.v3(), pTempVars, pEnvironment );
			
			if ( pSign1 == pSign2 ) {
				// V1 and V2 are on the same side of the other plane, so this segment
				// has no length
				setVertex( -anIndex, CSGFaceCollision.NONE, null, pTempVars, pEnvironment );
				return;
			}
		}
		// If there are undefined ends, then one or more edges cut the plane intersection line
		if ( anIndex < 2 ) {
			if ( pSign1 * pSign2 < 0 ) { // (pSign1==1 && pSign2==-1) || (pSign1==-1 && pSign2==1) ) {
				// V1 and V2 on opposite sides of the plane, so the EDGE12 is cut
				anIndex = setEdge( anIndex, CSGFaceCollision.EDGE12, pFace.v1(), pFace.v2(), pTempVars, pEnvironment );
			}
			if ( pSign2 * pSign3 < 0 ) { // (pSign2==1 && pSign3==-1) || (pSign2==-1 && pSign3==1) ) {
				// V2 and V3 on opposite sides of the plane, so the EDGE23 is cut
				anIndex = setEdge( anIndex, CSGFaceCollision.EDGE23, pFace.v2(), pFace.v3(), pTempVars, pEnvironment );
			}
			if ( pSign3 * pSign1 < 0 ) { // (pSign3==1 && pSign1==-1) || (pSign3==-1 && pSign1==1) ) {
				// V3 and V1 on opposite sides of the plane, so the EDGE31 is cut
				anIndex = setEdge( anIndex, CSGFaceCollision.EDGE31, pFace.v3(), pFace.v1(), pTempVars, pEnvironment );
			}
			if ( anIndex < 2 ) {
				throw new IllegalStateException( "Segment does not intersect with face" );
			}
		}
	}
	
	/**  Checks if two segments intersect */
	public boolean intersect(
		CSGSegment		pOther
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonBetweenPointsDbl; // TOL;
		
		// An invalid segment will use NaN as a distance.  Since any comparison with
		// NaN always yields 'false' structure the following logic accordingly
		if ( (this.mEndDist > pOther.mStartDist + tolerance)
		&&   (pOther.mEndDist > this.mStartDist + tolerance) ) {
			// Overlap
			return true;
		} else {
			// No overlap (or an invalid segment that we treat like no overlap)
			return false;
		}
/**
		if ( (this.mEndDist < pOther.mStartDist + tolerance )
		||    (pOther.mEndDist < this.mStartDist + tolerance ) ) {
			// No overlap
			return false;
		} else {
			// Overlap
			return true;
		}
**/
	}
	
	/** Accessors */
	public double getStartDistance() { return mStartDist; }
	public double getEndDistance() { return	mEndDist; }
	
	public CSGFaceCollision getStartCollision() { return mStartCollision; }
	public CSGFaceCollision getEndCollision() { return mEndCollision; }
	
	/** Check if this segment is defined by an edge */
	public boolean isEdgeCollision(
	) { 
		return( mStartCollision.getEdge( mEndCollision ).isEdge() );
	}
	
	/** When the 'other' segment is providing the position, its collision status
	 	is pertinent mainly to itself, not to 'this' segment.  What we do know is that
	 	the other position is somewhere along the intersection line, which is
	 	shared with this segment.
	 	
	 	So if this segment is defined by an edge, then we know the other position
	 	is along an edge in this segment.  If not an edge, then we have to
	 	assume an interior collision.
	 	
	 	If the other segment is strictly along an edge, then an edge collision 
	 	with this segment holds no interest. If the other segment is not an edge, then
	 	an edge collision with segment may still require a split since different portions
	 	of this face may be Inside vs Outside.
	 	
	 	Also, once we know we have an edge, we can also look for a vertex.
	 	(which I am not absolutely convinced is necessary, but we will keep it for now)
	 */
	public CSGFaceCollision getOtherCollision(
		CSGSegment		pOtherSegment
	,	Vector3d		pOtherPosition
	,	CSGEnvironment	pEnvironment
	) {
		// Look for this segment along an edge, and if so, look for a vertex
		CSGFaceCollision aCollision = mStartCollision.getEdge( mEndCollision );
		switch( aCollision ) {
		case EDGE12:
			if ( pOtherSegment.isEdgeCollision() ) {
				aCollision = CSGFaceCollision.NONE;		// Strictly edge to edge, not interesting
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
													,	mFace.v1().getPosition()
													, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V1;
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
													,	mFace.v2().getPosition()
													, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V2;
			}
			break;
			
		case EDGE23:
			if ( pOtherSegment.isEdgeCollision() ) {
				aCollision = CSGFaceCollision.NONE;		// Strictly edge to edge, not interesting
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
												,	mFace.v2().getPosition()
												, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V2;
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
													,	mFace.v3().getPosition()
													, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V3;
			}
			break;
			
		case EDGE31:
			if ( pOtherSegment.isEdgeCollision() ) {
				aCollision = CSGFaceCollision.NONE;		// Strictly edge to edge, not interesting
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
													,	mFace.v3().getPosition()
													, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V3;
			} else if ( CSGEnvironment.equalVector3d( pOtherPosition
													,	mFace.v1().getPosition()
													, 	pEnvironment.mEpsilonBetweenPointsDbl) ) {
				aCollision = CSGFaceCollision.V1;
			}
			break;
			
		case INTERIOR:
			// The given 'other' position looks like it is interior to this face. If it coincides
			// with a known segment position, then we have better info
			if ( CSGEnvironment.equalVector3d( mStartPosition
												, pOtherPosition
												, pEnvironment.mEpsilonBetweenPointsDbl ) ) {
				// Since we matched this segment's start position, we can use its start status
				aCollision = mStartCollision;
			} else if ( CSGEnvironment.equalVector3d( mEndPosition
												, pOtherPosition
												, pEnvironment.mEpsilonBetweenPointsDbl ) ) {
				// Since we matched this segment's end position, we can use its end status
				aCollision = mEndCollision;
			}
			break;
		}
		return( aCollision );
	}
	
	public Vector3d getStartPosition() { return mStartPosition; }
	public Vector3d getEndPosition() { return mEndPosition; }
	
	
	/** Sets an end as vertex (starting point if none end were defined, ending point otherwise)
	    @return false if all the ends were already defined, true otherwise
	 */
	protected int setVertex(
		int					pIndex
	,	CSGFaceCollision	pCollision
	,	CSGVertexIOB		pVertex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		switch( pIndex ) {
		case 0:
			// No end yet defined - define the starting point 
			mStartPosition = pVertex.getPosition();
			
			// By definition, we recognize this vertex as on a boundary
			pVertex.setStatus( CSGVertexStatus.BOUNDARY );

		 	mStartCollision = pCollision;
		 	mStartDist = mLine.computePointToPointDistance( mStartPosition, pTempVars, pEnvironment );
		 	return( 1 );
		 	
		case 1:
			// Starting point already defined - define ending point as VERTEX
			mEndPosition = pVertex.getPosition();
			
			// By definition, we recognize this vertex as on a boundary
			pVertex.setStatus( CSGVertexStatus.BOUNDARY );

			mEndCollision = pCollision;
			mEndDist = mLine.computePointToPointDistance( mEndPosition, pTempVars, pEnvironment );
					
			// The ending point distance should be smaller than  starting point distance 
			if ( mStartDist > mEndDist ) {
				swapEnds();
			}
			return( 2 );
		
		case -1:
			// The starting point is the only point on the other plane, this segment has no length
			mEndPosition = mStartPosition;
			mEndDist = mStartDist;
			mEndCollision = mStartCollision = pCollision;
			return( 2 );
			
		default:
			// Vertices already set....
			throw new IllegalStateException( "Segment intersects with 3 vertices" );
		}
	}
	
	/** Sets an end as edge (starting point if none end were defined, ending point otherwise)
	 
	    @return false if all ends were already defined, true otherwise
	    
	    ****** TempVars used:  vectd6
	 */
	protected int setEdge(
		int					pIndex
	,	CSGFaceCollision	pCollision
	,	CSGVertexIOB		pVertex1
	, 	CSGVertexIOB		pVertex2
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		Vector3d point1 = pVertex1.getPosition();
		Vector3d point2 = pVertex2.getPosition();
		Vector3d edgeDirection = point2.subtract( point1, pTempVars.vectd6 );
		
		CSGRay edgeLine = new CSGRay( edgeDirection, point1 );
		
		switch( pIndex ) {
		case 0:
			// No other points have yet been defined
			mStartCollision = pCollision;
			mStartPosition = mLine.computeLineIntersection( edgeLine, null, pTempVars, pEnvironment );
			mStartDist = mLine.computePointToPointDistance( mStartPosition, pTempVars, pEnvironment );
			
			if ( mFace.getPlane().pointPosition( mStartPosition, pEnvironment.mEpsilonOnPlaneDbl ) != 0 ) {
				// Computed point is NOT on the face
				// mStartPosition = null;
				mStartDist = Double.NaN;
				if ( pEnvironment.mStructuralDebug ) {
					pEnvironment.log( Level.WARNING, "CSGSegment.setEdge invalid start: " + pVertex1 + ", " + pVertex2 + ", " + mStartPosition );
				}
			}
			return( 1 );
			
		case 1:
			// Starting point already defined, define the ending point
			mEndCollision = pCollision;
			mEndPosition = mLine.computeLineIntersection( edgeLine, null, pTempVars, pEnvironment );
			mEndDist = mLine.computePointToPointDistance( mEndPosition, pTempVars, pEnvironment );
			
			if ( mFace.getPlane().pointPosition( mEndPosition, pEnvironment.mEpsilonOnPlaneDbl ) != 0 ) {
				// Computed point is NOT on the face
				//mEndPosition = null;
				mEndDist = Double.NaN;
				if ( pEnvironment.mStructuralDebug ) {
					pEnvironment.log( Level.WARNING, "CSGSegment.setEdge invalid end: " + pVertex1 + ", " + pVertex2 + ", " + mEndPosition );
				}
			}			
			// The ending point distance should be 'farther' than starting point distance 
			if ( mStartDist > mEndDist ) {
			  	swapEnds();
			}
			return( 2 );
			
		default:
			// All points have already been defined
			throw new IllegalStateException( "Segment intersects with 3 edges" );
		}
	}

	/** Swaps the starting point and the ending point */	
	protected void swapEnds(
	) {
		double distTemp = mStartDist;
		mStartDist = mEndDist;
		mEndDist = distTemp;
		
		CSGFaceCollision collisionTemp = mStartCollision;
		mStartCollision = mEndCollision;
		mEndCollision = collisionTemp;	
		
		Vector3d positionTemp = mStartPosition;
		mStartPosition = mEndPosition;
		mEndPosition = positionTemp;
	}

	/** OVERRIDE for debug report */
	@Override
	public String toString(
	) {
		return( "Seg:\t" + mStartPosition + "/" + mStartCollision + " : " + mStartDist
				 + "\n\t" + mEndPosition + "/" + mEndCollision + " : " + mEndDist );
	}


	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGSegmentRevision
													, sCSGSegmentDate
													, pBuffer ) );
	}

}
