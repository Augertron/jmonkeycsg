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

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.placeholder.CSGPlaceholderCollisionShape;
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
	extends CSGNode
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGSpatial
{
	/** Version tracking support */
	public static final String sCSGGeonodeRevision="$Rev$";
	public static final String sCSGGeonodeDate="$Date$";

	
	/** The master geometry/mesh that describes the overall shape */
	protected CSGGeometry		mMasterGeometry;
    /** Control flag to force the use of a single material set at this node level */
    protected boolean			mForceSingleMaterial;
	/** The optional list of child shapes (each annotated with an action as it is added) */
	protected List<CSGShape>	mShapes;
	/** Geometry has a variable for LOD level, but Spatial does not */
	protected int				mLODLevel;
	/** Management of the generated meshes */
	protected CSGMeshManager	mMeshManager;
	protected boolean			mDeferSceneChanges;
	protected CSGShape			mPriorResult;
	/** TangetBinormal generation control flag */
	protected boolean			mGenerateTangentBinormal;

	
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
	
	/** OVERRIDE: to keep things tidy */
    @Override
    public CSGGeonode clone(
    	boolean 	pCloneMaterials
    ){
    	CSGGeonode aCopy = (CSGGeonode)super.clone( pCloneMaterials );
    	
    	// A shallow clone of the shape list will suffice since a shared
    	// shape will produce an appropriate mesh.  But we will need to 
    	// be careful with lights....
    	
    	// I am going to assume that any clone will still be regenerated
    	// and the MasterGeometry will be rebuilt
    	//aCopy.mMasterGeometry = null;
    	return( aCopy );
    }

	
	/** Access to the MasterGeometry that defines the overall shape */
	public CSGGeometry getMasterGeometry() { return mMasterGeometry; }
	
	/** Access to the single material control */
	@Override
	public void forceSingleMaterial( boolean pFlag ) { mForceSingleMaterial = pFlag; }

	/** Accessor to the TangentBinormal generation flag */
	public boolean getGenerateTangentBinormal() { return mGenerateTangentBinormal; }
	public void setGenerateTangentBinormal( boolean pFlag ) { mGenerateTangentBinormal = pFlag; }

    /** If physics is active for the shape, connect it all up now */
    @Override
    public void applyPhysics(
    	PhysicsSpace		pPhysicsSpace
    ,	Node				pRoot
    ) {
    	if ( pRoot == null ) {
    		// We are not part of a larger object, so we act as the root of any
    		// interior pieces
    		pRoot = this;
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
		if ( mShapes == null ) mShapes = new ArrayList<CSGShape>();
		
		// Remember the shape for later operation
		pShape.setOperator( pOperator );
		mShapes.add( pShape );
	}
	
	/** Remove a shape from this geometry */
	@Override
	public void removeAllShapes() { mShapes = null; mRegenNS = 0; };
	@Override
	public void removeShape(
		CSGShape	pShape
	) {
		if ( mShapes != null ) {
			mShapes.remove( pShape );
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

	/** Control when the scene changes are applied */
	@Override
	public void deferSceneChanges( boolean pFlag ) { mDeferSceneChanges = pFlag; }
	@Override
	public synchronized boolean applySceneChanges(
	) {
		if ( mMeshManager != null ) {
			// Apply the previously generated changes
			applySceneChanges( mMeshManager );
			
			// Any deferred changes are complete now
			mMeshManager = null;
			return( true );
		} else {
			return( false );
		}
	}
	protected void applySceneChanges(
		CSGMeshManager	pMeshManager
	) {
		// Apply the scene updates now
		this.detachAllChildren();		// Start with a fresh list of children
		
		if ( pMeshManager.getMeshCount() == 0 ) {
			// Singleton element, where the master becomes our only child
			this.attachChild( mMasterGeometry );
			
		} else {
			// Multiple elements
			for( Spatial aSpatial : pMeshManager.getSpatials( this.getName(), mLightControl ) ) {
				this.attachChild( aSpatial );
			}
			// NOTE that we only attach the independent child meshes, not the master itself
			//	This means we could have positioning issues if the CSGGeonode moves and we 
			//	then use mMasterGeometry for other processing, like collision detection
		}
		if ( this.isValid() ) {
	        // TangentBinormalGenerator directive
	        if ( mGenerateTangentBinormal ) {
	        	// The Generator understands working the children of a Node
	        	TangentBinormalGenerator.generate( this );
	        }
		}
	}

	/** Action to generate the mesh based on the given shapes */
	@Override
	public CSGShape regenerate(
		boolean				pOnlyIfNeeded
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		if ( pOnlyIfNeeded && (mRegenNS > 0) ) {
			// Regeneration already complete
			return( mPriorResult );
		} else {
			// Force regeneration from scratch
			mRegenNS = 0;
			mPriorResult = null;
		}
		pEnvironment = CSGEnvironment.resolveEnvironment( (pEnvironment == null) ? mEnvironment : pEnvironment, this );
		if ( (mShapes != null) && !mShapes.isEmpty() ) {
			// Time the construction operation
			mRegenNS = -1;
			long startTimer = System.nanoTime();
			
			// Prepare for custom materials
			CSGMeshManager meshManager = new CSGMeshManager( this, mForceSingleMaterial );

			// Sort the shapes as needed by their handler
			List<CSGShape> sortedShapes = mShapes.get(0).prepareShapeList( mShapes, pEnvironment );
			
			// Save on churn by leveraging temps
			CSGTempVars tempVars = CSGTempVars.get();
			try {
				// Operate on each shape in turn, blending it into the common
				CSGShape aProduct = null;
				for( CSGShape aShape : sortedShapes ) {
					aShape.setParentElement( this );

					// Apply the operator
					switch( aShape.getOperator() ) {
					case UNION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( meshManager, getLodLevel(), pEnvironment );
						} else {
							// Blend together
							aProduct = aProduct.union( aShape.refresh(), meshManager, tempVars, pEnvironment );
						}
						break;
						
					case DIFFERENCE:
						if ( aProduct == null ) {
							// NO PLACE TO START
						} else {
							// Blend together
							aProduct = aProduct.difference( aShape.refresh(), meshManager, tempVars, pEnvironment );
						}
						break;
						
					case INTERSECTION:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( meshManager, getLodLevel(), pEnvironment );
						} else {
							// Blend together
							aProduct = aProduct.intersection( aShape.refresh(), meshManager, tempVars, pEnvironment );
						}
						break;
						
					case SKIP:
						// This shape is not taking part
						break;

					case MERGE:
						if ( aProduct == null ) {
							// A place to start
							aProduct = aShape.clone( meshManager, getLodLevel(), pEnvironment );
						} else {
							// Treat multiple meshes as a single mesh
							aProduct = aProduct.merge( aShape.refresh(), meshManager, tempVars, pEnvironment );
						}
						break;
					}
				}
				// Build up the mesh(es)
				mMasterGeometry = null;
				if ( aProduct != null ) {
					// Transform the blended product into children
					// The meshManager will retain the set of generated meshes and can provide
					// any given mesh based on its index.
					aProduct.toMesh( meshManager, true, tempVars, pEnvironment );

					// Use the master mesh to describe the overall geometry
					mMasterGeometry 
						= new CSGGeometry( this.getName(), meshManager.resolveMesh( CSGMeshManager.sMasterMeshIndex ) );
					if ( mMaterial != null ) {
						mMasterGeometry.setMaterial( mMaterial.clone() );
					}
					// Return the product
					setError( aProduct.getError() );
					if ( mDeferSceneChanges ) synchronized( this ) {
						// Update the scene later
						mMeshManager = meshManager;
					} else {
						// Update the scene NOW
						applySceneChanges( meshManager );
					}
					return( mPriorResult = aProduct );
				} else {
					// Nothing produced
					setError( null );
				}
			} catch( CSGConstructionException ex ) {
				// Record the problem and toss it again
				setError( ex );
				throw ex;
			} finally {
				tempVars.release();
				mRegenNS = System.nanoTime() - startTimer;
			}
		} else {
			// Nothing interesting
			setError( null );
		}
		// Fall out to here only if nothing was generated
		return( null );
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
    	// Unfortunately, the MasterGeometry is not slaved to the repositioning of this
    	// CSGGeonode, so its collision detection will be off
    	int count = super.collideWith( pOther, pResults );
    	if ( count > 0 ) {
    		// We collided with something inside
    		// @todo - is it possible to reflect this collision at this NODE level???
    	}
    	return( count );
        //return( (mMasterGeometry == null) ? 0 : mMasterGeometry.collideWith( pOther, pResults ) );
    }


	/** Support the persistence of this Geometry */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );

		super.write( pExporter );

        // Override on handling multiple materials
        aCapsule.write( mForceSingleMaterial, "singleMaterial",  false );

		// Save the shapes
		// NOTE a deficiency in the OutputCapsule API which should operate on a List,
		//		but instead requires an ArrayList
		aCapsule.writeSavableArrayList( (ArrayList<CSGShape>)mShapes, "shapes", null );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
    	AssetManager aManager = pImporter.getAssetManager();
		InputCapsule aCapsule = pImporter.getCapsule( this );

		// Let the super do its thing
		super.read( pImporter );
		// But ensure we rebuild the children list from scratch based on "shapes"
		this.children.clear();
		
		// Look for the list of shapes
		mShapes = (List<CSGShape>)aCapsule.readSavableArrayList( "shapes", null );

        // Multi-materials can be suppressed
        mForceSingleMaterial = aCapsule.readBoolean( "singleMaterial",  false );
        
		// Look for specially defined tranform 
		if ( this.localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				localTransform = proxyTransform.getTransform();
			}
		}
		// Are we generating the tangents?
	    mGenerateTangentBinormal = aCapsule.readBoolean( "generateTangentBinormal", false );

		// Rebuild based on the shapes just loaded, which sets the mValid status
	    boolean doLater = aCapsule.readBoolean( "deferRegeneration", false );
	    if ( doLater ) {
	    	// No regeneration at this time
	    	this.mRegenNS = 0;
	    } else {
	    	// Do the regeneration now and capture any errors
	        try {
	        	regenerate();
	        } catch( CSGConstructionException ex ) {
	        	// This error should already be registered with this element
	        }
			if ( !this.isValid() ) {
				CSGEnvironment.sLogger.log( Level.WARNING, "Invalid Geonode: " + this );
			}
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
