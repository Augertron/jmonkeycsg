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
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

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
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.material.Material;
import com.jme3.material.MatParamTexture;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

/** Constructive Solid Geometry (CSG)
 
	Provide a basic 'shape' for CSG processing 
 
 	A CSG Shape is based on a generated set of polygons that describe the faces of the shape
 	(think Mesh for the CSG world).  Any shape can be CSG blended with another shape via
 	union, difference, and intersection.  The result is a new CSG Shape with its own set of 
 	polygons.
 	
 	The basic Shape can be generated by a supplied list of Polygons, but externally, it is
 	based on a JME 'shape', like a Box, Sphere, Cylinder, ...
 	In such a case, the polygons are built from the Mesh behind the JME Shape.
 	
 	The CSGShape acts like a Geometry so that transformations can be applied to the shape
 	before any CSG blending occurs.  I do not believe that Geometry based lights are of
 	use at this level, but it would be interesting to think of per-shape based 
 	textures/materials.
 	
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
public class CSGShape 
	extends Geometry
	implements Comparable<CSGShape>, Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeRevision="$Rev$";
	public static final String sCSGShapeDate="$Date$";

	/** Canned, immutable empty list of polygons */
	protected static final List<CSGPolygon> sEmptyPolygons = new ArrayList<CSGPolygon>(0);
	
	/** Service to create a vector buffer for a given List */
    public static FloatBuffer createVector3Buffer(
    	List<Vector3f> 	pVectors
    ) {
        FloatBuffer aBuffer = BufferUtils.createVector3Buffer( pVectors.size() );
        for( Vector3f aVector : pVectors ) {
            if ( aVector != null ) {
            	aBuffer.put( aVector.x ).put( aVector.y ).put( aVector.z );
            } else {
            	aBuffer.put(0).put(0).put(0);
            }
        }
        aBuffer.flip();
        return aBuffer;
    }
    public static FloatBuffer createVector2Buffer(
    	List<Vector2f> 	pVectors
    ) {
        FloatBuffer aBuffer = BufferUtils.createVector2Buffer( pVectors.size() );
        for( Vector2f aVector : pVectors ) {
            if ( aVector != null ) {
            	aBuffer.put( aVector.x ).put( aVector.y );
            } else {
            	aBuffer.put(0).put(0);
            }
        }
        aBuffer.flip();
        return aBuffer;
    }
    public static ShortBuffer createIndexBuffer(
    	List<Number> 	pIndices
    ) {
    	ShortBuffer aBuffer = BufferUtils.createShortBuffer( pIndices.size() );
        for( Number aValue : pIndices ) {
            if ( aValue != null ) {
            	aBuffer.put( aValue.shortValue() );
            } else {
            	aBuffer.put( (short)0 );
            }
        }
        aBuffer.flip();
        return aBuffer;
    }

	
	/** The list of polygons that make up this shape */
	protected List<CSGPolygon>			mPolygons;
	/** The operator applied to this shape as it is added into the geometry */
	protected CSGGeometry.CSGOperator	mOperator;
	/** Arbitrary 'ordering' of operations within the geometry */
	protected int						mOrder;
	/** Index that selects a custom material */
	protected int						mMaterialIndex;

	
	/** Basic null constructor */
	public CSGShape(
	) {
		this( sEmptyPolygons, 0 );
	}
	/** Constructor based on a mesh */
	public CSGShape(
		String	pShapeName
	,	Mesh	pMesh
	) {
		this( pShapeName, pMesh, 0 );
	}
	public CSGShape(
		String	pShapeName
	,	Mesh	pMesh
	,	int		pOrder
	) {
		super( pShapeName, pMesh );
		mPolygons = sEmptyPolygons;
		mOrder = pOrder;
		mOperator = CSGGeometry.CSGOperator.UNION;
	}
	/** Constructor based on an explicit list of polygons */
	protected CSGShape(
		List<CSGPolygon>	pPolygons
	,	int					pOrder
	) {
		super( null );
		mPolygons = pPolygons;
		mOrder = pOrder;
		mOperator = CSGGeometry.CSGOperator.UNION;
	}
		
	/** Make a copy of this shape */
	@Override
	public CSGShape clone(
	) {
		return( clone( 0 ) );
	}
	public CSGShape clone(
		Number		pMaterialIndex
	) {
		CSGShape aClone;
		
		if ( this.mesh == null ) {
			// NOTE that a shallow copy of the immutable polygons is acceptable
			List<CSGPolygon> newPolyList = new ArrayList<CSGPolygon>( mPolygons );
			aClone = new CSGShape( newPolyList, mOrder );
		} else {
			// Base it on the mesh (but do not clone the material)
			aClone = (CSGShape)super.clone( false );
			aClone.setOrder( mOrder );
		}
		aClone.setOperator( mOperator );
		aClone.setMaterialIndex( pMaterialIndex );
		return( aClone );
	}
	
	/** Accessor to the list of polygons */
	public List<CSGPolygon> getPolygons(
		Number	pMaterialIndex
	) { 
		if ( mPolygons.isEmpty() && (this.mesh != null) ) {
			// Generate the polygons
			this.mMaterialIndex = (pMaterialIndex == null) ? 0 : pMaterialIndex.intValue();
			mPolygons = fromMesh( this.mesh, this.getLocalTransform() );
			
		} else if ( !mPolygons.isEmpty() 
				&& (pMaterialIndex != null)
				&& (this.mMaterialIndex != pMaterialIndex.intValue()) ) {
			// We have polygons, but a different custom material is desired
			throw new IllegalArgumentException( "CSGShape invalid change-of-material" );
		}
		return mPolygons; 
	}
	
	/** Accessor to the operator */
	public CSGGeometry.CSGOperator getOperator() { return mOperator; }
	public void setOperator(
		CSGGeometry.CSGOperator	pOperator
	) {
		mOperator = pOperator;
	}
	
	/** Accessor to the order of operations */
	public int getOrder() { return mOrder; }
	public void setOrder(
		int		pOrder
	) {
		mOrder = pOrder;
	}

	/** Accessor to the custom material index */
	public int getMaterialIndex() { return mMaterialIndex; }
	public void setMaterialIndex(
		int		pMaterialIndex
	) {
		mMaterialIndex = pMaterialIndex;
	}
	public void setMaterialIndex(
		Number	pMaterialIndex
	) {
		mMaterialIndex = (pMaterialIndex == null) ? 0 : pMaterialIndex.intValue();
	}

	/** Add a shape into this one */
	public CSGShape union(
		CSGShape	pOther
	,	Number		pOtherMaterialIndex
	) {
		CSGPartition a = new CSGPartition( getPolygons( this.mMaterialIndex ) );
		CSGPartition b = new CSGPartition( pOther.getPolygons( pOtherMaterialIndex ) );
		
        // Avoid some object churn by using the thread-specific 'temp' variables
        TempVars vars = TempVars.get();
        try {
			a.clipTo( b, vars.vect1, vars.vect2d );
			b.clipTo( a, vars.vect1, vars.vect2d );
			b.invert();
			b.clipTo( a, vars.vect1, vars.vect2d );
			b.invert();
			a.buildHierarchy( b.allPolygons( null ), vars.vect1, vars.vect2d, 0 );
        } finally {
        	vars.release();
        }
		return( new CSGShape( a.allPolygons( null ), this.getOrder() ));
	}
	
	/** Subtract a shape from this one */
	public CSGShape difference(
		CSGShape	pOther
	,	Number		pOtherMaterialIndex
	) {
		CSGPartition a = new CSGPartition( getPolygons( this.mMaterialIndex ) );
		CSGPartition b = new CSGPartition( pOther.getPolygons( pOtherMaterialIndex ) );
		
        // Avoid some object churn by using the thread-specific 'temp' variables
        TempVars vars = TempVars.get();
        try {
			a.invert();
			a.clipTo( b, vars.vect1, vars.vect2d );
			b.clipTo( a, vars.vect1, vars.vect2d );
			b.invert();
			b.clipTo( a, vars.vect1, vars.vect2d );
			b.invert();
			a.buildHierarchy( b.allPolygons( null ), vars.vect1, vars.vect2d, 0 );
			a.invert();
        } finally {
        	vars.release();
        }
		return( new CSGShape( a.allPolygons( null ), this.getOrder() ) );
	}

	/** Find the intersection with another shape */
	public CSGShape intersection(
		CSGShape	pOther
	,	Number		pOtherMaterialIndex
	) {
		CSGPartition a = new CSGPartition( getPolygons( this.mMaterialIndex ) );
	    CSGPartition b = new CSGPartition( pOther.getPolygons( pOtherMaterialIndex ) );
		
        // Avoid some object churn by using the thread-specific 'temp' variables
        TempVars vars = TempVars.get();
        try {
		    a.invert();
		    b.clipTo( a, vars.vect1, vars.vect2d );
		    b.invert();
		    a.clipTo( b, vars.vect1, vars.vect2d );
		    b.clipTo( a, vars.vect1, vars.vect2d);
		    a.buildHierarchy( b.allPolygons( null ), vars.vect1, vars.vect2d, 0 );
		    a.invert();
        } finally {
        	vars.release();
        }
		return( new CSGShape( a.allPolygons( null ), this.getOrder() ) );
	}

	/** Produce the set of polygons that correspond to a given mesh */
	protected List<CSGPolygon> fromMesh(
		Mesh		pMesh
	,	Transform	pTransform
	) {
		// Convert the mesh in to appropriate polygons
		IndexBuffer idxBuffer = pMesh.getIndexBuffer();
		FloatBuffer posBuffer = pMesh.getFloatBuffer(Type.Position);
		FloatBuffer normBuffer = pMesh.getFloatBuffer(Type.Normal);
		FloatBuffer texCoordBuffer = pMesh.getFloatBuffer(Type.TexCoord);
		
		// Work from 3 points which define a triangle
		List<CSGPolygon> polygons = new ArrayList<CSGPolygon>( idxBuffer.size() / 3 );
		for (int i = 0; i < idxBuffer.size(); i += 3) {
			int idx1 = idxBuffer.get(i);
			int idx2 = idxBuffer.get(i + 1);
			int idx3 = idxBuffer.get(i + 2);
			
			// Extract the positions
			Vector3f pos1 = new Vector3f(posBuffer.get(idx1 * 3), posBuffer.get((idx1 * 3) + 1), posBuffer.get((idx1 * 3) + 2));
			Vector3f pos2 = new Vector3f(posBuffer.get(idx2 * 3), posBuffer.get((idx2 * 3) + 1), posBuffer.get((idx2 * 3) + 2));
			Vector3f pos3 = new Vector3f(posBuffer.get(idx3 * 3), posBuffer.get((idx3 * 3) + 1), posBuffer.get((idx3 * 3) + 2));

			// Extract the normals
			Vector3f norm1 = new Vector3f(normBuffer.get(idx1 * 3), normBuffer.get((idx1 * 3) + 1), normBuffer.get((idx1 * 3) + 2));
			Vector3f norm2 = new Vector3f(normBuffer.get(idx2 * 3), normBuffer.get((idx2 * 3) + 1), normBuffer.get((idx2 * 3) + 2));
			Vector3f norm3 = new Vector3f(normBuffer.get(idx3 * 3), normBuffer.get((idx3 * 3) + 1), normBuffer.get((idx3 * 3) + 2));

			// Extract the Texture Coordinates
			Vector2f texCoord1 = new Vector2f(texCoordBuffer.get(idx1 * 2), texCoordBuffer.get((idx1 * 2) + 1));
			Vector2f texCoord2 = new Vector2f(texCoordBuffer.get(idx2 * 2), texCoordBuffer.get((idx2 * 2) + 1));
			Vector2f texCoord3 = new Vector2f(texCoordBuffer.get(idx3 * 2), texCoordBuffer.get((idx3 * 2) + 1));
			
			// Construct the vertices that define the points of the triangle
			List<CSGVertex> aVertexList = new ArrayList<CSGVertex>( 3 );
			aVertexList.add( new CSGVertex( pos1, norm1, texCoord1, pTransform ) );
			aVertexList.add( new CSGVertex( pos2, norm2, texCoord2, pTransform ) );
			aVertexList.add( new CSGVertex( pos3, norm3, texCoord3, pTransform ) );
			
			// And build the appropriate polygon
			CSGPolygon aPolygon = new CSGPolygon( aVertexList, this.mMaterialIndex );
			polygons.add( aPolygon );
		}
		return( polygons );
	}
	
	/** Produce the mesh(es) that corresponds to this shape
	 	The zeroth mesh in the list is the total, composite mesh.
	 	Every other mesh (if present) applies solely to a specific Material.
	  */
	public List<Mesh> toMesh(
		int		pMaxMaterialIndex
	) {
		List<Mesh> meshList = new ArrayList( pMaxMaterialIndex + 1 );
		
		List<CSGPolygon> aPolyList = getPolygons( null );
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
			
			List<Number> vertexPointers = new ArrayList<Number>( aVertexCount );
			for( CSGVertex aVertex : aVertexList ) {
				pPositionList.add( aVertex.getPosition() );
				pNormalList.add( aVertex.getNormal() );
				pTexCoordList.add( aVertex.getTextureCoordinate() );
				
				vertexPointers.add( indexPtr++ );
			}
			for( int ptr = 2; ptr < aVertexCount; ptr += 1 ) {
				pIndexList.add( vertexPointers.get(0) );
				pIndexList.add( vertexPointers.get(ptr-1) );
				pIndexList.add( vertexPointers.get(ptr) );
			}
		}
		// Use our own buffer setters to optimize access and cut down on object churn
		aMesh.setBuffer( Type.Position, 3, createVector3Buffer( pPositionList ) );
		aMesh.setBuffer( Type.Normal, 3, createVector3Buffer( pNormalList ) );
		aMesh.setBuffer( Type.TexCoord, 2, createVector2Buffer( pTexCoordList ) );
		aMesh.setBuffer( Type.Index, 3, createIndexBuffer( pIndexList ) );
