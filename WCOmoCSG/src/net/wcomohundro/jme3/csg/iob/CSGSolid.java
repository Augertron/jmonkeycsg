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
	/** Statistics tracking how the faces of this solid are split */
	protected int				mOriginalFaceCount;
	
	
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
	
	/**
	 * Method used to add a face properly for internal methods
	 * 
	 * @param v1 a face vertex
	 * @param v2 a face vertex
	 * @param v3 a face vertex
	 */
	protected CSGFace addFace(
		CSGVertexIOB 	v1
	, 	CSGVertexIOB 	v2
	, 	CSGVertexIOB 	v3
	, 	int 			pMaterialIndex
	) {
		Vector3d aPosition = v1.getPosition();
		Vector3d bPosition = v2.getPosition();
		Vector3d cPosition = v3.getPosition();
		
		if ( (aPosition.distance( bPosition ) > 1e-7)
		&&   (bPosition.distance( cPosition ) > 1e-7)
		&&	 (cPosition.distance( aPosition ) > 1e-7) ) {
			CSGFace aFace = new CSGFace( v1, v2, v3, pMaterialIndex );
			mFaces.add( aFace );
			return( aFace );
		} else {
			return null;
		}
	}
	
	/** Add an appropriate boundary vertex */
	protected CSGVertexIOB addVertex(
		Vector3d	pNewPosition
	, 	CSGFace		pFace
	) {
		// NOTE that once upon a time, a list of unique vertices was kept and scanned
		//		to only add a vertex that was not yet defined.
		//		I could find no good reason for this, and the overhead of this separate
		//		list was substantial.
		Vector3d aNormal = new Vector3d();
		Vector2f aTexCoord = new Vector2f();
		pFace.extrapolate( pNewPosition, aNormal, aTexCoord );
		CSGVertexIOB vertex = new CSGVertexIOB( pNewPosition, aNormal, aTexCoord, CSGVertexStatus.BOUNDARY );
		return( vertex );	
	}

	/**
	 * Split faces so that none face is intercepted by a face of other object
	 * 
	 * @param pSolid the other object 3d used to make the split 
	 */
	public void splitFaces(
		CSGSolid 		pSolid
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGRay line;
		CSGSegment segment1, segment2;
		double distFace1Vert1, distFace1Vert2, distFace1Vert3, distFace2Vert1, distFace2Vert2, distFace2Vert3;
		int signFace1Vert1, signFace1Vert2, signFace1Vert3, signFace2Vert1, signFace2Vert2, signFace2Vert3;
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;
		
		// Check if the objects bounds overlap
		CSGBounds thisBound = this.getBounds();
		CSGBounds otherBound = pSolid.getBounds();
		if ( thisBound.overlap( otherBound, pEnvironment ) ) {			
			// Check each face in this solid
			//	NOTE that we iterate with an index, since we dynamically adjust 'i'
			//		 as a face is split
			for( int i = 0; i < mFaces.size(); i += 1 ) {
				CSGFace face1 = mFaces.get( i );
				
				// Check if object1 face and object2 overlap ...
				CSGBounds thisFaceBound = face1.getBound();
				if ( thisFaceBound.overlap( otherBound, pEnvironment ) ) {
					//for each object2 face...
					for( CSGFace face2 : pSolid.getFaces() ) {
						//if object1 face bound and object2 face bound overlap...  
						CSGBounds otherFaceBound = face2.getBound();
						if ( thisFaceBound.overlap( otherFaceBound, pEnvironment ) ) {
							//PART I - DO TWO POLIGONS INTERSECT?
							//POSSIBLE RESULTS: INTERSECT, NOT_INTERSECT, COPLANAR
							
							//distance from the face1 vertices to the face2 plane
							distFace1Vert1 = face2.computeDistance( face1.v1() );
							distFace1Vert2 = face2.computeDistance( face1.v2() );
							distFace1Vert3 = face2.computeDistance( face1.v3() );
							
							//distances signs from the face1 vertices to the face2 plane 
							signFace1Vert1 = (distFace1Vert1 > tolerance)
												? 1 
												: (distFace1Vert1 < -tolerance) ? -1 : 0; 
							signFace1Vert2 = (distFace1Vert2 > tolerance)
												? 1 
												: (distFace1Vert2 < -tolerance) ? -1 : 0;
							signFace1Vert3 = (distFace1Vert3 > tolerance)
												? 1 
												: (distFace1Vert3 < -tolerance) ? -1 : 0;
							
							//if all the signs are zero, the planes are coplanar
							//if all the signs are positive or negative, the planes do not intersect
							//if the signs are not equal...
							if (!(signFace1Vert1==signFace1Vert2 && signFace1Vert2==signFace1Vert3))
							{
								//distance from the face2 vertices to the face1 plane
								distFace2Vert1 = face1.computeDistance( face2.v1() );
								distFace2Vert2 = face1.computeDistance( face2.v2() );
								distFace2Vert3 = face1.computeDistance( face2.v3() );
								
								//distances signs from the face2 vertices to the face1 plane
								signFace2Vert1 = (distFace2Vert1 > tolerance)
													? 1 
													: (distFace2Vert1 < -tolerance) ? -1 : 0; 
								signFace2Vert2 = (distFace2Vert2 > tolerance)
													? 1 
													: (distFace2Vert2 < -tolerance) ? -1 : 0;
								signFace2Vert3 = (distFace2Vert3 > tolerance)
													? 1 
													: (distFace2Vert3 < -tolerance) ? -1 : 0;
								//if the signs are not equal...
								if (!(signFace2Vert1==signFace2Vert2 && signFace2Vert2==signFace2Vert3))
								{
									line = new CSGRay( face1, face2, pEnvironment );
							
									//intersection of the face1 and the plane of face2
									segment1 = new CSGSegment(line, face1, signFace1Vert1, signFace1Vert2, signFace1Vert3, pTempVars, pEnvironment );
																	
									//intersection of the face2 and the plane of face1
									segment2 = new CSGSegment(line, face2, signFace2Vert1, signFace2Vert2, signFace2Vert3, pTempVars, pEnvironment );
																
									//if the two segments intersect...
									if( segment1.intersect( segment2, pEnvironment ) ) {
										//PART II - SUBDIVIDING NON-COPLANAR POLYGONS
										this.splitFace( i, segment1, segment2, pEnvironment );
																			
										//prevent from infinite loop (with a loss of mFaces...)
										//if(numFacesStart*20<getNumFaces())
										//{
										//	System.out.println("possible infinite loop situation: terminating faces split");
										//	return;
										//}
								
										// if the face in the position isn't the same, there was a break 
										if ( face1 != mFaces.get(i) ) {
											// if the generated solid is equal the origin...
											int lastFaceIndex = mFaces.size() -1;
											if ( face1.equals( mFaces.get( lastFaceIndex ) ) ) {
												// Return it to its position and jump it
												if ( i != lastFaceIndex ) {
													mFaces.remove( lastFaceIndex );
													mFaces.add( i, face1 );
												} else {
													continue;
												}
											} else {
												// Proceed with the next face
												i--;
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
	}
	
	/** Classify faces as being inside, outside or on boundary of other object */
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

	
	/** Split an individual face  */	  
	protected void splitFace(
		int 		facePos
	, 	CSGSegment segment1
	, 	CSGSegment segment2
	,	CSGEnvironment	pEnvironment
	) {
		CSGVertexIOB startPosVertex, endPosVertex;
		Vector3d startPos, endPos;
		CSGSegment.CSGSegmentType startType, endType, middleType;
		double startDist, endDist;
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL
		
		CSGFace face = mFaces.get( facePos );
		CSGVertexIOB startVertex = segment1.getStartVertex();
		CSGVertexIOB endVertex = segment1.getEndVertex();
		
		// Select the deeper starting point 		
		if ( segment2.getStartDistance() > (segment1.getStartDistance() + tolerance)) {
			startDist = segment2.getStartDistance();
			startType = segment1.getIntermediateType();
			startPos = segment2.getStartPosition();
		} else {
			startDist = segment1.getStartDistance();
			startType = segment1.getStartType();
			startPos = segment1.getStartPosition();
		}		
		// Select the deeper ending point
		if ( segment2.getEndDistance() < (segment1.getEndDistance() - tolerance)) {
			endDist = segment2.getEndDistance();
			endType = segment1.getIntermediateType();
			endPos = segment2.getEndPosition();
		} else {
			endDist = segment1.getEndDistance();
			endType = segment1.getEndType();
			endPos = segment1.getEndPosition();
		}		
		middleType = segment1.getIntermediateType();
		
		//set vertex to BOUNDARY if it is start type		
		if (startType == CSGSegment.CSGSegmentType.VERTEX)
		{
			startVertex.setStatus( CSGVertexStatus.BOUNDARY );
		}
				
		//set vertex to BOUNDARY if it is end type
		if (endType == CSGSegment.CSGSegmentType.VERTEX)
		{
			endVertex.setStatus( CSGVertexStatus.BOUNDARY );
		}
		
		//VERTEX-_______-VERTEX 
		if (startType == CSGSegment.CSGSegmentType.VERTEX && endType == CSGSegment.CSGSegmentType.VERTEX)
		{
			return;
		}
		
		//______-EDGE-______
		else if (middleType == CSGSegment.CSGSegmentType.EDGE)
		{
			//gets the edge 
			int splitEdge;
			if ((startVertex == face.v1() && endVertex == face.v2()) || (startVertex == face.v2() && endVertex == face.v1()))
			{
				splitEdge = 1;
			}
			else if ((startVertex == face.v2() && endVertex == face.v3()) || (startVertex == face.v3() && endVertex == face.v2()))
			{	  
				splitEdge = 2; 
			} 
			else
			{
				splitEdge = 3;
			} 
			
			//VERTEX-EDGE-EDGE
			if (startType == CSGSegment.CSGSegmentType.VERTEX)
			{
				breakFaceInTwo(facePos, endPos, splitEdge);
				return;
			}
			
			//EDGE-EDGE-VERTEX
			else if (endType == CSGSegment.CSGSegmentType.VERTEX)
			{
				breakFaceInTwo(facePos, startPos, splitEdge);
				return;
			}
        
			// EDGE-EDGE-EDGE
			else if (startDist == endDist)
			{
				breakFaceInTwo(facePos, endPos, splitEdge);
			}
			else
			{
				if((startVertex == face.v1() && endVertex == face.v2()) || (startVertex == face.v2() && endVertex == face.v3()) || (startVertex == face.v3() && endVertex == face.v1()))
				{
					breakFaceInThree(facePos, startPos, endPos, splitEdge);
				}
				else
				{
					breakFaceInThree(facePos, endPos, startPos, splitEdge);
				}
			}
			return;
		}
		
		//______-FACE-______
		
		//VERTEX-FACE-EDGE
		else if (startType == CSGSegment.CSGSegmentType.VERTEX && endType == CSGSegment.CSGSegmentType.EDGE)
		{
			breakFaceInTwo(facePos, endPos, endVertex);
		}
		//EDGE-FACE-VERTEX
		else if (startType == CSGSegment.CSGSegmentType.EDGE && endType == CSGSegment.CSGSegmentType.VERTEX)
		{
			breakFaceInTwo(facePos, startPos, startVertex);
		}
		//VERTEX-FACE-FACE
		else if (startType == CSGSegment.CSGSegmentType.VERTEX && endType == CSGSegment.CSGSegmentType.FACE)
		{
			breakFaceInThree(facePos, endPos, startVertex);
		}
		//FACE-FACE-VERTEX
		else if (startType == CSGSegment.CSGSegmentType.FACE && endType == CSGSegment.CSGSegmentType.VERTEX)
		{
			breakFaceInThree(facePos, startPos, endVertex);
		}
		//EDGE-FACE-EDGE
		else if (startType == CSGSegment.CSGSegmentType.EDGE && endType == CSGSegment.CSGSegmentType.EDGE)
		{
			breakFaceInThree(facePos, startPos, endPos, startVertex, endVertex);
		}
		//EDGE-FACE-FACE
		else if (startType == CSGSegment.CSGSegmentType.EDGE && endType == CSGSegment.CSGSegmentType.FACE)
		{
			breakFaceInFour(facePos, startPos, endPos, startVertex);
		}
		//FACE-FACE-EDGE
		else if (startType == CSGSegment.CSGSegmentType.FACE && endType == CSGSegment.CSGSegmentType.EDGE)
		{
			breakFaceInFour(facePos, endPos, startPos, endVertex);
		}
		//FACE-FACE-FACE
		else if (startType == CSGSegment.CSGSegmentType.FACE && endType == CSGSegment.CSGSegmentType.FACE)
		{
			Vector3d segmentVector = new Vector3d(startPos.x-endPos.x, startPos.y-endPos.y, startPos.z-endPos.z);
						
			//if the intersection segment is a point only...
			if ( (Math.abs( segmentVector.x ) < tolerance)
			&&   (Math.abs( segmentVector.y ) < tolerance)
			&&   (Math.abs( segmentVector.z ) < tolerance) ) {
				breakFaceInThree(facePos, startPos);
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
				breakFaceInFive(facePos, startPos, endPos, linedVertex);
			}
			else
			{
				breakFaceInFive(facePos, endPos, startPos, linedVertex);
			}
		}
	}
	  
	/**
	 * Face breaker for VERTEX-EDGE-EDGE / EDGE-EDGE-VERTEX
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 * @param edge that will be split 
	 */		
	private void breakFaceInTwo(int facePos, Vector3d newPos, int splitEdge)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face ); 
						
		if (splitEdge == 1)
		{
			addFace(face.v1(), vertex, face.v3(), face.getMaterialIndex());
			addFace(vertex, face.v2(), face.v3(), face.getMaterialIndex());
		}
		else if (splitEdge == 2)
		{
			addFace(face.v2(), vertex, face.v1(), face.getMaterialIndex());
			addFace(vertex, face.v3(), face.v1(), face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), vertex, face.v2(), face.getMaterialIndex());
			addFace(vertex, face.v1(), face.v2(), face.getMaterialIndex());
		}
	}
	
	/**
	 * Face breaker for VERTEX-FACE-EDGE / EDGE-FACE-VERTEX
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 * @param endVertex vertex used for splitting 
	 */		
	private void breakFaceInTwo(int facePos, Vector3d newPos, CSGVertexIOB endVertex)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face );
					
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex, face.v3(), face.getMaterialIndex());
			addFace(vertex, face.v2(), face.v3(), face.getMaterialIndex());
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex, face.v1(), face.getMaterialIndex());
			addFace(vertex, face.v3(), face.v1(), face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), vertex, face.v2(), face.getMaterialIndex());
			addFace(vertex, face.v1(), face.v2(), face.getMaterialIndex());
		}
	}
	
	/**
	 * Face breaker for EDGE-EDGE-EDGE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param splitEdge edge that will be split
	 */
	private void breakFaceInThree(int facePos, Vector3d newPos1, Vector3d newPos2, int splitEdge)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face );	
		CSGVertexIOB vertex2 = addVertex( newPos2, face );
						
		if (splitEdge == 1)
		{
			addFace(face.v1(), vertex1, face.v3(), face.getMaterialIndex());
			addFace(vertex1, vertex2, face.v3(), face.getMaterialIndex());
			addFace(vertex2, face.v2(), face.v3(), face.getMaterialIndex());
		}
		else if (splitEdge == 2)
		{
			addFace(face.v2(), vertex1, face.v1(), face.getMaterialIndex());
			addFace(vertex1, vertex2, face.v1(), face.getMaterialIndex());
			addFace(vertex2, face.v3(), face.v1(), face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), vertex1, face.v2(), face.getMaterialIndex());
			addFace(vertex1, vertex2, face.v2(), face.getMaterialIndex());
			addFace(vertex2, face.v1(), face.v2(), face.getMaterialIndex());
		}
	}
		
	/**
	 * Face breaker for VERTEX-FACE-FACE / FACE-FACE-VERTEX
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 * @param endVertex vertex used for the split
	 */
	private void breakFaceInThree(int facePos, Vector3d newPos, CSGVertexIOB endVertex)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face );
						
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex());
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex());
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex());
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex());
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex());
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex());
			addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex());
			addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex());
		}
	}
	
	/**
	 * Face breaker for EDGE-FACE-EDGE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param startVertex vertex used the new faces creation
	 * @param endVertex vertex used for the new faces creation
	 */
	private void breakFaceInThree(int facePos, Vector3d newPos1, Vector3d newPos2, CSGVertexIOB startVertex, CSGVertexIOB endVertex)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face );
		CSGVertexIOB vertex2 = addVertex( newPos2, face );
						
		if (startVertex.equals(face.v1()) && endVertex.equals(face.v2()))
		{
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v1(), vertex2, face.v3(), face.getMaterialIndex());
			addFace(vertex1, face.v2(), vertex2, face.getMaterialIndex());
		}
		else if (startVertex.equals(face.v2()) && endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v1(), vertex1, face.v3(), face.getMaterialIndex());
			addFace(vertex2, face.v2(), vertex1, face.getMaterialIndex());
		}
		else if (startVertex.equals(face.v2()) && endVertex.equals(face.v3()))
		{
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v2(), vertex2, face.v1(), face.getMaterialIndex());
			addFace(vertex1, face.v3(), vertex2, face.getMaterialIndex());
		}
		else if (startVertex.equals(face.v3()) && endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v2(), vertex1, face.v1(), face.getMaterialIndex());
			addFace(vertex2, face.v3(), vertex1, face.getMaterialIndex());
		}
		else if (startVertex.equals(face.v3()) && endVertex.equals(face.v1()))
		{
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v3(), vertex2, face.v2(), face.getMaterialIndex());
			addFace(vertex1, face.v1(), vertex2, face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v3(), vertex1, face.v2(), face.getMaterialIndex());
			addFace(vertex2, face.v1(), vertex1, face.getMaterialIndex());
		}
	}
		
	/**
	 * Face breaker for FACE-FACE-FACE (a point only)
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos new vertex position
	 */
	private void breakFaceInThree(int facePos, Vector3d newPos)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex = addVertex( newPos, face );
				
		addFace(face.v1(), face.v2(), vertex, face.getMaterialIndex());
		addFace(face.v2(), face.v3(), vertex, face.getMaterialIndex());
		addFace(face.v3(), face.v1(), vertex, face.getMaterialIndex());
	}
	
	/**
	 * Face breaker for EDGE-FACE-FACE / FACE-FACE-EDGE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param endVertex vertex used for the split
	 */	
	private void breakFaceInFour(int facePos, Vector3d newPos1, Vector3d newPos2, CSGVertexIOB endVertex)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face );
		CSGVertexIOB vertex2 = addVertex( newPos2, face );
		
		if (endVertex.equals(face.v1()))
		{
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex());
			addFace(vertex1, face.v2(), vertex2, face.getMaterialIndex());
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex());
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex());
		}
		else if (endVertex.equals(face.v2()))
		{
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex());
			addFace(vertex1, face.v3(), vertex2, face.getMaterialIndex());
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex());
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex());
		}
		else
		{
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex());
			addFace(vertex1, face.v1(), vertex2, face.getMaterialIndex());
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex());
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex());
		}
	}
	
	/**
	 * Face breaker for FACE-FACE-FACE
	 * 
	 * @param facePos face position on the faces array
	 * @param newPos1 new vertex position
	 * @param newPos2 new vertex position 
	 * @param linedVertex what vertex is more lined with the interersection found
	 */		
	private void breakFaceInFive(int facePos, Vector3d newPos1, Vector3d newPos2, int linedVertex)
	{
		CSGFace face = mFaces.get(facePos);
		mFaces.remove(facePos);
		
		CSGVertexIOB vertex1 = addVertex( newPos1, face );
		CSGVertexIOB vertex2 = addVertex( newPos2, face );
		
		double cont = 0;		
		if (linedVertex == 1)
		{
			addFace(face.v2(), face.v3(), vertex1, face.getMaterialIndex());
			addFace(face.v2(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v3(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v2(), vertex2, face.v1(), face.getMaterialIndex());
			addFace(face.v3(), face.v1(), vertex2, face.getMaterialIndex());
		}
		else if(linedVertex == 2)
		{
			addFace(face.v3(), face.v1(), vertex1, face.getMaterialIndex());
			addFace(face.v3(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v1(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v3(), vertex2, face.v2(), face.getMaterialIndex());
			addFace(face.v1(), face.v2(), vertex2, face.getMaterialIndex());
		}
		else
		{
			addFace(face.v1(), face.v2(), vertex1, face.getMaterialIndex());
			addFace(face.v1(), vertex1, vertex2, face.getMaterialIndex());
			addFace(face.v2(), vertex2, vertex1, face.getMaterialIndex());
			addFace(face.v1(), vertex2, face.v3(), face.getMaterialIndex());
			addFace(face.v2(), face.v3(), vertex2, face.getMaterialIndex());
		}
	}

	/** Operate on all faces within this solid, ensuring they do not overlap with the
		faces of another solid
	 */
/*****
	public void splitFaces(
		CSGSolid		pOther
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		mOriginalFaceCount = mFaces.size();
		int faceLimit = mOriginalFaceCount * 10;
		
		// We split only if there is some broad overlap	
		CSGBounds otherBounds = pOther.getBounds();
		if ( this.getBounds().overlap( otherBounds, pEnvironment ) ) {
			// Operate on each face in this solid.
			// NOTE that we dynamically manipulate the for loop index counter to adapt
			//		processing for the changing elements in the mFaces list.
			for( int i = 0; i < mFaces.size(); i += 1 ) {
				// Quick and Dirty limiter on indefinite action
				if ( i > faceLimit ) {
					return;
				}
				// Operate on this face only if it overlaps with the broad bounds of the other solid
				CSGFace aFace = mFaces.get( i );
				if ( aFace.getBound().overlap( otherBounds, pEnvironment ) ) {
					// Match this face against every face in the other solid
					for( CSGFace bFace : pOther.getFaces() ) {
						// Action required only if the broad bounds of the faces overlap
						if ( aFace.getBound().overlap( bFace.getBound(), pEnvironment ) ) {
							// PART I - DO TWO POLYGONS INTERSECT?
							// Match all vertices in this face against the other face
							
							//  if all the signs are zero, the planes are coplanar
							//  if all the signs are positive or negative, the planes do not intersect
							//  if the signs are not equal, then we have to deal with the intersection
							int signFace1Vert1 = bFace.computePosition( aFace.getVertex( 0 ), pEnvironment );
							int signFace1Vert2 = bFace.computePosition( aFace.getVertex( 1 ), pEnvironment );
							int signFace1Vert3 = bFace.computePosition( aFace.getVertex( 2 ), pEnvironment );
							
							if ( !(signFace1Vert1==signFace1Vert2 && signFace1Vert2==signFace1Vert3) ) {
								// Match all vertices in the other face against this face
								int signFace2Vert1 = aFace.computePosition( bFace.getVertex( 0 ), pEnvironment );
								int signFace2Vert2 = aFace.computePosition( bFace.getVertex( 1 ), pEnvironment );
								int signFace2Vert3 = aFace.computePosition( bFace.getVertex( 2 ), pEnvironment );
													
								// Like above, there is only an overlap if signs are not equal
								if ( !(signFace2Vert1==signFace2Vert2 && signFace2Vert2==signFace2Vert3) ) {
									// Break this face apart along the intersection of the two active faces
									CSGRay aLine = aFace.computeIntersect( bFace, pEnvironment );
							
									// Intersection of the face1 and the plane of face2
									CSGSegment segment1 = new CSGSegment( aLine
																		, aFace
																		, signFace1Vert1
																		, signFace1Vert2
																		, signFace1Vert3
																		, pTempVars
																		, pEnvironment );
																	
									// Intersection of the face2 and the plane of face1
									CSGSegment segment2 = new CSGSegment( aLine
																		, bFace
																		, signFace2Vert1
																		, signFace2Vert2
																		, signFace2Vert3
																		, pTempVars
																		, pEnvironment );
																
									// If the two segments intersect...
									if ( segment1.intersect( segment2, pEnvironment ) ) {
										//PART II - SUBDIVIDING NON-COPLANAR POLYGONS
										this.splitFace( i, segment1, segment2, pTempVars, pEnvironment );
	
										// If the face in the current position isn't the same, there was a break 
										if ( aFace != mFaces.get(i) ) {
											// Check out the end slot
											int j = mFaces.size() -1;
											if ( aFace.equals( mFaces.get( j ) ) ) {
												// The face has slid to the end of the list 
												if ( i != j ) {
													// Restore this face to its original position
													mFaces.remove( j );
													mFaces.add( i, aFace);
												} else {
													// Already at the end of the loop, just leave it
													continue;
												}
											} else {
												// Cycle again, accounting for the face removed
												i--;
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
	}
*****/

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
