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

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.plugins.blender.math.Vector3d;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeStatistics;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceStatus;
import net.wcomohundro.jme3.csg.math.CSGVertex;

/** Constructive Solid Geometry (CSG)

 	While struggling with the BSP approach, I stumbled across a Java implementation of a non-BSP
 	algorithm based on the paper:
 		D. H. Laidlaw, W. B. Trumbore, and J. F. Hughes.  
 		"Constructive Solid Geometry for Polyhedral Objects" 
 		SIGGRAPH Proceedings, 1986, p.161. 

	Their approach is to first analyze all the faces in two objects that are being combined.  Any
	faces that intersect are broken into smaller pieces that do not intersect.  Every resultant face
	is then categorized as being Inside, Outside, or on the Boundary of the other object. The
	boolean operation is performed by selecting the appropriately categorized face from the two
	objects.
	
	Unlike BSP, the IOB approach is not recursive, but iterative.  However, it does involve 
	matching every face in one solid with every face in a second solid, so you dealing with
	M * N performance issues.
	
	A Java implementation of this algorithm was made open source by 
		Danilo Balby Silva Castanheira (danbalby@yahoo.com)
	It is based on the Java3D library, not jMonkey.  It has not been actively worked on for 
	several years, and I was unable to get it operational.
	
	Like the fabsterpal BSP code, I have reimplemented this code to run within the jMonkey
	environment.  The original code handled the vertex positions quite well, but did nothing
	for normal and texture coordinates required by jMonkey.  I have done a significant
	rewrite to fully implement IOB within jMonkey (and to better understand the algorithm
	for myself)
	
	
	This handler operates on classifying the faces and vertices of two shapes based on their
	state of: Inside/Outside/Boundary
	
	Once classified, the boolean operators select those appropriate faces for the final shape.
	
	The key design idea is that a given solid is composed of some set of 'faces', where each
	face is defined by a triangle.  When operating on two solids, it is possible to compare
	every face in one solid with every face in the other.  So long as there is no intersection,
	then a face can be left alone.
	
	But should a face in solidA collide with a face in solidB, then we must split the face
	to eliminate collisions.
	
	So how can two triangles collide?  No matter what, the collision can only be along a single
	line.  So how can a line intersect with a face:
	1)	The line is totally outside the bounds of the face and does not intersect at all
 	2)	The line passes through a single vertex of the face
 	3)	The line passes through an edge of the face
 	4)	The line passes through a single vertex and a single edge of the face
 	5)	The line passes through two edges of the face

	Each of the cases above can be analyzed and the face split into multiple parts accordingly.
	Once we know we have no overlap, then each face can assessed to see if it is
	1)	Inside the second solid
	2)	Outside the second solid
	3)	Touching the second solid at a boundary
	
	The boolean operation is the collection of the appropriate faces from the two solids.
*/
public class CSGShapeIOB 
	implements CSGShape.CSGShapeProcessor<CSGEnvironmentIOB>, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeIOBRevision="$Rev$";
	public static final String sCSGShapeIOBDate="$Date$";
	
	/** Default configuration that applies to IOB processing */
	public static CSGEnvironmentIOB sDefaultEnvironment = new CSGEnvironmentIOB();

	/** Canned, immutable empty list of faces */
	protected static final List<CSGFace> sEmptyFaces = new ArrayList<CSGFace>(0);

	
    /** The underlying shape we operate on */
    protected CSGShape				mShape;
	/** The list of faces that make up this shape */
	protected List<CSGFace>			mFaces;
	/** Statistics */
	protected CSGStatsIOB			mStatistics;
    
    
	/** Basic null constructor */
	public CSGShapeIOB(
	) {
		this( null, sEmptyFaces, null );
	}
	/** Constructor based on an explicit list of faces, as determined by a blend of shapes */
	protected CSGShapeIOB(
		CSGShape			pForShape
	,	List<CSGFace>		pFaces
	,	CSGStatsIOB			pStatistics
	) {
		mShape = pForShape;
		mFaces = pFaces;
		mStatistics = pStatistics;
	}

	/** Ready a list of shapes for processing */
	@Override
	public List<CSGShape> prepareShapeList(
		List<CSGShape>		pShapeList
	,	CSGEnvironmentIOB	pEnvironment
	) {
		List<CSGShape> sortedShapes = new ArrayList<>( pShapeList );
		Collections.sort( sortedShapes, new Comparator<CSGShape>() {
			@Override
			public int compare( CSGShape pA, CSGShape pB ) {
				int thisOrder = pA.getOrder(), otherOrder = pB.getOrder();
				return( thisOrder - otherOrder );
			}
		});
		return( sortedShapes );
	}
	
	/** Connect to a shape */
	@Override
	public CSGShapeProcessor setShape(
		CSGShape		pForShape
	) {
		mShape = pForShape;
		if ( mStatistics == null ) {
			// Ensure we have statistics
			CSGElement aParent = pForShape.getParentElement();
			if ( aParent instanceof CSGShape ) {
				// Maybe the parent has something
				CSGShapeIOB parentHandler = (CSGShapeIOB)((CSGShape)aParent).getHandler( null, null );
				if ( parentHandler != null ) {
					this.mStatistics = parentHandler.mStatistics;
				}
			}
			// If we still have not found statistics to share, start from scratch
			if ( mStatistics == null ) {
				mStatistics = new CSGStatsIOB();
			}
		}
		return( this );
	}
	
	/** Statistics about what regenerate is doing */
	@Override
	public CSGShapeStatistics getStaticstics() { return mStatistics; }

	/** Get status about just what regenerate is doing */
	@Override
	public StringBuilder reportStatus( 
		StringBuilder 	pBuffer
	, 	boolean 		pBriefly	
	) {
		int triangleCount = mStatistics.mTriangleCount;
		if ( triangleCount > 0 ) {
			if ( pBriefly ) {
				// Composite
				triangleCount += mStatistics.mFaceCount;
				triangleCount += mStatistics.mClassificationCount;
				triangleCount += mStatistics.mFilterCount;
				pBuffer.append( triangleCount );
			} else {
				// Individual stats
				pBuffer.append( " " ).append( triangleCount );
	
				int faceCount = mStatistics.mFaceCount;
				if ( faceCount > 0 ) {
					pBuffer.append( " / " ).append( faceCount );
					
					int classificationCount = mStatistics.mClassificationCount;
					if ( classificationCount > 0 ) {
						pBuffer.append( " / " ).append( classificationCount );
						
						int filterCount = mStatistics.mFilterCount;
						if ( filterCount > 0 ) {
							pBuffer.append( " / " ).append( filterCount );
						}
					}
				}
				pBuffer.append( " " );
			}
		}
		return( pBuffer );
	}

	/** Refresh this handler and ensure it is ready for blending */
	@Override
	public CSGShapeProcessor refresh(
	) {
		if ( mShape.isBooleanBlend() ) {
			// The shape is a result of a prior blend, so retain the faces
		} else {
			// Ensure we start with an empty list of faces, which will be rebuilt
			// from the underlying mesh/subshapes
			mFaces.clear();
		}
		return( this );
	}
	
	/** Apply a transform to a resultant shape */
	@Override
	public void applyTransform(
		Transform		pTransform
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Every face must be adjusted
		for( CSGFace aFace : mFaces ) {
			aFace.applyTransform( pTransform, pTempVars, pEnvironment );
		}
	}


	/** Make a copy of this shape */
	@Override
	public CSGShape.CSGShapeProcessor clone(
		CSGShape	pForShape
	) {
		CSGShapeIOB aHandler = new CSGShapeIOB( pForShape, sEmptyFaces, this.mStatistics );
		if ( pForShape.isBooleanBlend() ) {
			// The shape is the result of a prior blend, so retain its faces
			aHandler.mFaces = new ArrayList( this.mFaces.size() );
			aHandler.mFaces.addAll( this.mFaces );
		} else {
			// Assume the faces are rebuilt from scratch
		}
		return( aHandler );
	}

	
	/** Accessor to the list of faces */
	protected List<CSGFace> getFaces(
		CSGMeshManager		pMeshManager
	,	int					pLevelOfDetail
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	) { 
		Mesh aMesh;
		if ( mFaces.isEmpty() && ((aMesh = mShape.getMesh()) != null) ) {
			// Generate the faces
			mFaces = fromMesh( aMesh
								, mShape.getCSGTransform( pEnvironment )
								, pMeshManager
								, pLevelOfDetail
								, pTempVars
								, pEnvironment );
		}
		return mFaces; 
	}
	
	/** Add a shape into this one */
	@Override
	public CSGShape union(
		CSGShape			pOther
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		List<CSGFace> thisFaceList 
			= this.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList, mStatistics );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment, this );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList, mStatistics );
		
		List<CSGFace> newFaceList
			= composeSolid( pOther, thisSolid, otherSolid, false
							,	CSGFaceStatus.OUTSIDE, CSGFaceStatus.SAME, CSGFaceStatus.OUTSIDE
							,	pTempVars, pEnvironment );
		
		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList, this.mStatistics );
		CSGShape aShape = new CSGShape( aHandler
										, this.mShape.getName()
										, this.mShape.getOrder()
										, this.mShape.getError()
										, pOther.getError() );
		return( aShape );
	}

	/** Subtract a shape from this one */
	@Override
	public CSGShape difference(
		CSGShape			pOther
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		List<CSGFace> thisFaceList 
			= this.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList, mStatistics );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment, this );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList, mStatistics );
		
		List<CSGFace> newFaceList
			= composeSolid( pOther, thisSolid, otherSolid, true
							,	CSGFaceStatus.OUTSIDE, CSGFaceStatus.OPPOSITE, CSGFaceStatus.INSIDE
							,	pTempVars, pEnvironment );
		
		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList, this.mStatistics );
		CSGShape aShape = new CSGShape( aHandler
										, this.mShape.getName()
										, this.mShape.getOrder()
										, this.mShape.getError()
										, pOther.getError() );
		return( aShape );
	}

	/** Find the intersection with another shape */
	@Override
	public CSGShape intersection(
		CSGShape			pOther
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		List<CSGFace> thisFaceList 
			= this.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList, mStatistics );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment, this );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList, mStatistics );
		
		List<CSGFace> newFaceList
			= composeSolid( pOther, thisSolid, otherSolid, false
							,	CSGFaceStatus.INSIDE, CSGFaceStatus.SAME, CSGFaceStatus.INSIDE
							,	pTempVars, pEnvironment );

		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList, this.mStatistics );
		CSGShape aShape = new CSGShape( aHandler
										, this.mShape.getName()
										, this.mShape.getOrder()
										, this.mShape.getError()
										, pOther.getError() );
		return( aShape );
	}
	
	/** Find the merge with another shape */
	@Override
	public CSGShape merge(
		CSGShape			pOther
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		// This is a simple addition of all the 'other' faces into this face list
		if ( this.mShape.isValid() ) {
			if ( pOther.isValid() ) {
				List<CSGFace> thisFaceList 
					= this.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
				
				CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment, this );
				List<CSGFace> otherFaceList 
					= otherIOB.getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		
				this.mFaces.addAll( otherFaceList );
			} else {
				// This shape is not valid if merged with an invalid shape
				this.mShape.setError( new CSGConstructionException( CSGErrorCode.INVALID_SHAPE
									,	"CSGShapeIOB.merge - blended invalid shape"
									,	this.mShape
									,	pOther.getError() ) );
			}
		}
		return( this.mShape );
	}
	
	/** Produce the set of faces that correspond to a given mesh */
	protected List<CSGFace> fromMesh(
		Mesh				pMesh
	,	Transform			pTransform
	,	CSGMeshManager		pMeshManager
	,	int					pLevelOfDetail
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	) throws CSGConstructionException {
		// Convert the mesh in to appropriate polygons
	    VertexBuffer indexBuffer = pMesh.getBuffer( VertexBuffer.Type.Index );
		IndexBuffer idxBuffer = null;
		if ( (pLevelOfDetail > 0) && (pLevelOfDetail < pMesh.getNumLodLevels()) ) {
			// Look for the given level of detail
			VertexBuffer lodBuffer = pMesh.getLodLevel( pLevelOfDetail );
			if ( lodBuffer != null ) {
				idxBuffer = IndexBuffer.wrapIndexBuffer( lodBuffer.getData() );
			}
		}
		if ( idxBuffer == null ) {
			// Use the 'standard'
			idxBuffer = pMesh.getIndexBuffer();
		}
		Mode meshMode = pMesh.getMode();
		FloatBuffer posBuffer = pMesh.getFloatBuffer( Type.Position );
		FloatBuffer normBuffer = pMesh.getFloatBuffer( Type.Normal );
		FloatBuffer texCoordBuffer = pMesh.getFloatBuffer( Type.TexCoord );
		
		int triangleCount, iAdjust, invalidFaceCount = 0;
		int vertexCount = idxBuffer.size();
		
		switch( meshMode ) {
		case Triangles:
			// Every 3 vertices specify a single triangle
			triangleCount = vertexCount / 3;
			iAdjust = 3;
			break;
		case TriangleStrip:
			// The first 3 vertices specify a triangle, while subsequent vertices are 
			// combined with the previous two.  But always keep in rotational order, so
			// ABCDE...  yields ABC, CBD, CDE, EDF, EFG
			triangleCount = vertexCount - 2;
			iAdjust = 1;
			break;
		default:
			throw new CSGConstructionException( CSGErrorCode.INVALID_MESH
											,	"Mesh type not supported: " + meshMode );
		}
		if ( posBuffer == null ) {
			throw new CSGConstructionException( CSGErrorCode.INVALID_MESH
											,	"Mesh lacking Type.Position buffer" );
		}
		if ( normBuffer == null ) {
			throw new CSGConstructionException( CSGErrorCode.INVALID_MESH
											,	"Mesh lacking Type.Normal buffer" );
		}
		// Work from 3 points which define a triangle
		List<CSGFace> faces = new ArrayList<CSGFace>( triangleCount );
		
		// Looking for 'common' vertices can speed up the I/O/B processing, but if
		// we are talking an extremely large count, then the overhead is not worth it.
		Map<Vector3d,CSGVertexIOB> aVertexList;
		if ( vertexCount < 5000 ) {
			aVertexList = new HashMap( vertexCount );
		} else {
			// Too many vertices to track, the HashMap wastes more time than it saves
			aVertexList = null;
		}
		CSGFace aFace = null;
		for( int i = 0, j = 0; j < triangleCount; i += iAdjust, j += 1) {
			mStatistics.mTriangleCount += 1;
			
			int idx1 = idxBuffer.get(i);
			int idx2 = idxBuffer.get(i + 1);
			int idx3 = idxBuffer.get(i + 2);
			
			if ( (meshMode == Mode.TriangleStrip) && ((j % 2) > 0) ) {
				// index 1 and 2 toggle every other time in the strip
				int temp = idx1;
				idx1 = idx2;
				idx2 = temp;
			}
			// Each position/normal entry is 3 floats for x/y/z
			int idx1x3 = idx1 * 3;
			int idx2x3 = idx2 * 3;
			int idx3x3 = idx3 * 3;
			
			// Each texture entry is 2 floats for x/y
			int idx1x2 = idx1 * 2;
			int idx2x2 = idx2 * 2;
			int idx3x2 = idx3 * 2;
			
			// Extract the positions
			Vector3f pos1 = new Vector3f( posBuffer.get( idx1x3 )
										, posBuffer.get( idx1x3 + 1)
										, posBuffer.get( idx1x3 + 2) );
			Vector3f pos2 = new Vector3f( posBuffer.get( idx2x3 )
										, posBuffer.get( idx2x3 + 1)
										, posBuffer.get( idx2x3 + 2) );
			Vector3f pos3 = new Vector3f( posBuffer.get( idx3x3 )
										, posBuffer.get( idx3x3 + 1)
										, posBuffer.get( idx3x3 + 2) );
			// Extract the normals
			Vector3f norm1 = new Vector3f( normBuffer.get( idx1x3 )
										 , normBuffer.get( idx1x3 + 1)
										 , normBuffer.get( idx1x3 + 2) );
			Vector3f norm2 = new Vector3f( normBuffer.get( idx2x3 )
										 , normBuffer.get( idx2x3 + 1)
										 , normBuffer.get( idx2x3 + 2) );
			Vector3f norm3 = new Vector3f( normBuffer.get( idx3x3)
										 , normBuffer.get( idx3x3 + 1)
										 , normBuffer.get( idx3x3 + 2) );

			// Extract the Texture Coordinates
			// Based on an interaction via the SourceForge Ticket system, another user has informed
			// me that UV texture coordinates are optional.... so be it
			Vector2f texCoord1, texCoord2, texCoord3;
			if ( texCoordBuffer != null ) {
				texCoord1 = new Vector2f( texCoordBuffer.get( idx1x2)
											, texCoordBuffer.get( idx1x2 + 1) );
				texCoord2 = new Vector2f( texCoordBuffer.get( idx2x2)
											, texCoordBuffer.get( idx2x2 + 1) );
				texCoord3 = new Vector2f( texCoordBuffer.get( idx3x2)
											, texCoordBuffer.get( idx3x2 + 1) );
			} else {
				texCoord1 = texCoord2 = texCoord3 = Vector2f.ZERO;
			}
			// And build the appropriate face
			aFace = new CSGFace( CSGVertexIOB.makeVertex( aFace, pos1, norm1, texCoord1, pTransform, pEnvironment )
								, CSGVertexIOB.makeVertex( aFace, pos2, norm2, texCoord2, pTransform, pEnvironment )
								, CSGVertexIOB.makeVertex( aFace, pos3, norm3, texCoord3, pTransform, pEnvironment )
								, mShape.getMeshIndex( pMeshManager, j )
								, pTempVars
								, pEnvironment );
			if ( aFace.isValid() ) {
				// Retain this face
				// Look for 'shared' vertices in the hope that since two vertices with the same 
				// position, by definition, share the same IOB status, then the classification pass 
				// would be faster. The overhead of a sequential scan through all the faces proved
				// to be too slow.
				// However, matching vertices via a HashMap gives us faster processing.
				if ( aVertexList != null ) {
					aFace.matchVertices( aVertexList, pEnvironment );
				}
				faces.add( aFace );
			} else if ( meshMode == Mode.Triangles ) {
				// We expect reasonable triangles in Triangle mode
				if ( pEnvironment.mStructuralDebug ) {
					CSGEnvironment.sLogger.log( Level.WARNING
											, "Invalid face in mesh[" + mShape.getName() + "] " + aFace );
				}
				invalidFaceCount += 1;
			} else {
				// Other triangle modes may use degenerate triangles on purpose
				invalidFaceCount += 1;
			}
		}
		if ( invalidFaceCount > 0 ) {
			CSGEnvironment.sLogger.log( Level.WARNING
			, "Degenerate triangles(" + invalidFaceCount + ") in mesh:" + mShape.getName() );			
		}
		return( faces );
	}

	/** Produce the mesh(es) that corresponds to this shape
	 	The generic mesh is the total, composite mesh.
	 	
	 	Other meshes are produced for every other Mesh Index defined, representing
	 	unique Materials and/or Lighting.
	  */
	@Override
	public void toMesh(
		CSGMeshManager		pMeshManager
	,	boolean				pProduceSubelements
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	) {
		List<CSGFace> aFaceList = getFaces( pMeshManager, 0, pTempVars, pEnvironment );
		int anEstimateVertexCount = aFaceList.size() * 3;
		
		List<Vector3f> aPositionList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector3f> aNormalList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector2f> aTexCoordList = new ArrayList<Vector2f>( anEstimateVertexCount  );
		List<Number> anIndexList = new ArrayList<Number>( anEstimateVertexCount );
		
		// Include the master list of all elements
		Mesh aMesh = toMesh( -1, aFaceList, aPositionList, aNormalList, aTexCoordList, anIndexList );
		pMeshManager.registerMasterMesh( aMesh, this.mShape.getName() );
		
		if ( pProduceSubelements ) {
			// Produce the meshes for all the sub elements
			for( int index = 0; index <= pMeshManager.getMeshCount(); index += 1 ) {
				// The zeroth index is the generic Material, all others are custom Meshes
				aPositionList.clear(); aNormalList.clear(); aTexCoordList.clear(); anIndexList.clear();
				aMesh = toMesh( index, aFaceList, aPositionList, aNormalList, aTexCoordList, anIndexList );
				pMeshManager.registerMesh( aMesh, new Integer( index ) );
			}
		}
	}
		
	protected Mesh toMesh(
		int					pMeshIndex
	,	List<CSGFace>		pFaceList
	,	List<Vector3f> 		pPositionList
	,	List<Vector3f> 		pNormalList
	,	List<Vector2f> 		pTexCoordList
	,	List<Number> 		pIndexList
	) {
		Mesh aMesh = new Mesh();
		// Walk the list of all polygons, collecting all appropriate vertices
		int indexPtr = 0;
		for( CSGFace aFace : pFaceList ) {
			// Does this polygon have a custom material?
			int meshIndex = aFace.getMeshIndex();
			if ( (pMeshIndex >= 0) && (meshIndex != pMeshIndex) ) {
				// Only material-specific polygons are interesting
				continue;
			}
			List<CSGVertex> aVertexList = aFace.getVertices();
			int aVertexCount = aVertexList.size();
			
			// Include every vertex in this polygon
			List<Number> vertexPointers = new ArrayList<Number>( aVertexCount );
			for( CSGVertex aVertex : aVertexList ) {
				pPositionList.add( aVertex.getPositionFlt() );
				pNormalList.add( aVertex.getNormalFlt() );
				pTexCoordList.add( aVertex.getTextureCoordinate() );
				
				vertexPointers.add( indexPtr++ );
			}
			// Produce as many triangles (all starting from vertex 0) as needed to
			// include all the vertices  (3 yields 1 triangle, 4 yields 2 triangles, etc)
			for( int ptr = 2; ptr < aVertexCount; ptr += 1 ) {
				mStatistics.mTriangleCount += 1;

				pIndexList.add( vertexPointers.get(0) );
				pIndexList.add( vertexPointers.get(ptr-1) );
				pIndexList.add( vertexPointers.get(ptr) );
			}
		}
		// Use our own buffer setters to optimize access and cut down on object churn
		aMesh.setBuffer( Type.Position, 3, CSGShape.createVector3Buffer( pPositionList ) );
		aMesh.setBuffer( Type.Normal, 3, CSGShape.createVector3Buffer( pNormalList ) );
		aMesh.setBuffer( Type.TexCoord, 2, CSGShape.createVector2Buffer( pTexCoordList ) );
		CSGShape.createIndexBuffer( pIndexList, aMesh );

		aMesh.updateBound();
		aMesh.updateCounts();
		return( aMesh );
	}
	
	/** Composes the list of faces of a solid based on the status of the faces in the
	 	given lists:
	 		INSIDE, OUTSIDE, SAME, OPPOSITE
	 
	  	@param pFaceStatus1  - status expected for the first solid faces
	 	@param pFaceStatus2  - 'other' status expected for the first solid faces
	 							(expected a status for the faces coincident with second list of faces)
	 	@param pFaceStatus3   - status expected for the second solid faces
	 */
	protected List<CSGFace> composeSolid(
		CSGShape			pOtherShape
	,	CSGSolid			pSolidA
	,	CSGSolid			pSolidB
	,	boolean				pInvertInteriorB
	,	CSGFaceStatus		pFaceStatus1
	, 	CSGFaceStatus 		pFaceStatus2
	, 	CSGFaceStatus		pFaceStatus3
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentIOB	pEnvironment
	)  throws CSGConstructionException {
		List<CSGFace> newFaceList = new ArrayList();
		
		// Split the faces so that neither of them intercepts each other
		pSolidA.splitFaces( pSolidB, pTempVars, pEnvironment );
		pSolidB.splitFaces( pSolidA, pTempVars, pEnvironment );
				
		// Classify the faces as being inside or outside the other solid
		pSolidA.classifyFaces( pSolidB, pTempVars, pEnvironment );
		pSolidB.classifyFaces( pSolidA, pTempVars, pEnvironment );
		
		if ( pInvertInteriorB ) {
			pSolidB = pSolidB.invertFaces( CSGFace.CSGFaceStatus.INSIDE, pEnvironment );
		}
		// Select faces that fit with the desired status  
		filterFaces( newFaceList, pSolidA.getFaces(), pFaceStatus1, pFaceStatus2 );
		filterFaces( newFaceList, pSolidB.getFaces(), pFaceStatus3, pFaceStatus3 );
		
		return( newFaceList );
	}
	protected void filterFaces(
		List<CSGFace>	pResultList
	,	List<CSGFace>	pSourceList
	,	CSGFaceStatus	pFaceStatus1
	,	CSGFaceStatus	pFaceStatus2
	) {
		// Walk the faces and match the status
		for( CSGFace aFace : pSourceList ) {
			mStatistics.mFilterCount += 1;
			
			CSGFaceStatus faceStatus = aFace.getStatus();
			if ( (faceStatus == pFaceStatus1) || (faceStatus == pFaceStatus2) ) {
				// This is a keeper
				pResultList.add( aFace );
			} else if ( faceStatus == CSGFaceStatus.UNKNOWN ) {
				// This face has slipped through the cracks
				CSGEnvironment.sLogger.log( Level.WARNING
				, "filterFaces: unknown face status in mesh:" + mShape.getName() );							
			}
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGShapeIOBRevision
													, sCSGShapeIOBDate
													, pBuffer ) );
	}

}
