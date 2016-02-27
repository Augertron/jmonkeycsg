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
import java.util.TreeMap;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties;
import net.wcomohundro.jme3.math.CSGTransform;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResults;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.math.Transform;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import com.jme3.scene.control.LightControl;
import com.jme3.texture.Texture;
import com.jme3.util.SafeArrayList;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

/**  Constructive Solid Geometry (CSG)

	The CSGGNode is a minimal extension to Node that fulfills CSGElement in supporting
	Materials, Lights, and Physics.
	Its basic use is in final Mesh production of a blended shape where Lights/Physics
	must be supported.
	
	It also acts as a base class for CSGGeonode, which supports the CSG Boolean operators
*/
public class CSGNode
	extends Node
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGElement
{
	/** Version tracking support */
	public static final String sCSGNodeRevision="$Rev$";
	public static final String sCSGNodeDate="$Date$";

	
	/** Unique identifier */
	protected String					mInstanceKey;
	/** The master material */
    protected Material 					mMaterial;
    /** Template transform control to apply to lights */
    protected Control					mLightControl;
	/** Physics that applies to this shape */
	protected PhysicsControl			mPhysics;
	/** The list of custom Propertes to apply to the various faces of the interior components */
	protected List<CSGFaceProperties>	mFaceProperties;
	/** Is this a valid geometry */
	protected CSGExceptionI				mInError;
	/** Shape regeneration time */
	protected long						mRegenNS;
	/** Processing environment to apply */
	protected CSGEnvironment			mEnvironment;
	/** A list of arbitrary elements that can be named and referenced during
	 	XML load processing via id='somename' and ref='somename',
	 	and subsequently referenced programmatically via its inherent name. */
	protected Map<String,Savable>		mLibraryItems;


	/** Basic null constructor */
	public CSGNode(
	) {
		this( "CSGNode" );
	}
	/** Constructor based on a given name */
	public CSGNode(
		String	pName
	) {
		super( pName );
		
		// Empty library until explicitly set
		mLibraryItems = Collections.EMPTY_MAP;
	}
	
	/** OVERRIDE: to keep things tidy */
    @Override
    public CSGNode clone(
    	boolean 	pCloneMaterials
    ){
        CSGNode aCopy = (CSGNode)super.clone( pCloneMaterials );
        
        // Keep the instance key unique
		mInstanceKey = CSGShape.assignInstanceKey( this.name );
		
		// Keep local lights strictly local and NOT shared
		if ( this.localLights.size() > 0 ) {
			LightList copyLights = new LightList( aCopy );
			for( Light aLight : this.localLights ) {
				copyLights.add( aLight.clone() );
			}
			aCopy.localLights = copyLights;
		}
        // Ensure we have our own copy of the physics
        if ( aCopy.mPhysics != null ) {
        	aCopy.mPhysics = (PhysicsControl)this.mPhysics.cloneForSpatial( aCopy );
        }
        return( aCopy );
    }

	/** Return the JME aspect of this element */
	@Override
	public Spatial asSpatial() { return this; }

	/** Is this a valid geometry */
	@Override
	public boolean isValid() { return( mInError == null ); }
	@Override
	public CSGExceptionI getError() { return mInError; }
	public void setError(
		CSGExceptionI	pError
	) {
		if ( (pError != null) && (pError.getCSGElement() == null) ) {
			pError.setCSGElement( this );
		}
		mInError = CSGConstructionException.registerError( mInError, pError );
	}

	
	/** Is there an active parent to this element? */
	@Override
	public CSGElement getParentElement(
	) {
		if ( this.parent instanceof CSGElement ) {
			return( (CSGElement)this.parent );
		} else {
			return( null );
		}
	}
	
	/** Access to library elements */
	@Override
	public Savable getLibraryItem(
		String		pItemName
	) {
		Savable anItem = mLibraryItems.get( pItemName );
		if ( anItem == null ) {
			CSGElement aParent = this.getParentElement();
			if ( aParent != null ) {
				anItem = aParent.getLibraryItem( pItemName );
			} 
		}
		return( anItem );
	}
	
	/** Unique keystring identifying this element */
	@Override
	public String getInstanceKey(
	) { 
		if ( mInstanceKey == null ) {
			mInstanceKey = CSGShape.assignInstanceKey( this.name );
		}
		return mInstanceKey; 
	}
	
	/** How long did it take to regenerate this shape */
	@Override
	public long getShapeRegenerationNS() { return mRegenNS; }
	
    /** Accessor to the Material (ala Geometry) */
	@Override
    public Material getMaterial() { return mMaterial; }
    @Override
    public void setMaterial(
    	Material 	pMaterial
    ) {
    	// Let the super apply the material to all the subelements
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
            	if ( aChild instanceof CSGElement ) {
            		// Apply as default
            		((CSGElement)aChild).setDefaultMaterial( mMaterial );
            		
            	} else if ( (aChild instanceof Geometry) && (((Geometry)aChild).getMaterial() == null) ) {
            		// Apply to any Geometry that does NOT have a material
            		aChild.setMaterial( mMaterial );
            	}
            }
    	}
    }
    
    /** Accessor to the physics */
    @Override
    public boolean hasPhysics() { return mPhysics != null; }
    @Override
    public PhysicsControl getPhysics() { return mPhysics; }
    @Override
    public void setPhysics(
    	PhysicsControl		pPhysics
    ) {
    	mPhysics = pPhysics;
    }
    
    /** If physics is active for the shape, connect it all up now */
    @Override
    public void applyPhysics(
    	PhysicsSpace		pPhysicsSpace
    ,	Node				pRoot
    ) {
    	if ( pRoot == null ) {
    		// CSGNode is only designed to represent a subcomponent of a larger CSG shape,
    		// to a root node is always expected to be supplied
    		throw new IllegalArgumentException( "CSGNode.applyPhysics expects a root node" );
    	}
    	// If this instance of Geonode has its own explicit mPhysics, then it defines its own
    	// collision shape from all participating subelements.
    	if ( mPhysics != null ) {
        	CSGPlaceholderCollisionShape.applyPhysics( pPhysicsSpace, mPhysics, this, pRoot );
    	}
    	// We also cycle through all children to give any with their own explicit physics
    	// a chance to process
	    for( Spatial aSpatial : children ) {
	    	if ( aSpatial instanceof CSGElement ) {
	    		// Let the subshape decide how to apply the physics
	    		((CSGElement)aSpatial).applyPhysics( pPhysicsSpace, pRoot );
	    	}
	    }
    }
    
    /** Accessor to the Light control to apply */
    public Control getLightControl() { return mLightControl; }
    public void setLightControl(
    	Control		pLightControl
    ) {
    	mLightControl = pLightControl;
    }
    
	/** Accessor to Face oriented properties */
	@Override
	public boolean hasFaceProperties() { return( (mFaceProperties != null) && !mFaceProperties.isEmpty() ); }
	@Override
	public List<CSGFaceProperties>	getFaceProperties() { return mFaceProperties; }

	/** Action to generate the mesh based on the given shapes */
    @Override
	public CSGShape regenerate(
	) throws CSGConstructionException {
		return( regenerate( false, null ) );
	}
	@Override
	public CSGShape regenerate(
		boolean				pOnlyIfNeeded
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		mRegenNS = -1;
		long totalNS = 0;
		pEnvironment = CSGEnvironment.resolveEnvironment( (pEnvironment == null) ? mEnvironment : pEnvironment, this );

		// Operate on all CSG elements defined within this node, returning the last
		// NOTE that we only detect first level children -- but you can always nest by
		//		using deeper levels of this class.
		CSGShape lastValidShape = null;
		mInError = null;
		for( Spatial aSpatial : this.getChildren() ) {
			if ( aSpatial instanceof CSGElement ) try {
				// Trigger the regeneration
				CSGElement csgSpatial = (CSGElement)aSpatial;
				CSGShape aShape = csgSpatial.regenerate( pOnlyIfNeeded, pEnvironment );
				
				totalNS += csgSpatial.getShapeRegenerationNS();
				if ( csgSpatial.isValid() ) {
					// Remember the last valid shape
					lastValidShape = aShape;
				} else {
					// If any child shape is invalid, then this shape is invalid
					setError( csgSpatial.getError() );
				}
			} catch( CSGConstructionException ex ) {
				// Record the error and continue on???
				setError( ex );
			}
		}
		mRegenNS = totalNS;
		return( lastValidShape );
	}
	
	/** Support the persistence of this Node */
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
		if ( mEnvironment != null ) {
			aCapsule.write( mEnvironment, "csgEnvironment", null );
		}
		aCapsule.writeSavableArrayList( (ArrayList)mFaceProperties, "faceProperties", null );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
    	AssetManager aManager = pImporter.getAssetManager();
		InputCapsule aCapsule = pImporter.getCapsule( this );

        // Any custom environment that could influence subsequent processing?
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );
        if ( mEnvironment != null ) {
        	// Incorporate this name AND define the standard as needed
        	mEnvironment = CSGEnvironment.resolveEnvironment( mEnvironment, this );
        }
		// Support arbitrary Library items, defined BEFORE we process the rest of the items
        // Such items can be referenced within the XML stream itself via the
        //		id='name' and ref='name'
        // mechanism, and can be reference programmatically via their inherent names
		mLibraryItems = (Map<String,Savable>)aCapsule.readStringSavableMap( "library", null );
		mLibraryItems = CSGShape.fillLibrary( this, mLibraryItems, aManager, true );

		// Let the super do its thing
		super.read( pImporter );
		
		// Act like a Geometry and support a Material
        String matName = aCapsule.readString( "materialName", null );
        if ( matName != null ) {
            // Material name is set, attempt to load material via J3M
            try {
                mMaterial = aManager.loadMaterial( matName );
            } catch( AssetNotFoundException ex ) {
                throw new IllegalArgumentException( "Cannot locate material: " + matName );
            }
        }
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
		// Look for specially defined tranform 
		if ( this.localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				localTransform = proxyTransform.getTransform();
			}
		}
		// Look to apply this material as a default for any elements with no material
		this.setDefaultMaterial( mMaterial );

        // Any physics?
        mPhysics = (PhysicsControl)aCapsule.readSavable( "physics", null );
        
        // Look for possible face properties to apply to interior mesh/subgroup
        mFaceProperties = aCapsule.readSavableArrayList( "faceProperties", null );

        // Are the local lights truely local?
        // Quite honestly, I do not understand the rationale behind how localLights
        // are managed under standard jme3.  If I make a light local to a Node, I fully
        // expect that light to move around as any transform is applied to the Node.
        boolean transformLights = aCapsule.readBoolean( "transformLights", true );
        if ( transformLights ) {
        	mLightControl = new CSGLightControl();
        } else {
        	mLightControl = (Control)aCapsule.readSavable( "lightControl", null );
        }
       if ( mLightControl != null ) {
        	// Build a control for every local light to keep its position in synch
        	// with transforms applied to this Node
    	   List<Control> controls 
    	   		= CSGLightControl.configureLightControls( null, mLightControl, this.getLocalLightList(), false, null );
    	   if ( controls != null ) {
	    	   for( Control aControl : controls ) {
	    		   this.addControl( aControl );
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
													, sCSGNodeRevision
													, sCSGNodeDate
													, pBuffer ) );
	}

	////// Debug support
	@Override
	public String toString(
	) {
		return( (this.name == null) ? getInstanceKey() : getInstanceKey() + "|" + getName() );
	}
}
