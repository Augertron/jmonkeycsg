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

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bounding.BoundingVolume;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResults;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.util.SafeArrayList;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

/**  Constructive Solid Geometry (CSG)

	The CSGGeonode class wants to act like a Geometry which produces a Mesh that is the result
	of the boolean CSG operations. It can have a Material that applies to the final product.
	 
	But we also wish to track portions of the final constructed shape and apply selected Materials 
	to those portions.  It seems the simplest way to segment this Material assignment is to 
	produce independent Meshes, one for each different Material.
	
	Unfortunately, I am seeing no simple mechanism in jMonkey that allows me to create a single
	Geometry with multiple Meshes.  Instead, jMonkey is predicated on using Nodes to hold 
	multiple children where each child can be a Geometry (with its Mesh and Material).
	
	So I will operate this CSG class as a Node, where its children are minimal Geometrys that
	are created to associate a Mesh with a Material. 
	I hope to optimize bounds and collision processing by working from the single complete Mesh
	that is produced by standard processing. 
*/
public class CSGGeonode
	extends Node
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGSpatial
{
	/** Version tracking support */
	public static final String sCSGGeonodeRevision="$Rev$";
	public static final String sCSGGeonodeDate="$Date$";

	
	/** The master geometry/mesh that describes the overall shape */
	protected CSGGeometry		mMasterGeometry;
	/** The master material */
    protected Material 			mMaterial;
    /** Control flag to force the use of a single material set at this node level */
    protected boolean			mForceSingleMaterial;
	/** The list of child shapes (each annotated with an action as it is added) */
	protected List<CSGShape>	mShapes;
	/** Geometry has a variable for LOD level, but Spatial does not */
	protected int				mLODLevel;
	/** Is this a valid geometry */
	protected boolean			mIsValid;
	/** Shape regeneration time */
	protected long				mRegenNS;
	/** Processing environment to apply */
	protected CSGEnvironment	mEnvironment;

	
	/** Basic null constructor */
	public CSGGeonode(
	) {
		this( "CSGGeoNode" );
	}
	/** Constructor based on a given name */
	public CSGGeonode(
		String	pName
	) {
		super( pName );
	}
	
	/** Is this a valid geometry */
	@Override
	public boolean isValid() { return mIsValid; }
	
	/** How long did it take to regenerate this shape */
	@Override
	public long getShapeRegenerationNS() { return mRegenNS; }
	
    /** Include a shape */
	@Override
    public void addShape(
    	CSGShape	pShape
    ) {
    	addShape( pShape, CSGOperator.UNION );
    }
	@Override
    public void substractShape(
	    CSGShape	pShape
	) {
    	addShape( pShape, CSGOperator.DIFFERENCE );
    }
	@Override
    public void intersectShape(
	    CSGShape	pShape
	) {
    	addShape( pShape, CSGOperator.INTERSECTION );
    }

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
	
    /** Accessor to the Material (ala Geometry) */
	@Override
    public Material getMaterial() { return mMaterial; }
    @Override
    public void setMaterial(
    	Material 	pMaterial
    ) {
        this.mMaterial = pMaterial;
    }
    
    /** Accessor to the LOD level (ala Geometry) */
    @Override
    public int getLodLevel() { return mLODLevel; }
    @Override
    public void setLodLevel(
    	int		pLODLevel
    ) {
    	mLODLevel = pLODLevel;
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
			// Time the construction operation
			long startTimer = System.nanoTime();
			
			// Prepare for custom materials
			CSGMaterialManager materialManager 
				= new CSGMaterialManager( this.getMaterial(), mForceSingleMaterial );

			// Sort the shapes as needed by their handler
			List<CSGShape> sortedShapes = mShapes.get(0).prepareShapeList( mShapes, pEnvironment );
			
			// Save on churn by leveraging temps
			CSGTempVars tempVars = CSGTempVars.get();
			try {
				// Operate on each shape in turn, blending it into the common
				CSGShape aProduct = null;
				for( CSGShape aShape : sortedShapes ) {
					// Apply the operator
					switch( aShape.getOperator() ) {
					case UNION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( materialManager, getLodLevel(), pEnvironment );
						} else {
							// Blend together
							aProduct = aProduct.union( aShape.refresh(), materialManager, tempVars, pEnvironment );
						}
						break;
						
					case DIFFERENCE:
						if ( aProduct == null ) {
							// NO PLACE TO START
						} else {
							// Blend together
							aProduct = aProduct.difference( aShape.refresh(), materialManager, tempVars, pEnvironment );
						}
						break;
						
					case INTERSECTION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( materialManager, getLodLevel(), pEnvironment );
						} else {
							// Blend together
							aProduct = aProduct.intersection( aShape.refresh(), materialManager, tempVars, pEnvironment );
						}
						break;
						
					case SKIP:
						// This shape is not taking part
						break;
					}
				}
				// Build up the mesh(es)
				mMasterGeometry = null;
				if ( aProduct != null ) {
					// Transform the list of meshes into children
					// (note that the 'zero' mesh is an Overall mesh that crosses all materials,
					//  and the 'one' mesh is the one that corresponds to the generic material)
					List<Mesh> meshList 
						= aProduct.toMesh( materialManager.getMaterialCount()
											, materialManager
											, tempVars
											, pEnvironment );
					if ( !meshList.isEmpty() ) {
						// Use the 'zero' mesh to describe the overall geometry
						mMasterGeometry = new CSGGeometry( this.getName(), meshList.get( 0 ) );
						mMasterGeometry.setMaterial( mMaterial.clone() );
					}
					if ( meshList.size() == 1 ) {
						// Singleton element, where the master becomes our only child
						this.attachChild( mMasterGeometry );
						
					} else if ( meshList.size() > 1 ) {
						// Multiple elements, with the first dedicated to the 'generic' material
						// (which means the 'i' index is one greater than the material index)
						for( int i = 1, j = meshList.size(); i < j; i += 1 ) {
							Geometry aChild = new Geometry( this.getName() + i, meshList.get( i ) );
							Material aMaterial = materialManager.resolveMaterial( new Integer( i - 1 ) );
							aChild.setMaterial( aMaterial.clone() );
							this.attachChild( aChild );
						}
						// NOTE that we only attach the independent child meshes, not the master
					}
					// Return true if we have a valid product
					return( mIsValid = aProduct.isValid() );
				} else {
					// Nothing produced
					return( false );
				}
			} finally {
				tempVars.release();
				mRegenNS = System.nanoTime() - startTimer;
			}
		} else {
			// Nothing interesting
			return( false );
		}
	}
	
    /** Updates the bounding volume of the mesh. Should be called when the
		mesh has been modified.
		OVERRIDE to operate on the master 
     */
	@Override
    public void updateModelBound(
    ) {
        super.updateModelBound();
        
        if ( mMasterGeometry != null) {
        	mMasterGeometry.updateModelBound();
        }
    }

    /** Update the bounding volume that contains this geometry. 
     	OVERRIDE to operate on the master mesh
     */
    @Override
    protected void updateWorldBound(
    ) {
    	// I was hoping to optimize this processing by operating solely on the the 
    	// masterGeometry, but something goes amiss and the surfaces get lost at 
    	// random viewing angles ?????
        super.updateWorldBound();

        if ( mMasterGeometry != null) {
        	//this.worldBound = mMasterGeometry.refreshWorldBound().clone( this.worldBound );
        }
    }
    
    /**	Returns the number of triangles contained in all sub-branches of this node that contain geometry.
		OVERRIDE to operate directly from the master 
	*/
    @Override
    public int getTriangleCount(
    ) {
    	return( (mMasterGeometry == null) ? 0 : mMasterGeometry.getTriangleCount() );
    }
    
    /**	Returns the number of vertices contained in all sub-branches of this node that contain geometry.
   		OVERRIDE to operate directly from the master mesh
     */
    @Override
    public int getVertexCount(
    ) {
        return( (mMasterGeometry == null) ? 0 : mMasterGeometry.getVertexCount() );
    }
    
    /** Process a collision 
     	OVERRIDE to operate directly from the master
     */
    @Override
    public int collideWith(
    	Collidable 			pOther
    , 	CollisionResults 	pResults
    ) {
        return( (mMasterGeometry == null) ? 0 : mMasterGeometry.collideWith( pOther, pResults ) );
    }


	/** Support the persistence of this Geometry */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );

		// We are NOT interested in saving the generated children
		// since we expect to rebuild the composite
		SafeArrayList<Spatial> saveChildren = this.children;
		this.children = null;
		try {
			super.write( pExporter );
		} finally {
			this.children = saveChildren;
		}
		// Like a Geometry, a possible Material
        if ( mMaterial != null ) {
            aCapsule.write( mMaterial.getAssetName(), "materialName", null );
        }
        // Override on handling multiple materials
        aCapsule.write( mForceSingleMaterial, "singleMaterial",  false );

		// Save the shapes
		// NOTE a deficiency in the OutputCapsule API which should operate on a List,
		//		but instead requires an ArrayList
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
		// But ensure we rebuild the children list from scratch
		this.children.clear();;
		
		// Act like a Geometry and support a Material
        String matName = aCapsule.readString( "materialName", null );
        if ( matName != null ) {
            // Material name is set, attempt to load material via J3M
            try {
                mMaterial = pImporter.getAssetManager().loadMaterial( matName );
            } catch( AssetNotFoundException ex ) {
                throw new IllegalArgumentException( "Cannot locate material: " + matName );
            }
        }
        // Multi-materials can be suppressed
        mForceSingleMaterial = aCapsule.readBoolean( "singleMaterial",  false );
        
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
        // Any custom environment?
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );
        if ( mEnvironment != null ) mEnvironment.mShapeName = this.getName() + ": ";

		// Rebuild based on the shapes just loaded
		mIsValid = regenerate();
		
        // TangentBinormalGenerator directive
        boolean generate = aCapsule.readBoolean( "generateTangentBinormal", false );
        if ( generate ) {
        	// The Generator understands working the children of a Node
        	TangentBinormalGenerator.generate( this );
        }
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGGeonodeRevision
													, sCSGGeonodeDate
													, pBuffer ) );
	}


}