/***
		// Populate the appropriate mesh buffers (which are based on arrays)
		Vector3f[] positionArray = pPositionList.toArray( new Vector3f[ pPositionList.size() ] );
		Vector3f[] normalArray = pNormalList.toArray( new Vector3f[ pNormalList.size() ] );
		Vector2f[] texCoordArray = pTexCoordList.toArray( new Vector2f[ pTexCoordList.size() ] );
		int[] indicesIntArray = new int[ pIndexList.size() ];
		for(int i = 0, j = pIndexList.size(); i < j; i += 1 ) {
			indicesIntArray[i] = pIndexList.get(i);
		}
		aMesh.setBuffer( Type.Position, 3, BufferUtils.createFloatBuffer(positionArray));
		aMesh.setBuffer( Type.Normal, 3, BufferUtils.createFloatBuffer(normalArray));
		aMesh.setBuffer( Type.Index, 3, BufferUtils.createIntBuffer(indicesIntArray));
		aMesh.setBuffer( Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoordArray));
****/
		aMesh.updateBound();
		aMesh.updateCounts();
		
		return( aMesh );
	}

	
	/** Make this shape 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Let the geometry do its thing
		super.write( pExporter );
		
		OutputCapsule capsule = pExporter.getCapsule( this );
		capsule.write( mOrder, "Order", 0 );
		capsule.write( mOperator, "Operator", CSGGeometry.CSGOperator.UNION );
		if ( this.mesh == null ) {
			// If not based on a Mesh, then preserve the given polygons
			// NOTE a deficiency in the OutputCapsule API which should operate on a List,
			//		but instead requires an ArrayList
			capsule.writeSavableArrayList( (ArrayList<CSGPolygon>)mPolygons
											, "Polygons"
											, (ArrayList<CSGPolygon>)sEmptyPolygons );
		}
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		// Let the geometry do its thing
		super.read( pImporter );
		
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mOrder = aCapsule.readInt( "Order", 0 );
		mOperator = aCapsule.readEnum( "Operator", CSGGeometry.CSGOperator.class, CSGGeometry.CSGOperator.UNION );
		if ( this.mesh == null ) {
			// If not based on a mesh, then restore via the polygons
			mPolygons = (List<CSGPolygon>)aCapsule.readSavableArrayList( "Polygons"
																	, (ArrayList<CSGPolygon>)sEmptyPolygons );
		} else {
	        // Extended attributes
	        Vector2f aScale = (Vector2f)aCapsule.readSavable( "scaleTexture", null );
	        if ( aScale != null ) {
	        	this.mesh.scaleTextureCoordinates( aScale );
	        }
	        boolean repeatTexture = aCapsule.readBoolean( "repeatTexture", false );
	        if ( repeatTexture && (this.material != null)) {
	        	MatParamTexture aParam = this.getMaterial().getTextureParam( "DiffuseMap" );
	        	if ( aParam != null ) {
	        		aParam.getTextureValue().setWrap( Texture.WrapMode.Repeat );
	        	}
	        }
		}
	}
	
	/** Implement Comparable to enforce an appropriate application of operations */
	@Override
	public int compareTo(
		CSGShape	pOther
	) {
		int thisOrder = this.getOrder(), otherOrder = pOther.getOrder();
		
		if ( thisOrder == otherOrder ) {
			// CSG should be applied inner-level as follows: Union -> Intersection -> Difference
			switch( this.getOperator() ) {
			case UNION:
				switch( pOther.getOperator() ) {
				case UNION: return( 0 );		// Same as other UNION
				default: return( -1 );			// Before all others
				}
				
			case INTERSECTION:
				switch( pOther.getOperator() ) {
				case UNION: return( 1 );		// After UNION
				case INTERSECTION: return( 0 );	// Same as other INTERSECTION
				default: return( -1 );			// Before all others
				}
				
			case DIFFERENCE:
				switch( pOther.getOperator() ) {
				case UNION:
				case INTERSECTION: 
					return( 1 );				// After UNION/INTERSECTION
				case DIFFERENCE: return( 0 );	// Same as other DIFFERENCE
				default: return( -1 );			// Before all others
				}
				
			case SKIP:
				switch( pOther.getOperator() ) {
				case SKIP: return( 0 );			// Same as other SKIP
				default: return( 1 );			// After all others
				}
			}
			// If we fall out the above, then we come before
			return( -1 );
		}
		return( thisOrder - otherOrder );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGShapeRevision
													, sCSGShapeDate
													, pBuffer ) );
	}

}
