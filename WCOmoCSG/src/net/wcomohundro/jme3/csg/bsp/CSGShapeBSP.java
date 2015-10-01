/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014 jMonkeyEngine
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
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg.bsp;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGPolygon;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;
import net.wcomohundro.jme3.csg.bsp.CSGPartition;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.Mesh.Mode;
import com.jme3.material.Material;
import com.jme3.material.MatParamTexture;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

/** Constructive Solid Geometry (CSG)
 
	Provide a basic BSP 'shape processing' for CSG
 
 	----- From Evan Wallace -----
 	All CSG operations are implemented in terms of two functions, clipTo() and invert(), 
 	which remove parts of a BSP tree inside another BSP tree and swap solid and empty space, 
 	respectively. 
 	
 	To find the union of a and b, we want to remove everything in a inside b and everything 
 	in b inside a, then combine polygons from a and b into one solid:

		a.clipTo(b);
		b.clipTo(a);
		a.build(b.allPolygons());

	The only tricky part is handling overlapping coplanar polygons in both trees. The code 
	above keeps both copies, but we need to keep them in one tree and remove them in the other 
	tree. To remove them from b we can clip the inverse of b against a. The code for union now 
	looks like this:

		a.clipTo(b);
		b.clipTo(a);
		b.invert();
		b.clipTo(a);
		b.invert();
		a.build(b.allPolygons());

	Subtraction and intersection naturally follow from set operations. If union is A | B, 
	subtraction is A - B = ~(~A | B) and intersection is A & B = ~(~A | ~B) where ~ is 
	the complement operator.
	
 */
