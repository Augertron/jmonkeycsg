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
	
	Logic and Inspiration for BSP taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.wcomohundro.jme3.csg.bsp.CSGPartition;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.CSGMesh;
import net.wcomohundro.jme3.math.CSGTransform;

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
 
	Provide a basic 'shape' for CSG processing 
 
 	A CSG Shape is based on a generated set of polygons that describe the faces of the shape
 	(think Mesh for the CSG world).  Any shape can be CSG blended with another shape via
 	the boolean operators: union, difference, and intersection.  The result is a new 
 	CSG Shape with its own set of faces, from which a standard Mesh can be generated for
 	subsequent jme3 display.
 	
 	The basic Shape can be generated from the Mesh behind any JME Shape.
 	
 	The CSGShape acts like a Geometry so that transformations can be applied to the shape
 	before any CSG blending occurs.  I do not believe that Geometry based lights are of
 	use at this time. The underlying Material applied to a shape is retained during the
 	boolean operation, so that the final shape may have faces with differing materials.
 	
 	In addition, CSGShape supports the idea of a Mesh with multiple Materials.  Normal
 	JME3 meshes have no Material information.  But the primitive CSG shapes support assigning
 	different materials to different faces.  Therefore, if CSGShape can figure out how
 	mutlitple materials are assigned, then it keeps track of them.
 	
 	The boolean processing is based on either:
 	
 	1)	BinarySpacePartitioning - where the polygons of a shape are sorted into front/back
 			elements according to their position to a given plane.  Polygons that cross the
 			plane are 'split', and the front/back elements are recursively partitioned.
 			
 	2)	In/Out/Edge processing - where a solid's faces and vertices are categorized in 
 			relationship to another shapes face, with the operator determining which 
 			faces/vertices are retained in the resultant shape.
 	
 */
