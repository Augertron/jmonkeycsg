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

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceCollision;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceStatus;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB.CSGVertexStatus;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;


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
	
	
	/** Constructor based on a given list of faces */
	public CSGSolid(
		List<CSGFace>		pFaces
	) {
		mFaces = new ArrayList<CSGFace>( pFaces );;
	}
	
	/** Accessor to the face list */
	public List<CSGFace> getFaces() { return mFaces; }
	
	/** Produce a new solid with selected faces inverted */
	public CSGSolid invertFaces(
		CSGFace.CSGFaceStatus	pInvertFilter
	) {
		List<CSGFace> invertedList = new ArrayList<CSGFace>( mFaces.size() );
		for( CSGFace aFace : mFaces ) {
			if ( aFace.getStatus() == pInvertFilter ) {
				CSGFace invertedFace = aFace.clone( true );
				invertedList.add( invertedFace );
			}
		}
		return( new CSGSolid( invertedList ) );
	}
	
	/** Accessor this solid's bounds */
	public CSGBounds getBounds(
	) {
		if ( mBounds == null ) {
			mBounds = new CSGBounds( mFaces );
		}
		return( mBounds );
	}
	
	/** Add a new face to this solid */
	protected CSGFace addFace(
		CSGVertexIOB 	pV1
	, 	CSGVertexIOB 	pV2
	, 	CSGVertexIOB 	pV3
	, 	int 			pMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		if ( ConstructiveSolidGeometry
				.equalVector3d( pV1.getPosition(), pV2.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
		|| ConstructiveSolidGeometry
				.equalVector3d( pV2.getPosition(), pV3.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl )
		|| ConstructiveSolidGeometry
				.equalVector3d( pV3.getPosition(), pV1.getPosition(), pEnvironment.mEpsilonBetweenPointsDbl ) ) {
			// The face is too small to be pertinent
			return( null );
		} else {
			// Build the face
			CSGFace aFace = new CSGFace( pV1, pV2, pV3, pMaterialIndex, pTempVars, pEnvironment );
			if ( aFace.isValid() ) {
				// Retain the valid face
				mFaces.add( aFace );
				return( aFace );
			} else {
				// A face is invalid if no plane could be determined -- in other words, the
				// vertices are in a line
				throw new IllegalArgumentException( "Invalid new Face: " + aFace );
			}
		}
	}
	
	/** Add an appropriate vertex 
	 * 
	  	****** TempVars used: vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
	 */
	protected CSGVertexIOB addVertex(
		Vector3d			pNewPosition
	, 	CSGFace				pFace
	,	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		// NOTE that once upon a time, a list of unique vertices was kept and scanned
		//		to only add a vertex that was not yet defined.
		//		I could find no compelling reason for this, and the overhead of this separate
		//		list was substantial, so it has been dropped.
		CSGVertexIOB vertex = pFace.extrapolate( pNewPosition, pEdge, pTempVars, pEnvironment );
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
		CSGSolid 		pSolid
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGRay line;
		CSGSegment segment1, segment2;
		int signFace1Vert1, signFace1Vert2, signFace1Vert3, signFace2Vert1, signFace2Vert2, signFace2Vert3;
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;
		
		// Check each face in this solid
		//	NOTE that we iterate with an index, since we dynamically adjust 'i'
		//		 as a face is split.  This also means that mFaces.size() must
		//		 be refreshed for testing on each iteration.
		for( int i = 0; i < mFaces.size(); i += 1 ) {
			CSGFace face1 = mFaces.get( i );
			
			// Reset the face/vertices status for later classification
			face1.resetStatus();
				
			// Check if the objects bounds overlap
			CSGBounds thisBound = this.getBounds();
			CSGBounds otherBound = pSolid.getBounds();
			if ( thisBound.overlap( otherBound, pEnvironment ) ) {			
				// Check if object1 face and anything in object2 overlap
				CSGBounds thisFaceBound = face1.getBound();
				if ( thisFaceBound.overlap( otherBound, pEnvironment ) ) {
					// If there is a gross overlap, then each face in object 2 must be checked
					for( CSGFace face2 : pSolid.getFaces() ) {
						// Check if object1 face and object2 face overlap at all 
						CSGBounds otherFaceBound = face2.getBound();
						if ( thisFaceBound.overlap( otherFaceBound, pEnvironment ) ) {
							// Relative positions of the face1 vertices to the face2 plane
							signFace1Vert1 = face2.computePosition( face1.v1(), tolerance );
							signFace1Vert2 = face2.computePosition( face1.v2(), tolerance );
							signFace1Vert3 = face2.computePosition( face1.v3(), tolerance );
													
							// If all the signs are zero, the planes are coplanar, so skip it
							// If all the signs are positive or negative, the planes do not intersect, so skip it
							// If the signs are not equal, then it looks like an intersection and we are 
							// interested
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
										if ( splitFace( i, segment1, segment2, pTempVars, pEnvironment ) ) {
											// Splitting a face removes it from its current "i" position in
											// the list, and adds the new faces to the end.
											// This means the next face has slid up into the "i" slot
											i -= 1;
											
											// Recheck with a new 'face1'
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
		CSGSolid 		pOtherObject
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Match every face against the other object
		for( CSGFace aFace : mFaces ) {
			// If the face vertices aren't classified to make the simple classify
			if ( aFace.simpleClassify() == false ) {
				// Nothing simple about it, so use the ray trace classification
				aFace.rayTraceClassify( pOtherObject, pTempVars, pEnvironment );
				
				// Mark the vertices
				aFace.v1().mark( aFace.getStatus() );
				aFace.v2().mark( aFace.getStatus() );
				aFace.v3().mark( aFace.getStatus() );
			}
		}
	}

	
	/** Split an individual face
	 
	 	****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5, vectd6
    */	  
	protected boolean splitFace(
		int 			pFaceIndex
	, 	CSGSegment 		pFaceSegment
	, 	CSGSegment 		pOtherSegment
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
//		CSGSegment.CSGSegmentType startType, endType, middleType;
		CSGFace.CSGFaceCollision startCollision, endCollision;
		
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL
		
		// NOTE that pFaceSegment is based on the face at pFaceIndex, so the vertices
		//		come from 'face'.  The other segment is based on the 'other' face, so
		//		its vertices do not really apply.
		CSGFace aFace = mFaces.get( pFaceIndex );
		CSGVertexIOB startVertex = pFaceSegment.getStartVertex();
		CSGVertexIOB endVertex = pFaceSegment.getEndVertex();

		// NOTE that the position vectors can be taken from either segment.  Since
		//		both segments represent different portions of the same intersection
		//		line, the positions apply to both faces.
		Vector3d startPos, endPos;

		// We now need to restrict the intersection 'line' from the planes of the faces
		// to that portion which actually is bounded by the current face	
		if ( pOtherSegment.getStartDistance() > (pFaceSegment.getStartDistance() + tolerance)) {
			// The 'other' segment start is deeper so it determines the start point
//			startType = pFaceSegment.getIntermediateType();
			startPos = pOtherSegment.getStartPosition();
			
			// The other segment collision point is not pertinent.  The face segment can
			// tell us if an edge is involved or not
			startCollision = pFaceSegment.getOtherCollision( startPos );
		} else {
			// This face segment start is deeper, so it determines the start point
//			startType = pFaceSegment.getStartType();
//			if ( startType == CSGSegment.CSGSegmentType.VERTEX ) {
//				startVertex.setStatus( CSGVertexStatus.BOUNDARY );
//			}
			startPos = pFaceSegment.getStartPosition();
			startCollision = pFaceSegment.getStartCollision();
		}		
		if ( pOtherSegment.getEndDistance() < (pFaceSegment.getEndDistance() - tolerance)) {
			// The 'other' segment end is deeper, so it determines the end point
//			endType = pFaceSegment.getIntermediateType();
			endPos = pOtherSegment.getEndPosition();
			
			// The other segment collision point is not pertinent.  The face segment can
			// tell us if an edge is involved or not
			endCollision = pFaceSegment.getOtherCollision( endPos );
		} else {
			// This face segment end is deeper, to it determines the end point
//			endType = pFaceSegment.getEndType();
//			if ( endType == CSGSegment.CSGSegmentType.VERTEX ) {
//				endVertex.setStatus( CSGVertexStatus.BOUNDARY );
//			}
			endPos = pFaceSegment.getEndPosition();
			endCollision = pFaceSegment.getEndCollision();
		}		
//		middleType = pFaceSegment.getIntermediateType();
		
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
				return( false );
				
			case EDGE12:
			case EDGE23:
			case EDGE31:
				// Start through one vertex and then through an edge 
//				if ( startCollision.vertexOnEdge( endCollision ) ) {
					// The vertex is ON the target edge, so we have not crossed the face, 
					// we have a partial overlap along the given edge itself
					// (yes, the result is the same as below without the check for vertexOnEdger)
//					breakFaceInTwo( mFaces.remove( pFaceIndex ), endPos, endCollision, pTempVars, pEnvironment );
//					return( true );
//				} else {
					// We have a line through a vertex, crossing an opposite edge.  The 
					// new point must be on the given edge, so two new pieces will cover it.
					breakFaceInTwo( mFaces.remove( pFaceIndex ), endPos, endCollision, pTempVars, pEnvironment );
					return( true );
//				}
				
			case INTERIOR:
				// Start through one vertex and terminate in the middle of the face.
				// This requires the face to be split into three pieces circling around
				// the interior point.
				breakFaceInThree( mFaces.remove( pFaceIndex ), endPos, pTempVars, pEnvironment );
				return( true );
				
			default:
				// No real collision
				return( false );
			}
			
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
//					breakFaceInTwo( mFaces.remove( pFaceIndex ), startPos, startCollision, pTempVars, pEnvironment );
//					return( true );
//				} else {
					// We have a line through a vertex, crossing an opposite edge.  The 
					// new point must be on the given edge, so two new pieces will cover it.
					breakFaceInTwo( mFaces.remove( pFaceIndex ), startPos, startCollision, pTempVars, pEnvironment );
					return( true );
//				}
				
			case EDGE12:
			case EDGE23:
			case EDGE31:
				if ( startCollision != endCollision ) {
					// Start through one edge and end via another edge.  This cuts the face 
					// into three pieces with two new vertices at the edge split points.
					breakFaceInThree( mFaces.remove( pFaceIndex )
									, startPos, startCollision
									, endPos, endCollision
									, pTempVars, pEnvironment );
					return( true );
				} else {
					// Collision along the same edge.  This cuts the face
					// into three pieces with two new vertices along the same edge.
					breakFaceInThree( mFaces.remove( pFaceIndex )
									, startPos, endPos, startCollision
									, pTempVars, pEnvironment );
					
					return( true );
				}
				
			case INTERIOR:
				// Start through one edge and terminate in the middle of the face.
				// This requires the face to be split into four pieces circling around
				// the interior point.
				breakFaceInFour( mFaces.remove( pFaceIndex )
								, startPos, startCollision
								, endPos
								, pTempVars, pEnvironment );
				return( true );
				
			default:
				// No real collision
				return( false );
			}
			
		case INTERIOR:
			// The start point is in the middle of the face, what about the end?
			switch( endCollision ) {
			case V1:
			case V2:
			case V3:
				// Start from an interior point and end through a vertex.  This
				// cuts the face into three pieces circling around the interior
				// point.
				breakFaceInThree( mFaces.remove( pFaceIndex ), startPos, pTempVars, pEnvironment );
				return( true );
			
			case EDGE12:
			case EDGE23:
			case EDGE31:
				// Start from an interior point and end through an edge.
				// This requires the face to be split into four pieces circling
				// around the interior point.
				breakFaceInFour( mFaces.remove( pFaceIndex )
								, endPos, endCollision
								, startPos
								, pTempVars, pEnvironment );
				return( true );

				
			case INTERIOR:
				// Start and end on points within the interior of the face. Confirm they are
				// a reasonable distance apart.
				if ( ConstructiveSolidGeometry
					.equalVector3d( startPos, endPos, pEnvironment.mEpsilonBetweenPointsDbl ) ) {
					// Start and end are effectively the same, so treat it as a single center point
					breakFaceInThree( mFaces.remove( pFaceIndex ), startPos, pTempVars, pEnvironment );
					return( true );
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
						breakFaceInFive( mFaces.remove( pFaceIndex ), startPos, endPos, pickVertex, pTempVars, pEnvironment );
					} else {
						breakFaceInFive( mFaces.remove( pFaceIndex ), endPos, startPos, pickVertex, pTempVars, pEnvironment );
					}
					return( true );

				}
			
			default:
				// No real collision
				return( false );
			}
			
		default:
			// No real collision
			return( false );
		}
/****************************
		if ( (startType == CSGSegment.CSGSegmentType.VERTEX) 
		&&   (endType == CSGSegment.CSGSegmentType.VERTEX) ) {
			// VERTEX-_______-VERTEX  Collision along two vertices, so nothing needs be split
			return;
		}
		// Have we crossed an edge?
		if ( (startType == CSGSegment.CSGSegmentType.EDGE) 
		||   (middleType == CSGSegment.CSGSegmentType.EDGE)
		||   (endType == CSGSegment.CSGSegmentType.EDGE) ) {
			// Which edge has been touched 
			CSGFaceCollision splitEdge;
			if ( (startVertex == face.v1() && endVertex == face.v2()) 
			||   (startVertex == face.v2() && endVertex == face.v1()) ) {
				splitEdge = CSGFaceCollision.EDGE12;
			} else if ( (startVertex == face.v2() && endVertex == face.v3()) 
				   ||   (startVertex == face.v3() && endVertex == face.v2()) ) {	  
				splitEdge = CSGFaceCollision.EDGE23; 
			} else if ( (startVertex == face.v3() && endVertex == face.v1()) 
				   ||   (startVertex == face.v1() && endVertex == face.v3()) ) {	  
				splitEdge = CSGFaceCollision.EDGE31; 
			} else {
				throw new IllegalStateException( "No EDGE selected for edge collision" );
			}
			// ______-EDGE-______
			if ( middleType == CSGSegment.CSGSegmentType.EDGE ) {
				if ( startType == CSGSegment.CSGSegmentType.VERTEX ) {
					// VERTEX-EDGE-EDGE, where endPos is the collision point on the edge
					breakFaceInTwo( mFaces.remove( pFaceIndex ), endPos, splitEdge, pTempVars, pEnvironment );
					return;
				} else if ( endType == CSGSegment.CSGSegmentType.VERTEX ) {
					// EDGE-EDGE-VERTEX, where startPos is the collision point on the edge
					breakFaceInTwo( mFaces.remove( pFaceIndex ), startPos, splitEdge, pTempVars, pEnvironment );
					return;
				} else {
					// EDGE-EDGE-EDGE, 
//					if ( startDist == endDist ) {
//						// I do not understand this one
//						breakFaceInTwo( mFaces.remove( pFaceIndex ), endPos, splitEdge, pTempVars, pEnvironment );
//					} else {
						if ( (startVertex == face.v1() && endVertex == face.v2()) 
						||   (startVertex == face.v2() && endVertex == face.v3()) 
						||   (startVertex == face.v3() && endVertex == face.v1()) ) {
							// Start position intersects the edge
							breakFaceInThree( pFaceIndex, startPos, endPos, splitEdge, pTempVars, pEnvironment );
						} else {
							// End position intersects the edge
							breakFaceInThree( pFaceIndex, endPos, startPos, splitEdge, pTempVars, pEnvironment );
						}
//					}
				}
				return;
			}
			// ______-FACE-______
			else if ( (startType == CSGSegment.CSGSegmentType.VERTEX) 
				 &&   (endType == CSGSegment.CSGSegmentType.EDGE) ) {
				// VERTEX-FACE-EDGE
				breakFaceInTwo( pFaceIndex, endPos, endVertex, pTempVars, pEnvironment );
			} else if ((startType == CSGSegment.CSGSegmentType.EDGE)
				   &&  (endType == CSGSegment.CSGSegmentType.VERTEX) ) {
				// EDGE-FACE-VERTEX
				breakFaceInTwo( pFaceIndex, startPos, startVertex, pTempVars, pEnvironment );
			}
			// EDGE-FACE-EDGE
			else if ( (startType == CSGSegment.CSGSegmentType.EDGE)
				 &&   (endType == CSGSegment.CSGSegmentType.EDGE) ) {
				breakFaceInThree( pFaceIndex, startPos, endPos, startVertex, endVertex, pTempVars, pEnvironment );
			}
			// EDGE-FACE-FACE
			else if ( (startType == CSGSegment.CSGSegmentType.EDGE)
				 &&   (endType == CSGSegment.CSGSegmentType.FACE) ) {
				breakFaceInFour( pFaceIndex, startPos, endPos, startVertex, pTempVars, pEnvironment );
				//breakFaceInFour( mFaces.remove( pFaceIndex ), startPos, endPos, splitEdge, pTempVars, pEnvironment );
			}
			// FACE-FACE-EDGE
			else if ( (startType == CSGSegment.CSGSegmentType.FACE)
				 &&   (endType == CSGSegment.CSGSegmentType.EDGE) ) {
				breakFaceInFour( pFaceIndex, endPos, startPos, endVertex, pTempVars, pEnvironment );
				//breakFaceInFour( mFaces.remove( pFaceIndex ), endPos, startPos, splitEdge, pTempVars, pEnvironment );
			} else {
				throw new IllegalStateException( "Unsupported EDGE combination" );				
			}
		}
		//VERTEX-FACE-FACE
		else if (startType == CSGSegment.CSGSegmentType.VERTEX && endType == CSGSegment.CSGSegmentType.FACE)
		{
			breakFaceInThree( pFaceIndex, endPos, startVertex, pTempVars, pEnvironment );
		}
		//FACE-FACE-VERTEX
		else if (startType == CSGSegment.CSGSegmentType.FACE && endType == CSGSegment.CSGSegmentType.VERTEX)
		{
			breakFaceInThree( pFaceIndex, startPos, endVertex, pTempVars, pEnvironment );
		}
		//FACE-FACE-FACE
		else if (startType == CSGSegment.CSGSegmentType.FACE && endType == CSGSegment.CSGSegmentType.FACE)
		{
			Vector3d segmentVector = new Vector3d(startPos.x-endPos.x, startPos.y-endPos.y, startPos.z-endPos.z);
						
			// If the intersection segment is a point only...
			if ( (Math.abs( segmentVector.x ) < tolerance)
			&&   (Math.abs( segmentVector.y ) < tolerance)
			&&   (Math.abs( segmentVector.z ) < tolerance) ) {
				breakFaceInThree(  mFaces.remove( pFaceIndex ), startPos, pTempVars, pEnvironment );
				return;
			}
			//gets the vertex more lined with the intersection segment
			Vector3d position1 = face.v1().getPosition();
			Vector3d position2 = face.v2().getPosition();
			Vector3d position3 = face.v3().getPosition();
			
			int linedVertex;
			Vector3d linedVertexPos;
			Vector3d vertexVector;
			
			vertexVector = new Vector3d(endPos.x-position1.x, endPos.y-position1.y, endPos.z-position1.z);
			vertexVector.normalizeLocal();
			double dot1 = Math.abs(segmentVector.dot(vertexVector));
			
			vertexVector = new Vector3d(endPos.x-position2.x, endPos.y-position2.y, endPos.z-position2.z);
			vertexVector.normalizeLocal();
			double dot2 = Math.abs(segmentVector.dot(vertexVector));
			
			vertexVector = new Vector3d(endPos.x-position3.x, endPos.y-position3.y, endPos.z-position3.z);
			vertexVector.normalizeLocal();
			double dot3 = Math.abs(segmentVector.dot(vertexVector));
			
			if (dot1 > dot2 && dot1 > dot3) {
			 	linedVertex = 1;
				linedVertexPos = position1;
			} else if (dot2 > dot3 && dot2 > dot1) {
				linedVertex = 2;
				linedVertexPos = position2;
			} else {
				linedVertex = 3;
				linedVertexPos = position3;
			}
        
			// Now find which of the intersection endpoints is nearest to that vertex.
			if (linedVertexPos.distance(startPos) > linedVertexPos.distance(endPos))
			{
				breakFaceInFive( pFaceIndex, startPos, endPos, linedVertex, pTempVars, pEnvironment );
			}
			else
			{
				breakFaceInFive( pFaceIndex, endPos, startPos, linedVertex, pTempVars, pEnvironment );
			}
		} else {
			throw new IllegalStateException( "Unsupported splitFace combination" );							
		}
*********/
	}
	  
	/** Split a face into two pieces along a vertex and a point on the opposite edge */		
	protected void breakFaceInTwo(
		CSGFace				pFace
	, 	Vector3d 			pNewPos
	, 	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex = addVertex( pNewPos, pFace, pEdge, pTempVars, pEnvironment ); 
						
		switch( pEdge ) {
		case EDGE12:
			//               V2
			//
			//           
			//          New
			//         
			// 
			//		V1                V3
			addFace( pFace.v1(), vertex, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex, pFace.v2(), pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//           
			//                   New
			//         
			// 
			//		V1                V3
			addFace( pFace.v2(), vertex, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex, pFace.v3(), pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//           
			//         
			//         
			// 
			//		V1       New       V3
			addFace( pFace.v3(), vertex, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex, pFace.v1(), pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		}
	}
	
	/** Split into three pieces along two new positions on two given edges */
	protected void breakFaceInThree(
		CSGFace				pFace
	, 	Vector3d 			pEdgePosition1
	,	CSGFaceCollision	pEdgeCollision1
	, 	Vector3d 			pEdgePosition2
	, 	CSGFaceCollision	pEdgeCollision2
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pEdgePosition1, pFace, pEdgeCollision1, pTempVars, pEnvironment );	
		CSGVertexIOB vertex2 = addVertex( pEdgePosition2, pFace, pEdgeCollision2, pTempVars, pEnvironment );
						
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
				addFace( pFace.v1(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, pFace.v2(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v2(), pFace.v3(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				break;
			case EDGE23:
				//               V2
				//
				//          
				//          New1      New2
				//        
				// 
				//		V1                 V3
				addFace( pFace.v1(), vertex1, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, vertex2, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v2(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
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
				addFace( pFace.v2(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, pFace.v3(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v3(), pFace.v1(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				break;
			case EDGE31:
				//               V2
				//
				//          
				//                   New1
				//        
				// 
				//		V1      New2       V3
				addFace( pFace.v2(), vertex1, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, vertex2, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v3(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
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
				addFace( pFace.v3(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, pFace.v1(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v1(), pFace.v2(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				break;
			case EDGE12:
				//               V2
				//
				//          
				//         New2
				//        
				// 
				//		V1      New1       V3
				addFace( pFace.v3(), vertex1, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( vertex1, vertex2, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
				addFace( pFace.v1(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
				break;
			}
			break;
		}
	}
	
	/** Split into three pieces along two new positions on a single edge */
	protected void breakFaceInThree(
		CSGFace				pFace
	, 	Vector3d 			pEdgePosition1
	, 	Vector3d 			pEdgePosition2
	, 	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pEdgePosition1, pFace, pEdge, pTempVars, pEnvironment );	
		CSGVertexIOB vertex2 = addVertex( pEdgePosition2, pFace, pEdge, pTempVars, pEnvironment );
						
		switch( pEdge ) {
		case EDGE12:
			//               V2
			//
			//           New1
			//
			//         New2
			// 
			//		V1                V3
			addFace( pFace.v1(), vertex2, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, vertex1, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex1, pFace.v2(), pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//                  New2
			//
			//                     New1
			// 
			//		V1                V3
			addFace( pFace.v2(), vertex2, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, vertex1, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, pFace.v3(), pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//              
			//
			//                   
			// 
			//		V1  New1    New2   V3
			addFace( pFace.v3(), vertex2, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, vertex1, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, pFace.v1(), pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		}
	}

	/** Split into three pieces based on a single interior point */
	protected void breakFaceInThree(
		CSGFace				pFace
	, 	Vector3d 			pCenterPoint
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {	
		CSGVertexIOB vertex = addVertex( pCenterPoint, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
				
		//               V2
		//
		//           
		//              
		//               New
		// 
		//		V1                V3
		addFace( pFace.v1(), pFace.v2(), vertex, pFace.getMaterialIndex(), pTempVars, pEnvironment );
		addFace( pFace.v2(), pFace.v3(), vertex, pFace.getMaterialIndex(), pTempVars, pEnvironment );
		addFace( pFace.v3(), pFace.v1(), vertex, pFace.getMaterialIndex(), pTempVars, pEnvironment );
	}
	
	/** Split into four pieces from an interior point to a given edge */	
	protected void breakFaceInFour(
		CSGFace				pFace
	, 	Vector3d 			pPositionOnEdge
	, 	CSGFaceCollision	pEdgeCollision
	, 	Vector3d 			pPositionOnFace
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vtxEdge = addVertex( pPositionOnEdge, pFace, pEdgeCollision, pTempVars, pEnvironment );
		CSGVertexIOB vtxCenter = addVertex( pPositionOnFace, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		
		switch( pEdgeCollision ) {
		case EDGE12:
			//               V2
			//
			//           
			//         NewEdge     
			//              
			//              NewCtr
			//		V1                V3
			addFace( pFace.v1(), vtxEdge, vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vtxEdge, pFace.v2(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), pFace.v3(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), pFace.v1(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE23:
			//               V2
			//
			//           
			//                   NewEdge     
			//              
			//              NewCtr
			//		V1                V3
			addFace( pFace.v2(), vtxEdge, vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vtxEdge, pFace.v3(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), pFace.v1(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), pFace.v2(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE31:
			//               V2
			//
			//           
			//                   
			//             NewCtr
			//              
			//		V1     NewEdge     V3
			addFace( pFace.v3(), vtxEdge, vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vtxEdge, pFace.v1(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), pFace.v2(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), pFace.v3(), vtxCenter, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		}
	}
	
	/** Split into five pieces to accommodate two new points that are both in the
	 	interior of the given face. 
	 */
	protected void breakFaceInFive(
		CSGFace				pFace
	, 	Vector3d 			pNewPosition1
	, 	Vector3d 			pNewPosition2
	, 	CSGFaceCollision	pPickVertex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertexIOB vertex1 = addVertex( pNewPosition1, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
		CSGVertexIOB vertex2 = addVertex( pNewPosition2, pFace, CSGFaceCollision.INTERIOR, pTempVars, pEnvironment );
			
		switch( pPickVertex ) {
		case V1:
			addFace( pFace.v2(), pFace.v3(), vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), vertex2, pFace.v1(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), pFace.v1(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
			
		case V2:
			addFace( pFace.v3(), pFace.v1(), vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v3(), vertex2, pFace.v2(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), pFace.v2(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
			
		case V3:
			addFace( pFace.v1(), pFace.v2(), vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), vertex1, vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), vertex2, vertex1, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v1(), vertex2, pFace.v3(), pFace.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( pFace.v2(), pFace.v3(), vertex2, pFace.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		}
	}

	/**
	 * Face breaker for VERTEX-FACE-EDGE / EDGE-FACE-VERTEX
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 * @param endVertex vertex used for splitting 
	 */		
/***
	private void breakFaceInTwo(
		int facePos
	, 	Vector3d newPos
	, 	CSGVertexIOB endVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face, pTempVars, pEnvironment );
					
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex, face.v2(), face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex, face.v3(), face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else
		{
			addFace(face.v3(), vertex, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex, face.v1(), face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
		}
	}
***/
	/**
	 * Face breaker for EDGE-EDGE-EDGE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param splitEdge edge that will be split
	 */
/***
	private void breakFaceInThree(
		int		 facePos
	, 	Vector3d newPos1
	, 	Vector3d newPos2
	, 	CSGFaceCollision	pEdge
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);

		CSGVertexIOB vertex1 = addVertex( newPos1, face, pEdge, pTempVars, pEnvironment );	
		CSGVertexIOB vertex2 = addVertex( newPos2, face, pTempVars, pEnvironment );
						
		switch( pEdge ) {
		case EDGE12:
			addFace( face.v1(), vertex1, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex1, vertex2, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, face.v2(), face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE23:
			addFace( face.v2(), vertex1, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex1, vertex2, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, face.v3(), face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		case EDGE31:
			addFace( face.v3(), vertex1, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex1, vertex2, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace( vertex2, face.v1(), face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			break;
		}
	}
***/		
	/**
	 * Face breaker for VERTEX-FACE-FACE / FACE-FACE-VERTEX
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 * @param endVertex vertex used for the split
	 */
/***
	private void breakFaceInThree(
		int facePos
	, 	Vector3d newPos
	, 	CSGVertexIOB endVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face, pTempVars, pEnvironment );
						
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else
		{
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
	}
***/
	/**
	 * Face breaker for EDGE-FACE-EDGE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param startVertex vertex used the new faces creation
	 * @param endVertex vertex used for the new faces creation
	 */
/***
	private void breakFaceInThree(
		int facePos
	, 	Vector3d newPos1
	, 	Vector3d newPos2
	, 	CSGVertexIOB startVertex
	, 	CSGVertexIOB endVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face, pTempVars, pEnvironment );
		CSGVertexIOB vertex2 = addVertex( newPos2, face, pTempVars, pEnvironment );
						
		if (startVertex.equals(face.v1()) && endVertex.equals(face.v2()))
		{
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), vertex2, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v2(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (startVertex.equals(face.v2()) && endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), vertex1, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex2, face.v2(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (startVertex.equals(face.v2()) && endVertex.equals(face.v3()))
		{
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), vertex2, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v3(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (startVertex.equals(face.v3()) && endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), vertex1, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex2, face.v3(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (startVertex.equals(face.v3()) && endVertex.equals(face.v1()))
		{
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), vertex2, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v1(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else
		{
			addFace(face.v3(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), vertex1, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex2, face.v1(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
	}
	
	private void breakFaceInFour(
		int facePos
	, Vector3d newPos1
	, Vector3d newPos2
	, CSGVertexIOB endVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face, pTempVars, pEnvironment );
		CSGVertexIOB vertex2 = addVertex( newPos2, face, pTempVars, pEnvironment );
		
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v2(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v3(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else
		{
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(vertex1, face.v1(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
	}
***/
	/**
	 * Face breaker for FACE-FACE-FACE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param linedVertex what vertex is more lined with the interersection found
	 */	
/***
	private void breakFaceInFive(
		int facePos
	, Vector3d newPos1
	, Vector3d newPos2
	, int linedVertex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face, pTempVars, pEnvironment );
		CSGVertexIOB vertex2 = addVertex( newPos2, face, pTempVars, pEnvironment );
		
		double cont = 0;		
		if (linedVertex == 1)
		{
			addFace(face.v2(), face.v3(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), vertex2, face.v1(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else if(linedVertex == 2)
		{
			addFace(face.v3(), face.v1(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v3(), vertex2, face.v2(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
		else
		{
			addFace(face.v1(), face.v2(), vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), vertex2, vertex1, face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v1(), vertex2, face.v3(), face.getMaterialIndex(), pTempVars, pEnvironment );
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex(), pTempVars, pEnvironment );
		}
	}
***/
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGSolidRevision
													, sCSGSolidDate
													, pBuffer ) );
	}

}
