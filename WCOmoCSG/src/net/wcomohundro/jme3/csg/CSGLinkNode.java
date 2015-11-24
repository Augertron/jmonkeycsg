/** Copyright (c) 2015, WCOmohundro
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
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

/** Simple extension of Node that extends Savable.read() to accept a list of external references 
 	and provides for a custom environment.
 	
 	There is no simple programmatic way to access the CSGEnvironment defined by this node.
 	However, in an .xml definition, you can set the environment here and assign it an id
 		<csgEnvironment class='net.wcomohundro.jme3.csg.CSGEnvironment' id='CommonCSGEnvironment' ..../>
 	
 	and then reference it in a subsequent definition via 
 		<csgEnvironment class='net.wcomohundro.jme3.csg.CSGEnvironment' ref='CommonCSGEnvironment'/>
 		
 	Likewise, you can define/reference a common material.
 		
 	This node also can act as a collection of independent CSGSpatials, triggering a single
 	regeneration of all CSG elements within itself.
 */
public class CSGLinkNode 
	extends Node
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGSpatial
{
	/** Version tracking support */
	public static final String sCSGLinkNodeRevision="$Rev$";
	public static final String sCSGLinkNodeDate="$Date$";
	
	
	/** The common material */
    protected Material 			mMaterial;
	/** Common LOD to apply */
	protected int				mLODLevel;
	/** Is this a valid geometry */
	protected boolean			mIsValid;
	/** Shape regeneration time */
	protected long				mRegenNS;
	/** Common environment to supply for CSG processing */
	protected CSGEnvironment 	mEnvironment;
    
	/** Null constructor */
	public CSGLinkNode(
	) {
		// Assume invalid until regeneration OR read() completes
		mIsValid = false;
	}
	
	/** Are all the CSGSpatial valid? */
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
    public void subtractShape(
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
		// Shapes are not really managed here....
		// @todo - think about a default CSGGeonode to operate on????
	}
	
	/** Remove a shape from this geometry */
	@Override
	public void removeShape(
		CSGShape	pShape
	) {
		// Shapes are not really managed here....
		// @todo - think about a default CSGGeonode to operate on????
	}
	
    /** Accessor to the Material (ala Geometry) */
	@Override
    public Material getMaterial() { return mMaterial; }
    @Override
    public void setMaterial(
    	Material 	pMaterial
    ) {
    	// Let the super apply the material to all its subelements
    	super.setMaterial( pMaterial );
        this.mMaterial = pMaterial;
    }
    /** Special provisional setMaterial() that does NOT override anything 
	 	already in force, but supplies a default if any element is missing 
	 	a material
	 */
    @Override
	public void setDefaultMaterial(
		Material	pMaterial
	) {
    	if ( this.mMaterial == null ) {
    		this.mMaterial = pMaterial;
    	}
    	if ( mMaterial != null ) {
    		// Apply to all children where appropriate
            for( Spatial aChild : children ) {
            	if ( aChild instanceof ConstructiveSolidGeometry.CSGSpatial ) {
            		// Apply as default
            		((ConstructiveSolidGeometry.CSGSpatial)aChild).setDefaultMaterial( mMaterial );
            		
            	} else if ( (aChild instanceof Geometry) && (((Geometry)aChild).getMaterial() == null) ) {
            		// Apply to any Geometry that does NOT have a material
            		aChild.setMaterial( mMaterial );
            	}
            }
    	}
    }
    
    /** If physics is active for the shape, connect it all up now */
    @Override
    public void applyPhysics(
    	PhysicsSpace		pPhysicsSpace
	,	PhysicsControl		pDefaultPhysics
    ) {
    	// Apply to all subcomponents
    	for( Spatial aSpatial : children ) {
    		if ( aSpatial instanceof CSGSpatial ) {
    			// Let the subshape decide how to apply the physics
    			((CSGSpatial)aSpatial).applyPhysics( pPhysicsSpace, pDefaultPhysics );
    		} 
    	}
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
		// Operate on all CSG elements defined within this node
		// NOTE that we only detect first level children -- but you can always nest by
		//		using deeper levels of this class.
		mIsValid = true;
		for( Spatial aSpatial : this.getChildren() ) {
			if ( aSpatial instanceof CSGSpatial ) {
				// Trigger the regeneration
				CSGSpatial csgSpatial = (CSGSpatial)aSpatial;
				csgSpatial.regenerate( pEnvironment );
				
				mRegenNS += csgSpatial.getShapeRegenerationNS();
				if ( !csgSpatial.isValid() ) {
					mIsValid = false;
				}
			}
		}
		return( this.isValid() );
	}

	@Override
    public void read(
    	JmeImporter 	pImporter
    ) throws IOException {
        // Any custom environment?
    	AssetManager aManager = pImporter.getAssetManager();
        InputCapsule aCapsule = pImporter.getCapsule( this );
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );
        
		// Act like a Geometry and support a Material (which could then be 'referenced')
        String matName = aCapsule.readString( "materialName", null );
        if ( matName != null ) {
            // Material name is set, attempt to load material via J3M
            try {
                mMaterial = pImporter.getAssetManager().loadMaterial( matName );
            } catch( AssetNotFoundException ex ) {
                throw new IllegalArgumentException( "Cannot locate material: " + matName );
            }
        }
    	// Standard Node processing
        super.read( pImporter );
        
        // Look for list of external definitions
		List<AssetKey> assetLoaderKeys
        	= (List<AssetKey>)aCapsule.readSavableArrayList( "assetLoaderKeyList", null );
		if ( assetLoaderKeys != null ) {
			// Load each associated asset
			for( AssetKey aKey : assetLoaderKeys ) {
				AssetInfo keyInfo = aManager.locateAsset( aKey );
				if ( keyInfo != null ) {
					Object aSpatial = aManager.loadAsset( aKey );
					if ( aSpatial instanceof Spatial ) {
						this.attachChild( (Spatial)aSpatial );
					}
				}
			}
		}
		// Look to apply this material as a default for any elements with no material
		this.setDefaultMaterial( mMaterial );

		// Individual CSGSpatials will regenerate as part of read(), so now scan the list
		// looking for any oddness.
		mIsValid = true;
		for( Spatial aSpatial : this.getChildren() ) {
			if ( aSpatial instanceof CSGSpatial ) {
				CSGSpatial csgSpatial = (CSGSpatial)aSpatial;
				mRegenNS += csgSpatial.getShapeRegenerationNS();
				if ( !csgSpatial.isValid() ) {
					mIsValid = false;
				}
			}
		}
    }
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGLinkNodeRevision
													, sCSGLinkNodeDate
													, pBuffer ) );
	}

}