public class CSGShape 
	extends Geometry
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeRevision="$Rev$";
	public static final String sCSGShapeDate="$Date$";
	
	/** Handler interface */
	public interface CSGShapeProcessor {
		
		/** Connect to a shape */
		public CSGShapeProcessor setShape(
			CSGShape		pForShape
		);
		/** Make a copy */
		public CSGShapeProcessor clone(
			CSGShape		pForShape
		);
		
		/** Ready a list of shapes for processing */
		public List<CSGShape> prepareShapeList(
			List<CSGShape>	pShapeList
		,	CSGEnvironment	pEnvironment
		);	
		
		/** Add a shape into this one */
		public CSGShape union(
			CSGShape			pOtherShape
		,	CSGMaterialManager	pMaterialManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironment		pEnvironment
		);
		
		/** Subtract a shape from this one */
		public CSGShape difference(
			CSGShape			pOtherShape
		,	CSGMaterialManager	pMaterialManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironment		pEnvironment
		);

		/** Find the intersection with another shape */
		public CSGShape intersection(
			CSGShape			pOtherShape
		,	CSGMaterialManager	pMaterialManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironment		pEnvironment
		);
		
		/** Produce the mesh(es) that corresponds to this shape
		 	The zeroth mesh in the list is the total, composite mesh.
		 	Every other mesh (if present) applies solely to a specific Material.
		  */
		public List<Mesh> toMesh(
			int					pMaxMaterialIndex
		,	CSGMaterialManager	pMaterialManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironment		pEnvironment
		);

	}
	
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

	/** The active handler that performs shape manipulation */
	protected CSGShapeProcessor			mHandler;
	/** The operator applied to this shape as it is added into the geometry */
	protected CSGGeometry.CSGOperator	mOperator;
	/** Arbitrary 'ordering' of operations within the geometry */
	protected int						mOrder;

	
	/** Generic constructor */
	public CSGShape(
	) {
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
		mOrder = pOrder;
		mOperator = CSGGeometry.CSGOperator.UNION;
	}
	public CSGShape(
		String	pShapeName
	,	int		pOrder
	) {
		super( pShapeName );	// no mess provided
		mOrder = pOrder;
		mOperator = CSGGeometry.CSGOperator.UNION;		
	}
	/** Constructor based on a given handler */
	public CSGShape(
		CSGShapeProcessor	pHandler
	,	int					pOrder
	) {
		mHandler = pHandler.setShape( this );
		mOrder = pOrder;
	}
		
	/** Make a copy of this shape */
	@Override
	public CSGShape clone(
	) {
		return( clone( null, this.getLodLevel(), CSGEnvironment.sStandardEnvironment ) );
	}
	public CSGShape clone(
		CSGMaterialManager	pMaterialManager
	,	int					pLODLevel
	,	CSGEnvironment		pEnvironment
	) {
		CSGShape aClone;
		
		if ( this.mesh != null ) {
			// Base it on the mesh (but do not clone the material)
			aClone = (CSGShape)super.clone( false );
			aClone.setOrder( mOrder );
			
			if ( this.mesh instanceof CSGMesh ) {
				// Take this opportunity to register every custom face material in the mesh
				((CSGMesh)this.mesh).registerMaterials( pMaterialManager );
			}
		} else {
			// Empty with no mesh
			aClone = new CSGShape( this.getName(), this.mOrder );
		}
		aClone.setOperator( mOperator );
//		aClone.setLodLevel( pLODLevel );
		
		// Register this shape's standard Material
		pMaterialManager.resolveMaterialIndex( this.getMaterial() );
		
		aClone.mHandler = this.getHandler( pEnvironment ).clone( aClone );
		return( aClone );
	}
	
	/** Mostly internal access to the handler */
	public CSGShapeProcessor getHandler(
		CSGEnvironment	pEnvironment
	) {
		if ( mHandler == null ) {
			mHandler = pEnvironment.resolveShapeProcessor().setShape( this );
		}
		return( mHandler );
	}
	
	/** Ready a list of shapes for processing */
	public List<CSGShape> prepareShapeList(
		List<CSGShape>	pShapeList
	,	CSGEnvironment	pEnvironment
	) {
		return( getHandler( pEnvironment ).prepareShapeList( pShapeList, pEnvironment ) );
	}
		
	/** The shape knows if it is 'valid' or not */
	public boolean isValid() { return true; }
	
	/** Accessor to the material that applies to the given surface */
	public Integer getMaterialIndex(
		CSGMaterialManager	pMaterialManager
	,	int					pFaceIndex
	) {
		Material useMaterial;
		
		if ( this.mesh instanceof CSGMesh ) {
			// CSGMesh based primitives may support per-face materials in their own right
			useMaterial = ((CSGMesh)this.mesh).getMaterial( pFaceIndex );
			if ( useMaterial == null ) {
				// Nothing special, use the standard
				useMaterial = this.getMaterial();
			}
		} else {
			// Use the standard 
			useMaterial = this.getMaterial();
		}
		// Base the index on the underlying material
		return( pMaterialManager.resolveMaterialIndex( useMaterial ) );
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
	
	/** Add a shape into this one */
	public CSGShape union(
		CSGShape			pOtherShape
	,	CSGMaterialManager	pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		return( getHandler( pEnvironment ).union( pOtherShape, pMaterialManager, pTempVars, pEnvironment ) );
	}
	
	/** Subtract a shape from this one */
	public CSGShape difference(
		CSGShape		pOtherShape
	,	CSGMaterialManager	pMaterialManager
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		return( getHandler( pEnvironment ).difference( pOtherShape, pMaterialManager, pTempVars, pEnvironment ) );
	}

	/** Find the intersection with another shape */
	public CSGShape intersection(
		CSGShape			pOtherShape
	,	CSGMaterialManager	pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		return( getHandler( pEnvironment ).intersection( pOtherShape, pMaterialManager, pTempVars, pEnvironment ) );
	}

	/** Produce the mesh(es) that corresponds to this shape
	 	The zeroth mesh in the list is the total, composite mesh.
	 	Every other mesh (if present) applies solely to a specific Material.
	  */
	public List<Mesh> toMesh(
		int					pMaxMaterialIndex
	,	CSGMaterialManager	pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		return( getHandler( pEnvironment ).toMesh( pMaxMaterialIndex, pMaterialManager, pTempVars, pEnvironment ) );
	}
		
	
	/** Make this shape 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Let the geometry do its thing
		super.write( pExporter );
		
		OutputCapsule capsule = pExporter.getCapsule( this );
		capsule.write( mOrder, "order", 0 );
		capsule.write( mOperator, "operator", CSGGeometry.CSGOperator.UNION );
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		// Let the geometry do its thing
		super.read( pImporter );
		
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mOrder = aCapsule.readInt( "order", 0 );
		mOperator = aCapsule.readEnum( "operator", CSGGeometry.CSGOperator.class, CSGGeometry.CSGOperator.UNION );
		if ( this.mesh != null ) {
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
		if ( localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				localTransform = proxyTransform.getTransform();
			}
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGShapeRevision
													, sCSGShapeDate
													, pBuffer ) );
	}

}