public class CSGShapeBSP
	implements CSGShape.CSGShapeProcessor, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeBSPRevision="$Rev$";
	public static final String sCSGShapeBSPDate="$Date$";

	/** Default configuration that applies to BSP processing */
	public static CSGEnvironment sDefaultEnvironment = new CSGEnvironment( false );

	/** Canned, immutable empty list of polygons */
	protected static final List<CSGPolygon> sEmptyPolygons = new ArrayList<CSGPolygon>(0);
	

    /** The underlying shape we operate on */
    protected CSGShape					mShape;
	/** BSP hierarchy level where the partitioning became corrupt */
    protected int						mCorruptLevel;
    /** Count of vertices 'lost' while constructing this shape */
    protected int						mLostVertices;
	/** The list of polygons that make up this shape */
	protected List<CSGPolygon>			mPolygons;

	
	/** Basic null constructor */
	public CSGShapeBSP(
	) {
		this( null, sEmptyPolygons );
	}

	/** Constructor based on an explicit list of polygons, as determined by a blend of shapes */
	protected CSGShapeBSP(
		CSGShape			pForShape
	,	List<CSGPolygon>	pPolygons
	) {
		mShape = pForShape;
		mPolygons = pPolygons;
	}
	
	/** Ready a list of shapes for processing */
	@Override
	public List<CSGShape> prepareShapeList(
		List<CSGShape>	pShapeList
	,	CSGEnvironment	pEnvironment
	) {
		List<CSGShape> sortedShapes = new ArrayList<>( pShapeList );
		Collections.sort( sortedShapes, new Comparator<CSGShape>() {
			public int compare( CSGShape pA, CSGShape pB ) {
				int thisOrder = pA.getOrder(), otherOrder = pB.getOrder();
				
				if ( thisOrder == otherOrder ) {
					// CSG should be applied inner-level as follows: Union -> Intersection -> Difference
					switch( pA.getOperator() ) {
					case UNION:
						switch( pB.getOperator() ) {
						case UNION: return( 0 );		// Same as other UNION
						default: return( -1 );			// Before all others
						}
						
					case INTERSECTION:
						switch( pB.getOperator() ) {
						case UNION: return( 1 );		// After UNION
						case INTERSECTION: return( 0 );	// Same as other INTERSECTION
						default: return( -1 );			// Before all others
						}
						
					case DIFFERENCE:
						switch( pB.getOperator() ) {
						case UNION:
						case INTERSECTION: 
							return( 1 );				// After UNION/INTERSECTION
						case DIFFERENCE: return( 0 );	// Same as other DIFFERENCE
						default: return( -1 );			// Before all others
						}
						
					case SKIP:
						switch( pB.getOperator() ) {
						case SKIP: return( 0 );			// Same as other SKIP
						default: return( 1 );			// After all others
						}
					}
					// If we fall out the above, then we come before
					return( -1 );
				}
				return( thisOrder - otherOrder );
			}
		});
		return( sortedShapes );
	}
		
	/** The shape is 'valid' so long as its BSP hierarchy is not corrupt */
	public boolean isValid() { return( mCorruptLevel == 0 ); }
	public int whereCorrupt() { return( mCorruptLevel ); }
	public void setCorrupt(
		CSGPartition	pPartitionA
	,	CSGPartition	pPartitionB
	) {
		int whereCorrupt;
		whereCorrupt = pPartitionA.whereCorrupt();
		if ( whereCorrupt > mCorruptLevel ) mCorruptLevel = whereCorrupt;
		whereCorrupt = pPartitionB.whereCorrupt();
		if ( whereCorrupt > mCorruptLevel ) mCorruptLevel = whereCorrupt;
		
		mLostVertices = pPartitionA.getLostVertexCount( true ) + pPartitionB.getLostVertexCount( true );
	}
	/** Accessor to how many vertices where 'lost' during construction */
	public int getLostVertexCount() { return mLostVertices; }
	
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
		return( new CSGShapeBSP( pForShape, sEmptyPolygons ) );
	}
	
	/** Accessor to the list of polygons */
	protected List<CSGPolygon> getPolygons(
		Number			pMaterialIndex
	,	int				pLevelOfDetail
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) { 
		if ( mPolygons.isEmpty() && (mShape.getMesh() != null) ) {
			// Generate the polygons
			mShape.setMaterialIndex( (pMaterialIndex == null) ? 0 : pMaterialIndex.intValue() );
			mPolygons = fromMesh( mShape.getMesh(), mShape.getLocalTransform(), pLevelOfDetail, pTempVars, pEnvironment );
			
		} else if ( !mPolygons.isEmpty() 
				&& (pMaterialIndex != null)
				&& (mShape.getMaterialIndex() != pMaterialIndex.intValue()) ) {
			// We have polygons, but a different custom material is desired
			throw new IllegalArgumentException( "CSGShape invalid change-of-material" );
		}
		return mPolygons; 
	}
	
	/** Add a shape into this one */
	@Override
	public CSGShape union(
		CSGShape		pOther
	,	Number			pOtherMaterialIndex
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		CSGPartition a = new CSGPartition( this
											, this.getPolygons( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment );
		CSGPartition b = new CSGPartition( otherBSP
											, otherBSP.getPolygons( pOtherMaterialIndex, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		
		a.clipTo( b, pTempVars, pEnvironment );
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		a.buildHierarchy( b.allPolygons( null ), pTempVars, pEnvironment );

		CSGShapeBSP aHandler = new CSGShapeBSP( null, a.allPolygons( null ) );
		aHandler.setCorrupt( a, b );
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
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		CSGPartition a = new CSGPartition( this
											, this.getPolygons( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		CSGPartition b = new CSGPartition( otherBSP
											, otherBSP.getPolygons( pOtherMaterialIndex, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		
		a.invert();
		a.clipTo( b, pTempVars, pEnvironment );
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		a.buildHierarchy( b.allPolygons( null ), pTempVars, pEnvironment );
		a.invert();

		CSGShapeBSP aHandler = new CSGShapeBSP( null, a.allPolygons( null ) );
		aHandler.setCorrupt( a, b );
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
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		CSGPartition a = new CSGPartition( this
											, this.getPolygons( mShape.getMaterialIndex(), 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
	    CSGPartition b = new CSGPartition( otherBSP
	    									, otherBSP.getPolygons( pOtherMaterialIndex, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		
	    a.invert();
	    b.clipTo( a, pTempVars, pEnvironment );
	    b.invert();
	    a.clipTo( b, pTempVars, pEnvironment );
	    b.clipTo( a, pTempVars, pEnvironment );
	    a.buildHierarchy( b.allPolygons( null ), pTempVars, pEnvironment );
	    a.invert();

		CSGShapeBSP aHandler = new CSGShapeBSP( null, a.allPolygons( null ) );
		aHandler.setCorrupt( a, b );
		CSGShape aShape = new CSGShape( aHandler, mShape.getOrder() );
        return( aShape );
	}

	/** Produce the set of polygons that correspond to a given mesh */
	protected List<CSGPolygon> fromMesh(
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
		List<CSGPolygon> polygons = new ArrayList<CSGPolygon>( idxBuffer.size() / 3 );
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
			// Construct the vertices that define the points of the triangle
			CSGVertex[] aVertexList = new CSGVertex[ 3 ];
			aVertexList[0] = CSGVertex.makeVertex( pos1, norm1, texCoord1, pTransform, pEnvironment );
			aVertexList[1] = CSGVertex.makeVertex( pos2, norm2, texCoord2, pTransform, pEnvironment );
			aVertexList[2] = CSGVertex.makeVertex( pos3, norm3, texCoord3, pTransform, pEnvironment );
			
			// And build the appropriate polygon (assuming the vertices are far enough apart to be significant)
			int polyCount
				= CSGPolygon.addPolygon( polygons, aVertexList, mShape.getMaterialIndex(), pTempVars, pEnvironment );
		}
		return( polygons );
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
		
		List<CSGPolygon> aPolyList = getPolygons( null, 0, pTempVars, pEnvironment );
		int anEstimateVertexCount = aPolyList.size() * 3;
		
		List<Vector3f> aPositionList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector3f> aNormalList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector2f> aTexCoordList = new ArrayList<Vector2f>( anEstimateVertexCount  );
		List<Number> anIndexList = new ArrayList<Number>( anEstimateVertexCount );
		
		// Include the master list of all elements
		meshList.add( toMesh( -1, aPolyList, aPositionList, aNormalList, aTexCoordList, anIndexList ) );
		
		// Include per-material meshes
		for( int index = 0; (pMaxMaterialIndex > 0) && (index <= pMaxMaterialIndex); index += 1 ) {
			// The zeroth index is the generic Material, all others are custom Materials
			aPositionList.clear(); aNormalList.clear(); aTexCoordList.clear(); anIndexList.clear();
			Mesh aMesh = toMesh( index, aPolyList, aPositionList, aNormalList, aTexCoordList, anIndexList );
			meshList.add( aMesh );
		}
		return( meshList );
	}
		
	protected Mesh toMesh(
		int					pMaterialIndex
	,	List<CSGPolygon>	pPolyList
	,	List<Vector3f> 		pPositionList
	,	List<Vector3f> 		pNormalList
	,	List<Vector2f> 		pTexCoordList
	,	List<Number> 		pIndexList
	) {
		Mesh aMesh = new Mesh();
		
		// Walk the list of all polygons, collecting all appropriate vertices
		int indexPtr = 0;
		for( CSGPolygon aPolygon : pPolyList ) {
			// Does this polygon have a custom material?
			int materialIndex = aPolygon.getMaterialIndex();
			if ( (pMaterialIndex >= 0) && (materialIndex != pMaterialIndex) ) {
				// Only material-specific polygons are interesting
				continue;
			}
			List<CSGVertex> aVertexList = aPolygon.getVertices();
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

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGShapeBSPRevision
													, sCSGShapeBSPDate
													, pBuffer ) );
	}

}
