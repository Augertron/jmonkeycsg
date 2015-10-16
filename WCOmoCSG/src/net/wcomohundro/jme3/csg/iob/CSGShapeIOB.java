/** Copyright (c) Danilo Balby Silva Castanheira (danbalby@yahoo.com)
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
import java.util.List;

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
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;
import net.wcomohundro.jme3.csg.iob.CSGFace.CSGFaceStatus;

/** Constructive Solid Geometry (CSG)

	Provide a basic 'shape' for CSG processing 
	
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

	Each of the cases above can be analysed and the face split into multiple parts accordingly.
	Once we know we have no overlap, then each face can assessed to see if it is
	1)	Inside the second solid
	2)	Outside the second solid
	3)	Touching the second solid at a boundary
	
	The boolean operation is the collection of the appropriate faces from the two solids.
*/
public class CSGShapeIOB 
	implements CSGShape.CSGShapeProcessor, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeIOBRevision="$Rev$";
	public static final String sCSGShapeIOBDate="$Date$";
	
	/** Default configuration that applies to IOB processing */
	public static CSGEnvironment sDefaultEnvironment 
		= new CSGEnvironment( true, "net.wcomohundro.jme3.csg.iob.CSGShapeIOB");

	/** Canned, immutable empty list of faces */
	protected static final List<CSGFace> sEmptyFaces = new ArrayList<CSGFace>(0);

	
    /** The underlying shape we operate on */
    protected CSGShape				mShape;
	/** The list of faces that make up this shape */
	protected List<CSGFace>			mFaces;
    
    
	/** Basic null constructor */
	public CSGShapeIOB(
	) {
		this( null, sEmptyFaces );
	}
	/** Constructor based on an explicit list of faces, as determined by a blend of shapes */
	protected CSGShapeIOB(
		CSGShape			pForShape
	,	List<CSGFace>		pFaces
	) {
		mShape = pForShape;
		mFaces = pFaces;
	}


	/** Ready a list of shapes for processing */
	@Override
	public List<CSGShape> prepareShapeList(
		List<CSGShape>	pShapeList
	,	CSGEnvironment	pEnvironment
	) {
		// Process the shapes/operations in the order given
		return( pShapeList );
	}
	
	/** Connect to a shape */
	@Override
	public CSGShapeProcessor setShape(
		CSGShape		pForShape
	) {
		mShape = pForShape;
		return( this );
	}

	/** Make a copy of this shape */
	@Override
	public CSGShape.CSGShapeProcessor clone(
		CSGShape	pForShape
	) {
		return( new CSGShapeIOB( pForShape, sEmptyFaces ) );
	}
	
	/** Accessor to the list of faces */
	protected List<CSGFace> getFaces(
		Number			pMaterialIndex
	,	int				pLevelOfDetail
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) { 
		if ( mFaces.isEmpty() && (mShape.getMesh() != null) ) {
			// Generate the faces
			mShape.setMaterialIndex( (pMaterialIndex == null) ? 0 : pMaterialIndex.intValue() );
			mFaces = fromMesh( mShape.getMesh(), mShape.getLocalTransform(), pLevelOfDetail, pTempVars, pEnvironment );
			
		} else if ( !mFaces.isEmpty() 
				&& (pMaterialIndex != null)
				&& (mShape.getMaterialIndex() != pMaterialIndex.intValue()) ) {
			// We have faces, but a different custom material is desired
			throw new IllegalArgumentException( "CSGShape invalid change-of-material" );
		}
		return mFaces; 
	}
	
	/** Add a shape into this one */
	@Override
	public CSGShape union(
		CSGShape		pOther
	,	Number			pOtherMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGFace> thisFaceList 
			= this.getFaces( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pOtherMaterialIndex, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList );
		
		List<CSGFace> newFaceList
			= composeSolid( thisSolid, otherSolid, false
							,	CSGFaceStatus.OUTSIDE, CSGFaceStatus.SAME, CSGFaceStatus.OUTSIDE
							,	pTempVars, pEnvironment );
		
		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList );
		CSGShape aShape = new CSGShape( aHandler, mShape.getOrder() );
		return( aShape );
	}

	/** Subtract a shape from this one */
	@Override
	public CSGShape difference(
		CSGShape		pOther
	,	Number			pOtherMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGFace> thisFaceList 
			= this.getFaces( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pOtherMaterialIndex, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList );
		
		List<CSGFace> newFaceList
			= composeSolid( thisSolid, otherSolid, true
							,	CSGFaceStatus.OUTSIDE, CSGFaceStatus.OPPOSITE, CSGFaceStatus.INSIDE
							,	pTempVars, pEnvironment );
		
		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList );
		CSGShape aShape = new CSGShape( aHandler, mShape.getOrder() );
		return( aShape );
	}

	/** Find the intersection with another shape */
	@Override
	public CSGShape intersection(
		CSGShape		pOther
	,	Number			pOtherMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGFace> thisFaceList 
			= this.getFaces( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment );
		CSGSolid thisSolid = new CSGSolid( thisFaceList );
		
		CSGShapeIOB otherIOB = (CSGShapeIOB)pOther.getHandler( pEnvironment );
		List<CSGFace> otherFaceList 
			= otherIOB.getFaces( pOtherMaterialIndex, 0, pTempVars, pEnvironment );
		CSGSolid otherSolid = new CSGSolid( otherFaceList );
		
		List<CSGFace> newFaceList
			= composeSolid( thisSolid, otherSolid, false
							,	CSGFaceStatus.INSIDE, CSGFaceStatus.SAME, CSGFaceStatus.INSIDE
							,	pTempVars, pEnvironment );

		CSGShapeIOB aHandler = new CSGShapeIOB( null, newFaceList );
		CSGShape aShape = new CSGShape( aHandler, mShape.getOrder() );
		return( aShape );
	}
	
	/** Produce the set of faces that correspond to a given mesh */
	protected List<CSGFace> fromMesh(
		Mesh			pMesh
	,	Transform		pTransform
	,	int				pLevelOfDetail
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
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
		if ( pEnvironment.mStructuralDebug ) {
			switch( meshMode ) {
			case Triangles:
				// This is the only one we can deal with at this time
				break;
			default:
				throw new IllegalArgumentException( "Only Mode.Triangles type mesh is currently supported" );
			}
			if ( posBuffer == null ) {
				throw new IllegalArgumentException( "Mesh lacking Type.Position buffer" );
			}
			if ( normBuffer == null ) {
				throw new IllegalArgumentException( "Mesh lacking Type.Normal buffer" );
			}
		}
		// Work from 3 points which define a triangle
		List<CSGFace> faces = new ArrayList<CSGFace>( idxBuffer.size() / 3 );
		for (int i = 0; i < idxBuffer.size(); i += 3) {
			int idx1 = idxBuffer.get(i);
			int idx2 = idxBuffer.get(i + 1);
			int idx3 = idxBuffer.get(i + 2);
			
			int idx1x3 = idx1 * 3;
			int idx2x3 = idx2 * 3;
			int idx3x3 = idx3 * 3;
			
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
			faces.add( new CSGFace( CSGVertexIOB.makeVertex( pos1, norm1, texCoord1, pTransform, pEnvironment )
								, CSGVertexIOB.makeVertex( pos2, norm2, texCoord2, pTransform, pEnvironment )
								, CSGVertexIOB.makeVertex( pos3, norm3, texCoord3, pTransform, pEnvironment )
								, mShape.getMaterialIndex()
								, pTempVars
								, pEnvironment ) );
		}
		return( faces );
	}

	/** Produce the mesh(es) that corresponds to this shape
	 	The zeroth mesh in the list is the total, composite mesh.
	 	Every other mesh (if present) applies solely to a specific Material.
	  */
	@Override
	public List<Mesh> toMesh(
		int				pMaxMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		List<Mesh> meshList = new ArrayList( pMaxMaterialIndex + 1 );
		
		List<CSGFace> aFaceList = getFaces( null, 0, pTempVars, pEnvironment );
		int anEstimateVertexCount = aFaceList.size() * 3;
		
		List<Vector3f> aPositionList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector3f> aNormalList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector2f> aTexCoordList = new ArrayList<Vector2f>( anEstimateVertexCount  );
		List<Number> anIndexList = new ArrayList<Number>( anEstimateVertexCount );
		
		// Include the master list of all elements
		meshList.add( toMesh( -1, aFaceList, aPositionList, aNormalList, aTexCoordList, anIndexList ) );
		
		// Include per-material meshes
		for( int index = 0; (pMaxMaterialIndex > 0) && (index <= pMaxMaterialIndex); index += 1 ) {
			// The zeroth index is the generic Material, all others are custom Materials
			aPositionList.clear(); aNormalList.clear(); aTexCoordList.clear(); anIndexList.clear();
			Mesh aMesh = toMesh( index, aFaceList, aPositionList, aNormalList, aTexCoordList, anIndexList );
			meshList.add( aMesh );
		}
		return( meshList );
	}
		
	protected Mesh toMesh(
		int					pMaterialIndex
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
			int materialIndex = aFace.getMaterialIndex();
			if ( (pMaterialIndex >= 0) && (materialIndex != pMaterialIndex) ) {
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
				pIndexList.add( vertexPointers.get(0) );
				pIndexList.add( vertexPointers.get(ptr-1) );
				pIndexList.add( vertexPointers.get(ptr) );
			}
		}
		// Use our own buffer setters to optimize access and cut down on object churn
		aMesh.setBuffer( Type.Position, 3, CSGShape.createVector3Buffer( pPositionList ) );
		aMesh.setBuffer( Type.Normal, 3, CSGShape.createVector3Buffer( pNormalList ) );
		aMesh.setBuffer( Type.TexCoord, 2, CSGShape.createVector2Buffer( pTexCoordList ) );
		aMesh.setBuffer( Type.Index, 3, CSGShape.createIndexBuffer( pIndexList ) );

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
		CSGSolid		pSolidA
	,	CSGSolid		pSolidB
	,	boolean			pInvertInteriorB
	,	CSGFaceStatus	pFaceStatus1
	, 	CSGFaceStatus 	pFaceStatus2
	, 	CSGFaceStatus	pFaceStatus3
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGFace> newFaceList = new ArrayList();
		
		// Split the faces so that neither of them intercepts each other
		pSolidA.splitFaces( pSolidB, pTempVars, pEnvironment );
		pSolidB.splitFaces( pSolidA, pTempVars, pEnvironment );
				
		// Classify the faces as being inside or outside the other solid
		pSolidA.classifyFaces( pSolidB, pTempVars, pEnvironment );
		pSolidB.classifyFaces( pSolidA, pTempVars, pEnvironment );
		
		if ( pInvertInteriorB ) {
			pSolidB = pSolidB.invertFaces( CSGFace.CSGFaceStatus.INSIDE );
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
			CSGFaceStatus faceStatus = aFace.getStatus();
			if ( (faceStatus == pFaceStatus1) || (faceStatus == pFaceStatus2) ) {
				// This is a keeper
				pResultList.add( aFace );
			}
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGShapeIOBRevision
													, sCSGShapeIOBDate
													, pBuffer ) );
	}

}
