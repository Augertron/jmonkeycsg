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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;

import com.jme3.asset.AssetKey;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.bounding.BoundingVolume;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

import net.wcomohundro.jme3.csg.shape.CSGMesh;


/**  Constructive Solid Geometry (CSG)

	The CSGGeometry class extends the basic JME Geometry to support the blending of various CSG shapes.
 	The standard CSG operations of Union / Difference / Intersection are supported.
 	
 	The end result is a Mesh produced by the blending of the shapes.  This mesh can then
 	be transformed and textured as it is placed in the scene.
 	
 	Unfortunately, when I went to expand this Geometry concept to handle blending materials from the 
 	underlying shapes, I hit a wall where a Geometry cannot support multiple meshes.  You have to
 	operate as a Node with multiple child Geometries to get multiple meshes and materials.
 	
 	I had considered deprecating this class, but we may still trip over a need for a simple Geometry.
 	So long as you can tolerate a single Material, then this class may still be of interest to you.
 	I have also added the design concept of this Geometry being in 'debug' mode, where the 
 	associated Mesh can provide us with an alternate, debug representation of itself.
 	
 	@see CSGGeonode - which is rooted in Node and creates independent sub-Geometries
 					  to handle the different meshes/textures
*/
public class CSGGeometry
	extends Geometry 
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGSpatial
{
	/** Version tracking support */
	public static final String sCSGGeometryRevision="$Rev$";
	public static final String sCSGGeometryDate="$Date$";

	/** The list of child shapes (each annotated with an action as it is added) */
	protected List<CSGShape>	mShapes;
	/** Processing environment to apply */
	protected CSGEnvironment	mEnvironment;
	/** Is this a valid geometry */
	protected boolean			mIsValid;
	/** Control flag for the special 'debug' mode */
	protected boolean			mDebugMesh;
	

	/** Basic null constructor */
	public CSGGeometry(
	) {
		this( "CSGGeometry" );
	}
	/** Constructor based on a given name */
	public CSGGeometry(
		String	pName
	) {
		super( pName );
	}
	/** Constructor on a name and given mesh */
	public CSGGeometry(
		String	pName
	,	Mesh	pMesh
	) {
		super( pName, pMesh );
	}

	/** Accessor to the debug mesh control flag */
	public boolean isDebugMesh() { return mDebugMesh; }
	public void setDebugMesh( boolean pFlag ) { mDebugMesh = pFlag; }
	
	/** Is this a valid geometry */
	@Override
	public boolean isValid() { return mIsValid; }
	
	/** Add a shape to this geometry */
	@Override
	public void addShape(
		CSGShape	pShape
	,	CSGOperator	pOperator
	) {
		if ( mShapes == null ) mShapes = new ArrayList<CSGShape>();
		
		// Remember the shape for later operation
		pShape.setOperator( pOperator );
		mShapes.add( pShape );
	}
	
	/** Remove a shape from this geometry */
	@Override
	public void removeShape(
		CSGShape	pShape
	) {
		if ( mShapes != null ) {
			mShapes.remove( pShape );
		}
	}
	
	/** MESH level accessors that allow us to 'delegate' the given mesh */
	@Override
    public Mesh getMesh(
    ) {
        return( mesh );
    }
	@Override
    public BoundingVolume getModelBound(
    ) {
        return( getMesh().getBound() );
    }
	@Override
    public int getVertexCount(
    ) {
        return( getMesh().getVertexCount() );
    }
	@Override
    public int getTriangleCount(
    ) {
        return( getMesh().getTriangleCount() );
    }

	/** For CSGGeonode use -- allow non protected access to updateWorldBound
	 						  and the resulting bound
	 */
	public BoundingVolume refreshWorldBound(
	) {
		this.updateWorldBound();
		return( worldBound );
	}
	
	/** Action to generate the mesh based on the given shapes */
	@Override
	public boolean regenerate(
	) {
		return( regenerate( (mEnvironment == null) ? CSGEnvironment.sStandardEnvironment : mEnvironment ) );
	}
	@Override
	public boolean regenerate(
		CSGEnvironment		pEnvironment
	) {
		if ( (mShapes != null) && !mShapes.isEmpty() ) {
			// Sort the shapes based on their operator
			List<CSGShape> sortedShapes = new ArrayList<>( mShapes );
			Collections.sort( sortedShapes );
				
			// Save on churn by leveraging temps
			TempVars tempVars = TempVars.get();
			try {
				// Operate on each shape in turn, blending it into the common
				CSGShape aProduct = null;
				for( CSGShape aShape : sortedShapes ) {
					// Apply the operator
					switch( aShape.getOperator() ) {
					case UNION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( null, getLodLevel() );
						} else {
							// Blend together
							aProduct = aProduct.union( aShape, null, tempVars, pEnvironment );
						}
						break;
						
					case DIFFERENCE:
						if ( aProduct == null ) {
							// NO PLACE TO START
						} else {
							// Blend together
							aProduct = aProduct.difference( aShape, null, tempVars, pEnvironment );
						}
						break;
						
					case INTERSECTION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( null, getLodLevel() );
						} else {
							// Blend together
							aProduct = aProduct.intersection( aShape, null, tempVars, pEnvironment );
						}
						break;
						
					case SKIP:
						// This shape is not taking part
						break;
					}
				}
				if ( aProduct != null ) {
					List<Mesh> meshList = aProduct.toMesh( 0, tempVars, pEnvironment );
					if ( !meshList.isEmpty() ) {
						// The overall, blended mesh represents this Geometry
						this.setMesh( meshList.get( 0 ) );
					}
					// Return true if we have a valid product
					return( mIsValid = aProduct.isValid() );
				} else {
					// Nothing interesting produced
					return( false );
				}
			} finally {
				tempVars.release();
			}
		} else if ( this.mesh instanceof CSGMesh ) {
			// If in 'debug' mode, look for a possible delegate
			this.mesh = ((CSGMesh)this.mesh).resolveMesh( mDebugMesh );
			return( true );
			
		} else {
			// Nothing of interest
			return( false );
		}
	}

	/** Support the persistence of this Geometry */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Let the super do its thing BUT DO NOT DUMP THE MESH
		// Instead, we rely on the shapes themselves
		Mesh saveMesh = this.mesh;
		this.mesh = null;
		try {
			super.write( pExporter );
		} finally {
			this.mesh = saveMesh;
		}
		// Save the shapes
		// NOTE a deficiency in the OutputCapsule API which should operate on a List,
		//		but instead requires an ArrayList
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.writeSavableArrayList( (ArrayList<CSGShape>)mShapes, "shapes", null );
		
		if ( mEnvironment != null ) {
			aCapsule.write( mEnvironment, "csgEnvironment", null );
		}
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// Let the super do its thing
		super.read( pImporter );
		
		// Debug support
		mDebugMesh = aCapsule.readBoolean( "debugMesh", false );
		
		// Look for the list of shapes
		mShapes = (List<CSGShape>)aCapsule.readSavableArrayList( "shapes", null );
		
		// Look for list of external definitions
		List<AssetKey> assetLoaderKeys
        	= (List<AssetKey>)aCapsule.readSavableArrayList( "assetLoaderKeyList", null );
		if ( assetLoaderKeys != null ) {
			// Load each associated asset
			for( AssetKey aKey : assetLoaderKeys ) {
				Object aShape = pImporter.getAssetManager().loadAsset( aKey );
				if ( aShape instanceof CSGShape ) {
					if ( mShapes == null ) mShapes = new ArrayList<CSGShape>();
					mShapes.add( (CSGShape)aShape );
				}
			}
		}
		// Anything special for the Material?
        boolean repeatTexture = aCapsule.readBoolean( "repeatTexture", false );
        if ( repeatTexture && (this.material != null)) {
        	MatParamTexture aParam = this.getMaterial().getTextureParam( "DiffuseMap" );
        	if ( aParam != null ) {
        		aParam.getTextureValue().setWrap( Texture.WrapMode.Repeat );
        	}
        }
        // Any custom environment?
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );
        if ( mEnvironment != null ) mEnvironment.mShapeName = this.getName() + ": ";
        
		// Rebuild based on the shapes just loaded
		mIsValid = regenerate();
		
        // TangentBinormalGenerator directive
        boolean generate = aCapsule.readBoolean( "generateTangentBinormal", false );
        if ( generate ) {
        	TangentBinormalGenerator.generate( this );
        }
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGGeometryRevision
													, sCSGGeometryDate
													, pBuffer ) );
	}


}
