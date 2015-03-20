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
import java.util.logging.Level;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetNotFoundException;
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
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGGeonodeRevision="$Rev$";
	public static final String sCSGGeonodeDate="$Date$";

	/** A canned zero */
	protected static final Integer sGenericMaterialIndex = new Integer( 0 );
	
	/** The master material */
    protected Material 			mMaterial;
    /** Control flag to force the use of a single material set at this node level */
    protected boolean			mForceSingleMaterial;
	/** The list of child shapes (each annotated with an action as it is added) */
	protected List<CSGShape>	mShapes;

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
	
	/** Add a shape to this geometry */
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
	public void removeShape(
		CSGShape	pShape
	) {
		if ( mShapes != null ) {
			mShapes.remove( pShape );
		}
	}
	
    /** Accessor to the Material (ala Geometry */
    public Material getMaterial() { return mMaterial; }
    @Override
    public void setMaterial(
    	Material 	pMaterial
    ) {
        this.mMaterial = pMaterial;
    }

	/** Action to generate the mesh based on the given shapes */
	public void regenerate(
	) {
		if ( (mShapes != null) && !mShapes.isEmpty() ) {
			// Sort the shapes based on their operator
			List<CSGShape> sortedShapes = new ArrayList<>( mShapes );
			Collections.sort( sortedShapes );
			
			// Prepare for custom materials
			Map materialMap = null;
			int materialCount = 0;
			
			// Operate on each shape in turn, blending it into the common
			CSGShape aProduct = null;
			for( CSGShape aShape : sortedShapes ) {
				// Any special Material?
				Integer materialIndex = null;
				Material aMaterial = (mForceSingleMaterial) ? null : aShape.getMaterial();
				if ( aMaterial != null ) {
					// Locate cached copy of the material
					AssetKey materialKey = aMaterial.getKey();
					
					if ( materialMap == null ) {
						// No other custom materials - this becomes the first
						materialMap = new HashMap( 17 );
						
						materialIndex = new Integer( ++materialCount );
						materialMap.put( materialKey, materialIndex );
						materialMap.put( materialIndex, aMaterial );
						
						// Include the 'generic' material as well as index zero
						if ( this.mMaterial != null ) {
							materialMap.put( this.mMaterial.getKey(), sGenericMaterialIndex );
							materialMap.put( sGenericMaterialIndex, this.mMaterial );
						}
						
					} else if ( materialMap.containsKey( materialKey ) ) {
						// Use the material already found
						materialIndex = (Integer)materialMap.get( materialKey );
						
					} else {
						// Add this custom material into the list
						materialIndex = new Integer( ++materialCount );
						materialMap.put( materialKey,  materialIndex );
						materialMap.put( materialIndex, aMaterial );
					}
				} else {
					// No custom material
					materialIndex = null;
				}
				// Apply the operator
				switch( aShape.getOperator() ) {
				case UNION:
					if ( aProduct == null ) {
						// A place to start
						aProduct = aShape.clone( materialIndex );
					} else {
						// Blend together
						aProduct = aProduct.union( aShape, materialIndex );
					}
					break;
					
				case DIFFERENCE:
					if ( aProduct == null ) {
						// NO PLACE TO START
					} else {
						// Blend together
						aProduct = aProduct.difference( aShape, materialIndex );
					}
					break;
					
				case INTERSECTION:
					if ( aProduct == null ) {
						// A place to start
						aProduct = aShape.clone( materialIndex );
					} else {
						// Blend together
						aProduct = aProduct.intersection( aShape, materialIndex );
					}
					break;
					
				case SKIP:
					// This shape is not taking part
					break;
				}
			}
			if ( aProduct != null ) {
				// Transform the list of meshes into children
				// (note that the zero mesh is an Overall mesh that crosses all materials,
				//  and the one mesh is the one that corresponds to the generic material)
				List<Mesh> meshList = aProduct.toMesh( (materialMap == null) ? 0 : materialCount );
				if ( meshList.size() == 1 ) {
					// Singleton element
					Geometry aChild = new Geometry( this.getName(), meshList.get( 0 ) );
					aChild.setMaterial( mMaterial );
					this.attachChild( aChild );
					
				} else if ( meshList.size() > 1 ) {
					// Multiple elements, with the first dedicated to the 'generic' material
					// (which means the 'i' index is one greater than the material index)
					for( int i = 1, j = meshList.size(); i < j; i += 1 ) {
						Geometry aChild = new Geometry( this.getName() + i, meshList.get( i ) );
						Material aMaterial = (Material)materialMap.get( new Integer( i - 1 ) );
						aChild.setMaterial( aMaterial );
						this.attachChild( aChild );
					}
				}
				
			}
		}
	}

	/** Support the persistence of this Geometry */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );

		// We are NOT interested in saving the generated children
		// since we expect to rebuild the composit
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
		aCapsule.writeSavableArrayList( (ArrayList<CSGShape>)mShapes, "Shapes", null );
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
		mShapes = (List<CSGShape>)aCapsule.readSavableArrayList( "Shapes", null );
		
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
		// Rebuild based on the shapes just loaded
		regenerate();
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGGeonodeRevision
													, sCSGGeonodeDate
													, pBuffer ) );
	}


}
