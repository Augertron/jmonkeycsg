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

import com.jme3.scene.plugins.blender.math.Vector3d;


/** Represent a line segment that results from the intersection of two faces */
public class CSGSegment 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSegmentRevision="$Rev: 72 $";
	public static final String sCSGSegmentDate="$Date: 2015-09-05 21:57:32 -0500 (Sat, 05 Sep 2015) $";
	
	private static final double TOL = 1e-10f;
	
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
		
		// If VERTEX is an end
		if ( pSign1 == 0 ) {
			anIndex = setVertex( anIndex, pFace.v1(), pTempVars, pEnvironment );
			
			// If other vertices on the same side - VERTEX-VERTEX VERTEX
			if ( pSign2 == pSign3 ) {
				anIndex = setVertex( anIndex, pFace.v1(), pTempVars, pEnvironment );
			}
		}
		// If VERTEX is an end
		if ( pSign2 == 0 ) {
			anIndex = setVertex( anIndex, pFace.v2(), pTempVars, pEnvironment );
			
			// If other vertices on the same side - VERTEX-VERTEX VERTEX
			if ( pSign1 == pSign3 ) {
				anIndex = setVertex( anIndex, pFace.v2(), pTempVars, pEnvironment );
			}
		}
		// If VERTEX is an end
		if ( pSign3 == 0 ) {
			anIndex = setVertex( anIndex, pFace.v3(), pTempVars, pEnvironment );
			
			// If other vertices on the same side - VERTEX-VERTEX VERTEX
			if ( pSign1 == pSign2 ) {
				anIndex = setVertex( anIndex, pFace.v3(), pTempVars, pEnvironment );
			}
		}
		// If there are undefined ends, then one or more edges cut the plane intersection line
		if ( anIndex < 2 ) {
			// EDGE is an end
			if ( (pSign1==1 && pSign2==-1) || (pSign1==-1 && pSign2==1) ) {
				anIndex = setEdge( anIndex, pFace.v1(), pFace.v2(), pTempVars, pEnvironment );
			}
			// EDGE is an end
			if( (pSign2==1 && pSign3==-1) || (pSign2==-1 && pSign3==1) ) {
				anIndex = setEdge( anIndex, pFace.v2(), pFace.v3(), pTempVars, pEnvironment );
			}
			// EDGE is an end
			if ( (pSign3==1 && pSign1==-1) || (pSign3==-1 && pSign1==1) ) {
				anIndex = setEdge( anIndex, pFace.v3(), pFace.v1(), pTempVars, pEnvironment );
			}
		}
	}
	
	/**  Checks if two segments intersect */
	public boolean intersect(
		CSGSegment		pOther
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = TOL; // pEnvironment.mEpsilonNearZero;
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
	
	public CSGVertexIOB getStartVertex() { return mStartVertex; }
	public CSGVertexIOB getEndVertex() { return mEndVertex; }
	
	public Vector3d getStartPosition() { return mStartPosition; }
	public Vector3d getEndPosition() { return mEndPosition; }
	
	
	/** Sets an end as vertex (starting point if none end were defined, ending point otherwise)
	    @return false if all the ends were already defined, true otherwise
	 */
	protected int setVertex(
		int				pIndex
	,	CSGVertexIOB	pVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		switch( pIndex ) {
		case 0:
			// No end yet defined - define starting point as VERTEX
			mStartVertex = pVertex;
			mStartPosition = pVertex.getPosition();
		 	mStartType = CSGSegmentType.VERTEX;
		 	mStartDist = mLine.computePointToPointDistance( mStartVertex.getPosition(), pTempVars, pEnvironment );
		 	return( 1 );
		 	
		case 1:
			// Starting point already defined - define ending point as VERTEX
			mEndVertex = pVertex;
			mEndPosition = pVertex.getPosition();
			mEndType = CSGSegmentType.VERTEX;
			mEndDist = mLine.computePointToPointDistance( mEndVertex.getPosition(), pTempVars, pEnvironment );
					
			if ( mStartVertex.equals( mEndVertex ) ) {
				//VERTEX-VERTEX-VERTEX
				mMiddleType = CSGSegmentType.VERTEX;
			} else if( mStartType == CSGSegmentType.VERTEX ) {
				// VERTEX-EDGE-VERTEX
				mMiddleType = CSGSegmentType.EDGE;
			}
			// The ending point distance should be smaller than  starting point distance 
			if ( mStartDist > mEndDist ) {
				swapEnds();
			}
			return( 2 );
		
		default:
			// Vertices already set....
			return( pIndex );
		}
	}
	
	/** Sets an end as edge (starting point if none end were defined, ending point otherwise)
	    @return false if all ends were already defined, true otherwise
	 */
	protected int setEdge(
		int				pIndex
	,	CSGVertexIOB	pVertex1
	, 	CSGVertexIOB	pVertex2
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		Vector3d point1 = pVertex1.getPosition();
		Vector3d point2 = pVertex2.getPosition();
		Vector3d edgeDirection = point2.subtract( point1, pTempVars.vectd4 );
		
		CSGRay edgeLine = new CSGRay( edgeDirection, point1 );
		
		switch( pIndex ) {
		case 0:
			// No other points have yet been defined
			mStartType = CSGSegmentType.EDGE;
			mStartVertex = pVertex1;
			mStartPosition = mLine.computeLineIntersection( edgeLine, pTempVars, pEnvironment );
			mStartDist = mLine.computePointToPointDistance( mStartPosition, pTempVars, pEnvironment);
			mMiddleType = CSGSegmentType.FACE;
			return( 1 );
			
		case 1:
			// Starting point already defined, define the ending point
			mEndType = CSGSegmentType.EDGE;
			mEndVertex = pVertex1;
			mEndPosition = mLine.computeLineIntersection( edgeLine, pTempVars, pEnvironment );
			mEndDist = mLine.computePointToPointDistance( mEndPosition, pTempVars, pEnvironment );
			mMiddleType = CSGSegmentType.FACE;
			
			// The ending point distance should be smaller than starting point distance 
			if ( mStartDist > mEndDist ) {
			  	swapEnds();
			}
			return( 2 );
			
		default:
			// All points have already been defined
			return( pIndex );
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
		
		CSGVertexIOB vertexTemp = mStartVertex;
		mStartVertex = mEndVertex;
		mEndVertex = vertexTemp;	
		
		Vector3d positionTemp = mStartPosition;
		mStartPosition = mEndPosition;
		mEndPosition = positionTemp;
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
