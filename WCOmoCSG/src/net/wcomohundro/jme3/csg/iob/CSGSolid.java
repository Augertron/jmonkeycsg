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
import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceCollision;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceStatus;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB.CSGVertexStatus;
import net.wcomohundro.jme3.csg.math.CSGPlaneDbl;
import net.wcomohundro.jme3.math.Vector3d;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;


/** Manage a list of 'faces' that make up a given solid shape, ready to blend with another solid.
 
 	The key design constraint is the ability to relate one solid to another, ensuring that faces
 	of the two solids do not intersect, and assigning an Inside/Outside/Boundary status to
 	every face as related to the other solid.
 	
 */
public class CSGSolid 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSolidRevision="$Rev$";
	public static final String sCSGSolidDate="$Date$";

	//private static final double TOL = 1e-10f;

	
	/** The list of faces that make up this solid */
	protected List<CSGFace>		mFaces;
	/** The overall volumetric 'bounds' of this solid */
	protected CSGBounds			mBounds;
	/** Statistics tracker */
	protected CSGStatsIOB		mStatistics;
	
	
	/** Constructor based on a given list of faces */
	public CSGSolid(
		List<CSGFace>		pFaces
	,	CSGStatsIOB			pStatistics
	) {
		mFaces = new ArrayList<CSGFace>( pFaces );
		mStatistics = pStatistics;
	}
	
	/** Accessor to the face list */
	public List<CSGFace> getFaces() { return mFaces; }
	
	/** Produce a new solid with selected faces inverted */
	public CSGSolid invertFaces(
		CSGFaceStatus	pInvertFilter
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGFace> invertedList = new ArrayList<CSGFace>( mFaces.size() );
		for( CSGFace aFace : mFaces ) {
			if ( aFace.getStatus() == pInvertFilter ) {
				CSGFace invertedFace = aFace.clone( true, pEnvironment );
				invertedList.add( invertedFace );
			}
		}
		return( new CSGSolid( invertedList, this.mStatistics ) );
	}
	
	/** Accessor this solid's bounds */
	public CSGBounds getBounds(
	) {
		if ( mBounds == null ) {
			mBounds = new CSGBounds( mFaces );
		}
		return( mBounds );
	}
	
	/** Add a new face to this solid, based on splitting an existing face */
	protected int addFace(
		int				pFaceIndex
	,	CSGVertexIOB 	pV1
	, 	CSGVertexIOB 	pV2
	, 	CSGVertexIOB 	pV3
	, 	CSGFace			pSeedFace
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		if ( CSGEnvironment
				.equalVector3d( pV1.getPosition(), pV2.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
		|| CSGEnvironment
				.equalVector3d( pV2.getPosition(), pV3.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
		|| CSGEnvironment
				.equalVector3d( pV3.getPosition(), pV1.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl ) ) {
			// The face is too small to be pertinent, so if another face is added, 
			// just overuse the given slot
			if ( pEnvironment.mStructuralDebug ) {
				pEnvironment.log( Level.INFO, "CSGSolid discarding miniscule face:\n\t" + pV1 + "\n\t" + pV2 + "\n\t" + pV3 );
			}
			return( pFaceIndex );
		} else {
			// Build the face (having checked the vertices above)
			// NOTE that the face constructor will clone the vertices appropriately
			//		BUT it is assumed that this new face is in the same plane as the seed face
			CSGFace aFace = new CSGFace( pV1, pV2, pV3, pSeedFace, pTempVars, pEnvironment );
			if ( aFace.isValid() ) {
				// Mark this new face with its starting point in the secondary scan.
				aFace.setScanStartIndex( pSeedFace.getScanStartIndex() );
				
				// Retain the valid face
				if ( pFaceIndex < 0 ) {
					// Simple add to the end
					mFaces.add( aFace );
					
					// Track another face appended to the end
					return( pFaceIndex -1 );
				} else {
					// Overwrite the slot given
					mFaces.set( pFaceIndex, aFace );
					
					// Force first append on the next add
					return( -1 );
				}
			} else {
				// A face is invalid if the given points are NOT on the given plane
				if ( pEnvironment.mStructuralDebug ) {
					pEnvironment.log( Level.INFO, "CSGSolid discarding invalid face:\n" + this );
				}
				return( pFaceIndex );
			}
		}
	}
	
	/** Add an appropriate vertex 

	  	****** TempVars used: vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
	 */
	protected CSGVertexIOB addVertex(
		Vector3d			pNewPosition
	,	CSGVertexStatus		pNewStatus
	, 	CSGFace				pFace
	,	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( !pEnvironment.mDoublePrecision ) {
			CSGEnvironment.toFloat( pNewPosition, pEnvironment.mEpsilonNearZeroDbl );
		}
		// NOTE that once upon a time, a list of unique vertices was kept and scanned
		//		to only add a vertex that was not yet defined.  I am assuming this was
		//		done to optimize processing during the 'status' phase, but since we
		//		are building a new vertex that is extrapolated between two others, I do not
		//		think the odds of this new vertex overlapping a previously existing one
		//		are very good.  So do not bother looking.
		CSGVertexIOB vertex = pFace.extrapolate( pNewPosition, pNewStatus, pEdge, pTempVars, pEnvironment );
		return( vertex );	
	}

	/** Split the faces of this solid so that no face crosses through any face of another object.
	 	Any face so found will be split into subfaces that do not cross.  Newly generated faces
	 	will be checked against the other object as well.
	 	
	 	The end result is a set of faces that can then be categorized as completely within
	 	or completely ouside of another object.
	 	
	 	****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
	 */
	public void splitFaces(
		CSGSolid 			pOtherSolid
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		CSGRay line;
		CSGSegment segment1, segment2;
		int signFace1Vert1, signFace1Vert2, signFace1Vert3, signFace2Vert1, signFace2Vert2, signFace2Vert3;
		double tolerance = pEnvironment.mEpsilonOnPlaneDbl; // TOL;
		
		CSGBounds thisBound = this.getBounds();
		CSGBounds otherBound = pOtherSolid.getBounds();
		boolean solidsOverlap = thisBound.overlap( otherBound, pEnvironment );

		// Check each face in this solid
		//	NOTE that we iterate with an index, since we dynamically adjust 'i'
		//		 as a face is split.  This also means that mFaces.size() must
		//		 be refreshed for testing on each iteration.
		int initialSize = mFaces.size();
		
		List<CSGFace> otherFaces = pOtherSolid.getFaces();
		int otherSize = otherFaces.size();
		int sizeLimit = initialSize * otherSize * 10;
		
loop1:	for( int i = 0, j = sizeLimit; i < (j = mFaces.size()); i += 1 ) {
			// Rationality check (possibly debug???)
			if ( j > sizeLimit ) {
				// We have split way too many times
				CSGConstructionException anError
					= new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
													,	"CSGSolid.splitFaces - too many splits:" + j );
				throw anError;
			}
			// Allow an outside monitor to abort long running construction
			if ( Thread.interrupted() ) {
				// NOTE use of .interrupted() (which clears the interrupted status) versus
				//		currentThread.isInterrupted()  (which leaves the interrupted status alone)
				CSGConstructionException anError
					= new CSGConstructionException( CSGErrorCode.INTERRUPTED
													,	"CSGSolid.splitFaces - interrupted: " + j );
				throw anError;
			}
			// Count this as a processed face
			mStatistics.mFaceCount += 1;
			
			// Reset the face status for later classification
			CSGFace face1 = mFaces.get( i );
			face1.resetStatus();

			if ( i < initialSize ) {
				// First pass over the seed faces does a full scan over the other solid
				// Anything after the initial set is built as a fragment of some other face
				// that has already been scanned against some subset of the second list.
				face1.setScanStartIndex( 0 );
			} else if ( i == initialSize ) {
				// The first pass has made it through all the seed faces.  Everything past
				// this point is the result of a split.  Refactor the limit to try to better
				// bound a reasonable count of faces.
				// Use the count we have right now, with how many more times the expanded
				// faces could split
				sizeLimit = j + ((j - initialSize) * otherSize * 10);
			}
			// Check if the objects bounds overlap
			//	NOTE the overlap check is within the face loop so that resetStatus() is called
			//		 on every face even if there is no overlap to process.
			if ( solidsOverlap ) {			
				// Check if object1 face and anything in object2 overlap
				CSGBounds thisFaceBound = face1.getBound();
				if ( thisFaceBound.overlap( otherBound, pEnvironment ) ) {
					// If there is a gross overlap, then each face in object 2 must be checked
					// The face itself tells us where to start in the secondary list
loop2:				for( int m = face1.getScanStartIndex(), n = otherFaces.size(); m < n; m += 1 ) {
						CSGFace face2 = otherFaces.get( m );
			
						// Check if object1 face and object2 face overlap at all 
						CSGBounds otherFaceBound = face2.getBound();
						if ( thisFaceBound.overlap( otherFaceBound, pEnvironment ) ) {
							// Relative positions of the face1 vertices to the face2 plane
							signFace1Vert1 = face2.computePosition( face1.v1(), tolerance );
							signFace1Vert2 = face2.computePosition( face1.v2(), tolerance );
							signFace1Vert3 = face2.computePosition( face1.v3(), tolerance );
													
							// If all the signs are zero, the planes are coplanar, so skip it
							// If all the signs are positive or negative, the planes do not intersect, so skip it
							// If the signs are not equal, then it looks like an intersection 
							// is possible and we are interested
							if ( !(signFace1Vert1==signFace1Vert2 && signFace1Vert2==signFace1Vert3) ) {
								// Relative positions of the face2 vertices to the face1 plane
								signFace2Vert1 = face1.computePosition( face2.v1(), tolerance );
								signFace2Vert2 = face1.computePosition( face2.v2(), tolerance );
								signFace2Vert3 = face1.computePosition( face2.v3(), tolerance );

								// If the signs are not equal, then there is an intersection
								if ( !(signFace2Vert1==signFace2Vert2 && signFace2Vert2==signFace2Vert3) ) {
									line = new CSGRay( face1, face2, pEnvironment );
							
									// Compute the intersection of the face1 and the plane of face2
									segment1 = new CSGSegment( line, face1, signFace1Vert1, signFace1Vert2, signFace1Vert3, pTempVars, pEnvironment );
																	
									// Compute the intersection of the face2 and the plane of face1
									segment2 = new CSGSegment( line, face2, signFace2Vert1, signFace2Vert2, signFace2Vert3, pTempVars, pEnvironment );
																
									// If the two segments intersect, then the face must be split
									if ( segment1.intersect( segment2, pEnvironment ) ) {
										// A face can be split into 2 - 5 subfaces, based on how they collide
										switch( splitFace( i, m + 1, segment1, segment2, pTempVars, pEnvironment ) ) {
										case 0:
											// The face was not really split, so continue on checking
											// with next face from solid2
											break;
											
										case -1:
											// The face was physically removed, so a new face is in the
											// ith position.  It must be checked from the beginning
											i -= 1;
											initialSize -= 1;
											continue loop1;
											
										default:
											// The face was split into some number of subpieces.  But each
											// subpiece is a proper subsegment of the whole, so intersection
											// checks already applied against solid2 will not change.  We
											// can continue on from where we are, checking against the
											// remaining faces from solid2.
											face1 = mFaces.get( i );
											thisFaceBound = face1.getBound();
											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	/** Classify faces as being inside, outside or on boundary of other object 
	 
 		****** TempVars used:  vectd5, vectd6
	 */
	public void classifyFaces(
		CSGSolid 			pOtherObject
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	) throws CSGConstructionException {
		// Match every face against the other object
		for( CSGFace aFace : mFaces ) {	
			// Count this as a classified face
			mStatistics.mClassificationCount += 1;
			
			// Status on the vertices can make the classification really simple
			if ( aFace.simpleClassify( pEnvironment ) == false ) {
				// Nothing simple about it, so use the ray trace classification
				CSGFaceStatus aStatus = aFace.rayTraceClassify( pOtherObject, pTempVars, pEnvironment );
				if ( aStatus != null ) {
					// Mark the vertices to reflect the face status, 
					// which can speed up the classification of other faces that share a vertex.
					aFace.v1().mark( aFace, pEnvironment );
					aFace.v2().mark( aFace, pEnvironment );
					aFace.v3().mark( aFace, pEnvironment );
				}
			}
		}
	}

	
	/** Split an individual face
	 	@return   0 := the face was not actually split
	 			 -1 := the face split into miniscule pieces and was simply removed
	 			  n := the number of faces the face was split into
	 
	 	****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
    */	  
	protected int splitFace(
		int 				pFaceIndex
	,	int					pSecondaryScanStartingPoint
	, 	CSGSegment 			pFaceSegment
	, 	CSGSegment 			pOtherSegment
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	) throws CSGConstructionException {
		CSGFace.CSGFaceCollision startCollision, endCollision;
		
		double tolerance = pEnvironment.mEpsilonOnPlaneDbl; // TOL
		CSGFace aFace = mFaces.get( pFaceIndex );

		// We can keep track of how far through the secondary face list we are
		// via the scan starting point in this face.
		aFace.setScanStartIndex( pSecondaryScanStartingPoint );
		
		// NOTE that the position vectors can be taken from either segment.  Since
		//		both segments represent different portions of the same intersection
		//		line, the positions apply to both faces.
		Vector3d startPos, endPos;
		CSGVertexStatus startStatus, endStatus;

		// We now need to restrict the intersection 'line' from the planes of the faces
		// to that portion which actually is bounded by the current face
		double startDelta = pOtherSegment.getStartDistance() - pFaceSegment.getStartDistance();
		if ( startDelta > tolerance ) {
		//pOtherSegment.getStartDistance() > pFaceSegment.getStartDistance() ) { 
		//(pFaceSegment.getStartDistance() + tolerance)) {
			// The 'other' segment start is deeper so it determines the start point
			startPos = pOtherSegment.getStartPosition();
			startStatus = pOtherSegment.getStartStatus();
			
			// The other segment collision point is not pertinent.  The face segment can
			// tell us if an edge is involved or not
			startCollision = pFaceSegment.getOtherCollision( pOtherSegment, startPos, pEnvironment );
		} else if ( startDelta < -tolerance ) {
			// This face segment start is deeper, so it determines the start point
			startPos = pFaceSegment.getStartPosition();
			startStatus = pFaceSegment.getStartStatus();
			startCollision = pFaceSegment.getStartCollision();
		} else {
			// The segments start at effectively the same point, so pick the 'best' fit
			startPos = pFaceSegment.getStartPosition();
			startCollision = pFaceSegment.getStartCollision();	
			startStatus = pFaceSegment.getStartStatus();
			if ( pOtherSegment.getStartStatus() == CSGVertexStatus.BOUNDARY ) {
				// Retain the fact we are on a boundary
				startStatus = CSGVertexStatus.BOUNDARY;
			}
		}
		double endDelta = pOtherSegment.getEndDistance() - pFaceSegment.getEndDistance();
		if ( endDelta < -tolerance ) {
		// pOtherSegment.getEndDistance() < pFaceSegment.getEndDistance() ) { 
		//(pFaceSegment.getEndDistance() - tolerance)) {
			// The 'other' segment end is nearer, so it determines the end point
			endPos = pOtherSegment.getEndPosition();
			endStatus = pOtherSegment.getEndStatus();
			
			// The other segment collision point is not pertinent.  The face segment can
			// tell us if an edge is involved or not
			endCollision = pFaceSegment.getOtherCollision( pOtherSegment, endPos, pEnvironment );
		} else if ( endDelta > tolerance ) {
			// This face segment end is nearer, so it determines the end point
			endPos = pFaceSegment.getEndPosition();
			endStatus = pFaceSegment.getEndStatus();
			endCollision = pFaceSegment.getEndCollision();
		} else {
			// The segments start at effectively the same point, so pick the 'best' fit
			endPos = pFaceSegment.getEndPosition();
			endCollision = pFaceSegment.getEndCollision();
			endStatus = pFaceSegment.getEndStatus();
			if ( pOtherSegment.getEndStatus() == CSGVertexStatus.BOUNDARY ) {
				// Retain the fact we are on a boundary
				endStatus = CSGVertexStatus.BOUNDARY;
			}
		}
		if ( pEnvironment.mStructuralDebug ) {
			// Confirm we have points on the expected plane
			CSGPlaneDbl facePlane = aFace.getPlane();
			int startOnPlane = facePlane.pointPosition( startPos, pEnvironment.mEpsilonOnPlaneDbl );
			int endOnPlane = facePlane.pointPosition( endPos, pEnvironment.mEpsilonOnPlaneDbl );
			if ( (startOnPlane != 0) || (endOnPlane != 0) ) {
				pEnvironment.log( Level.WARNING, "CSGSolid: Invalid split points " + startOnPlane + endOnPlane );
				
				int faceStartOnPlane = facePlane.pointPosition( pFaceSegment.getStartPosition(), pEnvironment.mEpsilonOnPlaneDbl );
				int faceEndOnPlane = facePlane.pointPosition( pFaceSegment.getEndPosition(), pEnvironment.mEpsilonOnPlaneDbl );
				int otherStartOnPlane = facePlane.pointPosition( pOtherSegment.getStartPosition(), pEnvironment.mEpsilonOnPlaneDbl );
				int otherEndOnPlane = facePlane.pointPosition( pOtherSegment.getEndPosition(), pEnvironment.mEpsilonOnPlaneDbl );
				if ( (startCollision != CSGFaceCollision.NONE) && (endCollision != CSGFaceCollision.NONE)) {
					throw new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
														,	"CSGSolid: Corrupted split" );
				}
			}
		}
		// The collision points determine how we split
		switch( startCollision ) {
		case V1:
		case V2:
		case V3:
			// The start point is one of this face's vertices
			switch( endCollision ) {
			case V1:
			case V2:
			case V3:
				// Start and end both through a vertex, which means we only 
				// collided along an edge, so no split is required.
				return( 0 );
				
			case EDGE12:
			case EDGE23:
			case EDGE31:
				// Start through one vertex and then through an edge 
//				if ( startCollision.vertexOnEdge( endCollision ) ) {
					// The vertex is ON the target edge, so we have not crossed the face, 
					// we have a partial overlap along the given edge itself
					// (yes, the result is the same as below without the check for vertexOnEdger)
//					pFaceIndex = breakFaceInTwo( aFace, pFaceIndex, endPos, endCollision, pTempVars, pEnvironment );
//				} else {
					// We have a line through a vertex, crossing an opposite edge.  The 
					// new point must be on the given edge, so two new pieces will cover it.
					pFaceIndex = breakFaceInTwo( aFace, pFaceIndex, endPos, endStatus, endCollision, pTempVars, pEnvironment );
//				}
				break;
				
			case INTERIOR:
				// Start through one vertex and terminate in the middle of the face.
				// This requires the face to be split into three pieces circling around
				// the interior point.
				pFaceIndex = breakFaceInThree( aFace, pFaceIndex, endPos, endStatus, pTempVars, pEnvironment );
				break;
				
			default:
				// No real collision
				return( 0 );
			}
			break;
			
		case EDGE12:
		case EDGE23:
		case EDGE31:
			// The start point is on an edge of the face, what about the end?
			switch( endCollision ) {
			case V1:
			case V2:
			case V3:
				// Start through one edge and end via a vertex
//				if ( endCollision.vertexOnEdge( startCollision ) ) {
					// The vertex is ON the target edge, so we have not crossed the face, 
					// we have a partial overlap along the given edge itself
					// (yes, the result is the same as below without the check of vertexOnEdge)
//					pFaceIndex = breakFaceInTwo( aFace, pFaceIndex, startPos, startCollision, pTempVars, pEnvironment );
//				} else {
					// We have a line through a vertex, crossing an opposite edge.  The 
					// new point must be on the given edge, so two new pieces will cover it.
					pFaceIndex = breakFaceInTwo( aFace, pFaceIndex, startPos, startStatus, startCollision, pTempVars, pEnvironment );
//				}
				break;
				
			case EDGE12:
			case EDGE23:
			case EDGE31:
				if ( startCollision != endCollision ) {
					// Start through one edge and end via another edge.  This cuts the face 
					// into three pieces with two new vertices at the edge split points.
					pFaceIndex = breakFaceInThree( aFace, pFaceIndex
												, startPos, startStatus, startCollision
												, endPos, endStatus, endCollision
												, pTempVars, pEnvironment );
				} else {
					// Collision along the same edge.  This cuts the face
					// into three pieces with two new vertices along the same edge.
					pFaceIndex = breakFaceInThree( aFace, pFaceIndex
												, startPos, startStatus, endPos, endStatus, startCollision
												, pTempVars, pEnvironment );
				}
				break;
				
			case INTERIOR:
				// Start through one edge and terminate in the middle of the face.
				// This requires the face to be split into four pieces circling around
				// the interior point.
				pFaceIndex = breakFaceInFour( aFace, pFaceIndex
											, startPos, startStatus, startCollision
											, endPos, endStatus
											, pTempVars, pEnvironment );
				break;
				
			default:
				// No real collision
				return( 0 );
			}
			break;
			
		case INTERIOR:
			// The start point is in the middle of the face, what about the end?
			switch( endCollision ) {
			case V1:
			case V2:
			case V3:
				// Start from an interior point and end through a vertex.  This
				// cuts the face into three pieces circling around the interior
				// point.
				pFaceIndex = breakFaceInThree( aFace, pFaceIndex, startPos, startStatus, pTempVars, pEnvironment );
				break;
			
			case EDGE12:
			case EDGE23:
			case EDGE31:
				// Start from an interior point and end through an edge.
				// This requires the face to be split into four pieces circling
				// around the interior point.
				pFaceIndex = breakFaceInFour( aFace, pFaceIndex
											, endPos, endStatus, endCollision
											, startPos, startStatus
											, pTempVars, pEnvironment );
				break;

			case INTERIOR:
				// Start and end on points within the interior of the face. Confirm they are
				// a reasonable distance apart.
				if ( CSGEnvironment
					.equalVector3d( startPos, endPos, pEnvironment.mEpsilonBetweenPointsDbl ) ) {
					// Start and end are effectively the same, so treat it as a single center point
					pFaceIndex = breakFaceInThree( aFace, pFaceIndex, startPos, startStatus, pTempVars, pEnvironment );
				} else {
					// Select the best vertex of the face to operate from
					// I do not really understand the following -- but we do break the face into five
					Vector3d newSegment = startPos.subtract( endPos, pTempVars.vectd1 );
					
					Vector3d toVertex = endPos.subtract( aFace.v1().getPosition(), pTempVars.vectd2 );
					toVertex.normalizeLocal();
					double dot1 = Math.abs( newSegment.dot( toVertex ) );

					toVertex = endPos.subtract( aFace.v2().getPosition(), pTempVars.vectd2 );
					toVertex.normalizeLocal();
					double dot2 = Math.abs( newSegment.dot( toVertex ) );

					toVertex = endPos.subtract( aFace.v3().getPosition(), pTempVars.vectd2 );
					toVertex.normalizeLocal();
					double dot3 = Math.abs( newSegment.dot( toVertex ) );
					
					CSGFaceCollision pickVertex;
					if ( (dot1 > dot2) && (dot1 > dot3)) {
					 	pickVertex = CSGFaceCollision.V1;
						toVertex = aFace.v1().getPosition();
					} else if ( (dot2 > dot3) && (dot2 > dot1)) {
					 	pickVertex = CSGFaceCollision.V2;
						toVertex = aFace.v2().getPosition();
					} else {
					 	pickVertex = CSGFaceCollision.V3;
						toVertex = aFace.v3().getPosition();
					}
					// Now find which of the intersection points is nearest to that vertex.
					if ( toVertex.distance( startPos ) > toVertex.distance( endPos ) ) {
						pFaceIndex = breakFaceInFive( aFace, pFaceIndex
													, startPos, startStatus
													, endPos, endStatus
													, pickVertex
													, pTempVars, pEnvironment );
					} else {
						pFaceIndex = breakFaceInFive( aFace, pFaceIndex
													, endPos, endStatus
													, startPos, startStatus
													, pickVertex
													, pTempVars, pEnvironment );
					}
				}
				break;
				
			default:
				// No real collision
				return( 0 );
			}
			break;
			
		default:
			// No real collision
			return( 0 );
		}
		// We only reach this point if the face was actually split
		// However, it might be possible that all the split pieces were so small, that no real
		// face was actually added.  If any face at all was added, then pFaceIndex would be 
		// reset to -1
		if ( pFaceIndex >= 0 ) {
			// Nothing actually added, but the original is still in its slot
			// Once upon a time, I thought it should be removed, but now I am not so sure
			if ( pEnvironment.mStructuralDebug ) {
				pEnvironment.log( Level.INFO, "CSGSolid.splitFace: no face remains:\n" + aFace );
			}
			if ( pEnvironment.mRemoveUnsplitFace ) {
				// Eliminate the face that could not be split
				mFaces.remove( pFaceIndex );
				return( -1 );
			} else {
				// Retain the face that could not be split
				return( 0 ); 
			}
		} else {
			// Return the count of the faces we split into
			return( -pFaceIndex );
		}
	}
	  
	/** Split a face into two pieces along a vertex and a point on the opposite edge */		
	protected int breakFaceInTwo(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pNewPos
	,	CSGVertexStatus		pNewStatus
	, 	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex = addVertex( pNewPos, pNewStatus, pFace, pEdge, pTempVars, pEnvironment ); 
						
		switch( pEdge ) {
		case EDGE12:
			//               V2
			//
			//           
			//          New
			//         
			// 
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex, pFace.v3(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex, pFace.v2(), pFace.v3(), pFace, pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//           
			//                   New
			//         
			// 
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex, pFace.v1(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex, pFace.v3(), pFace.v1(), pFace, pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//           
			//         
			//         
			// 
			//		V1       New       V3
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex, pFace.v2(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex, pFace.v1(), pFace.v2(), pFace, pTempVars, pEnvironment );
			break;
		}
		return( pFaceIndex );
	}
	
	/** Split into three pieces along two new positions on two given edges */
	protected int breakFaceInThree(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pEdgePosition1
	,	CSGVertexStatus		pEdgeStatus1
	,	CSGFaceCollision	pEdgeCollision1
	, 	Vector3d 			pEdgePosition2
	,	CSGVertexStatus		pEdgeStatus2
	, 	CSGFaceCollision	pEdgeCollision2
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pEdgePosition1, pEdgeStatus1, pFace, pEdgeCollision1, pTempVars, pEnvironment );	
		CSGVertexIOB vertex2 = addVertex( pEdgePosition2, pEdgeStatus2, pFace, pEdgeCollision2, pTempVars, pEnvironment );
						
		switch( pEdgeCollision1 ) {
		case EDGE12:
			switch( pEdgeCollision2 ) {
			case EDGE31:
				//               V2
				//
				//          
				//         New1
				//        
				// 
				//		V1      New2       V3
				pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, pFace.v2(), vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vertex2, pFace, pTempVars, pEnvironment );
				break;
			case EDGE23:
				//               V2
				//
				//          
				//          New1      New2
				//        
				// 
				//		V1                 V3
				pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex1, pFace.v3(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, vertex2, pFace.v3(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
				break;
			}
			break;
			
		case EDGE23:
			switch( pEdgeCollision2 ) {
			case EDGE12:
				//               V2
				//
				//          
				//          New2      New1
				//        
				// 
				//		V1                 V3
				pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, pFace.v3(), vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vertex2, pFace, pTempVars, pEnvironment );
				break;
			case EDGE31:
				//               V2
				//
				//          
				//                   New1
				//        
				// 
				//		V1      New2       V3
				pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex1, pFace.v1(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, vertex2, pFace.v1(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
				break;
			}
			break;
			
		case EDGE31:
			switch( pEdgeCollision2 ) {
			case EDGE23:
				//               V2
				//
				//          
				//                   New2
				//        
				// 
				//		V1      New1       V3
				pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, pFace.v1(), vertex2, pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vertex2, pFace, pTempVars, pEnvironment );
				break;
			case EDGE12:
				//               V2
				//
				//          
				//         New2
				//        
				// 
				//		V1      New1       V3
				pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex1, pFace.v2(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, vertex1, vertex2, pFace.v2(), pFace, pTempVars, pEnvironment );
				pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
				break;
			}
			break;
		}
		return( pFaceIndex );
	}
	
	/** Split into three pieces along two new positions on a single edge */
	protected int breakFaceInThree(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pEdgePosition1
	,	CSGVertexStatus		pEdgeStatus1
	, 	Vector3d 			pEdgePosition2
	,	CSGVertexStatus		pEdgeStatus2
	, 	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pEdgePosition1, pEdgeStatus1, pFace, pEdge, pTempVars, pEnvironment );	
		CSGVertexIOB vertex2 = addVertex( pEdgePosition2, pEdgeStatus2, pFace, pEdge, pTempVars, pEnvironment );
						
		switch( pEdge ) {
		case EDGE12:
			//               V2
			//
			//           New1
			//
			//         New2
			// 
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex2, pFace.v3(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex2, vertex1, pFace.v3(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex1, pFace.v2(), pFace.v3(), pFace, pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//                  New2
			//
			//                     New1
			// 
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex2, pFace.v1(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex2, vertex1, pFace.v1(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex2, pFace.v3(), pFace.v1(), pFace, pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//              
			//
			//                   
			// 
			//		V1  New1    New2   V3
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex2, pFace.v2(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex2, vertex1, pFace.v2(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vertex2, pFace.v1(), pFace.v2(), pFace, pTempVars, pEnvironment );
			break;
		}
		return( pFaceIndex );
	}

	/** Split into three pieces based on a single interior point */
	protected int breakFaceInThree(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pCenterPoint
	,	CSGVertexStatus		pCenterStatus
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {	
		CSGVertexIOB vertex = addVertex( pCenterPoint, pCenterStatus, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		if ( vertex == null ) {
			// No valid interior point was generated
			return( pFaceIndex );
		}
		//               V2
		//
		//           
		//              
		//               New
		// 
		//		V1                V3
		pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vertex, pFace, pTempVars, pEnvironment );
		pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vertex, pFace, pTempVars, pEnvironment );
		pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vertex, pFace, pTempVars, pEnvironment );

		return( pFaceIndex );
	}
	
	/** Split into four pieces from an interior point to a given edge */	
	protected int breakFaceInFour(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pPositionOnEdge
	,	CSGVertexStatus		pEdgeStatus
	, 	CSGFaceCollision	pEdgeCollision
	, 	Vector3d 			pPositionOnFace
	,	CSGVertexStatus		pFaceStatus
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vtxEdge = addVertex( pPositionOnEdge, pEdgeStatus, pFace, pEdgeCollision, pTempVars, pEnvironment );
		CSGVertexIOB vtxCenter = addVertex( pPositionOnFace, pFaceStatus, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		if ( vtxCenter == null ) {
			// No valid interior point was generated
			return( pFaceIndex );
		}
		
		switch( pEdgeCollision ) {
		case EDGE12:
			//               V2
			//
			//           
			//         NewEdge     
			//              
			//              NewCtr
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vtxEdge, vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vtxEdge, pFace.v2(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vtxCenter, pFace, pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//           
			//                   NewEdge     
			//              
			//              NewCtr
			//		V1                V3
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vtxEdge, vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vtxEdge, pFace.v3(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vtxCenter, pFace, pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//           
			//                   
			//             NewCtr
			//              
			//		V1     NewEdge     V3
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vtxEdge, vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, vtxEdge, pFace.v1(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vtxCenter, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vtxCenter, pFace, pTempVars, pEnvironment );
			break;
		}
		return( pFaceIndex );
	}
	
	/** Split into five pieces to accommodate two new points that are both in the
	 	interior of the given face. 
	 */
	protected int breakFaceInFive(
		CSGFace				pFace
	,	int					pFaceIndex
	, 	Vector3d 			pNewPosition1
	,	CSGVertexStatus		pNewStatus1
	, 	Vector3d 			pNewPosition2
	,	CSGVertexStatus		pNewStatus2
	, 	CSGFaceCollision	pPickVertex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pNewPosition1, pNewStatus1, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		CSGVertexIOB vertex2 = addVertex( pNewPosition2, pNewStatus1, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		if ( (vertex1 == null) || (vertex2 == null) ) {
			// No valid interior point was generated
			return( pFaceIndex );
		}			
		switch( pPickVertex ) {
		case V1:
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex2, pFace.v1(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vertex2, pFace, pTempVars, pEnvironment );
			break;
			
		case V2:
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), pFace.v1(), vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v3(), vertex2, pFace.v2(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vertex2, pFace, pTempVars, pEnvironment );
			break;
			
		case V3:
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), pFace.v2(), vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex1, vertex2, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), vertex2, vertex1, pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v1(), vertex2, pFace.v3(), pFace, pTempVars, pEnvironment );
			pFaceIndex = addFace( pFaceIndex, pFace.v2(), pFace.v3(), vertex2, pFace, pTempVars, pEnvironment );
			break;
		}
		return( pFaceIndex );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGSolidRevision
													, sCSGSolidDate
													, pBuffer ) );
	}

}
