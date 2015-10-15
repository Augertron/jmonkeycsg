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

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.CSGVertexDbl;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceCollision;

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
 	along an edge.
  */
public class CSGSegment 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSegmentRevision="$Rev$";
	public static final String sCSGSegmentDate="$Date$";
	
	//private static final double TOL = 1e-10f;
	
	/** Type of a segment terminus */
	public enum CSGSegmentType {
		VERTEX		// Terminus is on a vertex
	,	FACE		// Terminus in on a face
	,	EDGE		// Terminus is on an edge
	}
	
	/** Line resulting from the two planes intersection */
	protected CSGRay			mLine;
	
	/** Distance from the segment starting point to the point defining the plane */
	protected double 			mStartDist;
	/** Distance from the segment ending point to the point defining the plane */
	protected double 			mEndDist;
	
	/** starting point status relative to the face */
	protected CSGSegmentType	mStartType;
	/** intermediate status relative to the face */
	protected CSGSegmentType	mMiddleType;
	/** ending point status relative to the face */
	protected CSGSegmentType	mEndType;
	
	/** Start point collision status */
	protected CSGFaceCollision	mStartCollision;
	/** End point collision status */
	protected CSGFaceCollision	mEndCollision;
	
	/** Nearest vertex from the starting point */
	protected CSGVertexIOB 		mStartVertex;
	/** Actual starting position, possibly interpolated from other vertices */
	protected Vector3d			mStartPosition;
	
	/** Nearest vertex from the ending point */
	protected CSGVertexIOB 		mEndVertex; 
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
			if( pSign2 * pSign3 < 0 ) { // (pSign2==1 && pSign3==-1) || (pSign2==-1 && pSign3==1) ) {
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
		if ( (this.mEndDist < pOther.mStartDist + tolerance )
		||    (pOther.mEndDist < this.mStartDist + tolerance ) ) {
			// No overlap
			return false;
		} else {
			// Overlap
			return true;
		}
	}
	
	/** Accessors */
	public double getStartDistance() { return mStartDist; }
	public double getEndDistance() { return	mEndDist; }
	
	public CSGSegmentType getStartType() { return mStartType; }
	public CSGSegmentType getIntermediateType() { return mMiddleType; }
	public CSGSegmentType getEndType() { return mEndType; }
	
	public CSGFaceCollision getStartCollision() { return mStartCollision; }
	public CSGFaceCollision getEndCollision() { return mEndCollision; }
	public CSGFaceCollision getOtherCollision(
	) {
		// If the collision status is determined by an 'other' segment, then the
		// only thing we can tell about this segment is if the segment runs
		// along an edge.
		return( mStartCollision.getEdge( mEndCollision ) );
	}
	
	public CSGVertexIOB getStartVertex() { return mStartVertex; }
	public CSGVertexIOB getEndVertex() { return mEndVertex; }
	
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
			// No end yet defined - define starting point as VERTEX
			mStartVertex = pVertex;
			mStartPosition = pVertex.getPosition();
		 	mStartType = CSGSegmentType.VERTEX;
		 	mStartCollision = pCollision;
		 	mStartDist = mLine.computePointToPointDistance( mStartVertex.getPosition(), pTempVars, pEnvironment );
		 	return( 1 );
		 	
		case 1:
			// Starting point already defined - define ending point as VERTEX
			mEndVertex = pVertex;
			mEndPosition = pVertex.getPosition();
			mEndType = CSGSegmentType.VERTEX;
			mEndCollision = pCollision;
			mEndDist = mLine.computePointToPointDistance( mEndVertex.getPosition(), pTempVars, pEnvironment );
					
			// By definition, the end can only be set as a Vertex iff the start was a Vertex as well.
			// Otherwise, setEdge() would have been called
			mMiddleType =  CSGSegmentType.EDGE;
/****
			if ( mStartVertex.equals( mEndVertex ) ) {
				//VERTEX-VERTEX-VERTEX
				mMiddleType = CSGSegmentType.VERTEX;
			} else if( mStartType == CSGSegmentType.VERTEX ) {
				// VERTEX-EDGE-VERTEX
				mMiddleType = CSGSegmentType.EDGE;
			}
***/
			// The ending point distance should be smaller than  starting point distance 
			if ( mStartDist > mEndDist ) {
				swapEnds();
			}
			return( 2 );
		
		case -1:
			// The starting point is the only point on the other plane, this segment has no length
			mEndVertex = mStartVertex;
			mEndPosition = mStartPosition;
			mEndDist = mStartDist;
			mEndType = mMiddleType = CSGSegmentType.VERTEX;
			mEndCollision = pCollision;
			return( 2 );
			
		default:
			// Vertices already set....
			throw new IllegalStateException( "Segment intersects with 3 vertices" );
		}
	}
	
	/** Sets an end as edge (starting point if none end were defined, ending point otherwise)
	    @return false if all ends were already defined, true otherwise
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
		Vector3d edgeDirection = point2.subtract( point1, pTempVars.vectd4 );
		
		CSGRay edgeLine = new CSGRay( edgeDirection, point1 );
		
		switch( pIndex ) {
		case 0:
			// No other points have yet been defined
			mStartType = CSGSegmentType.EDGE;
			mStartCollision = pCollision;
			mStartVertex = pVertex1;
			mStartPosition = mLine.computeLineIntersection( edgeLine, pTempVars, pEnvironment );
			mStartDist = mLine.computePointToPointDistance( mStartPosition, pTempVars, pEnvironment);
			mMiddleType = CSGSegmentType.FACE;
			return( 1 );
			
		case 1:
			// Starting point already defined, define the ending point
			mEndType = CSGSegmentType.EDGE;
			mEndCollision = pCollision;
			mEndVertex = pVertex1;
			mEndPosition = mLine.computeLineIntersection( edgeLine, pTempVars, pEnvironment );
			mEndDist = mLine.computePointToPointDistance( mEndPosition, pTempVars, pEnvironment );
			mMiddleType = CSGSegmentType.FACE;
			
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
		
		CSGSegmentType typeTemp = mStartType;
		mStartType = mEndType;
		mEndType = typeTemp;
		
		CSGFaceCollision collisionTemp = mStartCollision;
		mStartCollision = mEndCollision;
		mEndCollision = collisionTemp;
		
		CSGVertexIOB vertexTemp = mStartVertex;
		mStartVertex = mEndVertex;
		mEndVertex = vertexTemp;	
		
		Vector3d positionTemp = mStartPosition;
		mStartPosition = mEndPosition;
		mEndPosition = positionTemp;
	}

	/** OVERRIDE for debug report */
	@Override
	public String toString(
	) {
		return( "Seg:\t" + mStartPosition + "/" + mStartVertex 
				 + "\n\t" + mEndPosition + "/" + mEndVertex );
	}


	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGSegmentRevision
													, sCSGSegmentDate
													, pBuffer ) );
	}

}
