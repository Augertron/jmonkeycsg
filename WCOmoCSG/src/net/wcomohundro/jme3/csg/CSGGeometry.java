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
import com.jme3.bullet.control.PhysicsControl;
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
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties;
import net.wcomohundro.jme3.csg.shape.CSGMesh;
import net.wcomohundro.jme3.math.CSGTransform;


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

	
	/** Unique identifier */
	protected String					mInstanceKey;
	/** The list of child shapes (each annotated with an action as it is added) */
	protected List<CSGShape>			mShapes;
	/** Template of light control to apply transforms */
	protected Control					mLightControl;
	/** Physics that applies to this shape */
	protected PhysicsControl			mPhysics;
	/** The list of custom Propertes to apply to the various faces of the interior components */
	protected List<CSGFaceProperties>	mFaceProperties;
	/** Processing environment to apply */
	protected CSGEnvironment			mEnvironment;
	/** Is this a valid geometry? */
	protected CSGExceptionI				mInError;
	/** Management of the generated meshes */
	protected CSGMeshManager			mMeshManager;
	protected boolean					mDeferSceneChanges;
	/** How long did it take to regenerate this shape (nanoseconds) */
	protected long						mRegenNS;
	/** Control flag for the special 'debug' mode */
	protected boolean					mDebugMesh;
	

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
		mInstanceKey = CSGShape.assignInstanceKey( "CSGGeometry" );
	}
	
	/** Return the JME aspect of this element */
	@Override
	public Spatial asSpatial() { return this; }

	/** Unique keystring identifying this element */
	@Override
	public String getInstanceKey(
	) { 
		if ( mInstanceKey == null ) {
			mInstanceKey = CSGShape.assignInstanceKey( this.name );
		}
		return mInstanceKey; 
	}
	
	/** Is this a valid geometry */
	@Override
	public boolean isValid() { return( mInError == null ); }
	@Override
	public CSGExceptionI getError() { return mInError; }
	public void setError(
		CSGExceptionI	pError
	) {
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

    /** Special provisional setMaterial() that does NOT override anything 
	 	already in force, but supplies a default if any element is missing 
	 	a material
	 */
    @Override
	public void setDefaultMaterial(
		Material	pMaterial
	) {
    	if ( this.material == null ) {
    		this.material = pMaterial;
    	}
    }
	/** Access to the single material control */
	@Override
	public void forceSingleMaterial( 
		boolean pFlag 
	) { 
		if ( !pFlag ) {
			// CSGGeometry is inherently single material
			throw new IllegalArgumentException( "CSGGeometry is inherently single material" );
		}
	}

    /** Test if this Spatial has its own custom physics defined */
    @Override
    public boolean hasPhysics() { return mPhysics != null; }
    @Override
    public PhysicsControl getPhysics() { return mPhysics; }
    
    /** If physics is active for the shape, connect it all up now */
    @Override
    public void applyPhysics(
    	PhysicsSpace		pPhysicsSpace
    ,	Node				pRoot
    ) {
    	// A CSGGeometry only applies its own physics.  If it does not have its own
    	// custom physics, then it assumes it is part of a compound shape where the
    	// physics is applied at a higher level.
    	if ( mPhysics != null ) {
    		CSGPlaceholderCollisionShape.applyPhysics( pPhysicsSpace, mPhysics, this, pRoot );
    	}
    }
    
	/** Accessor to Face oriented properties */
	@Override
	public boolean hasFaceProperties() { return( (mFaceProperties != null) && !mFaceProperties.isEmpty() ); }
	@Override
	public List<CSGFaceProperties>	getFaceProperties() { return mFaceProperties; }
	
	/** How long did it take to regenerate this shape */
	@Override
	public long getShapeRegenerationNS() { return mRegenNS; }

	/** Accessor to the debug mesh control flag */
	public boolean isDebugMesh() { return mDebugMesh; }
	public void setDebugMesh( boolean pFlag ) { mDebugMesh = pFlag; }
	
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
    
	/** Add a shape to this geometry using the given operator */
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
	public void removeAllShapes() { mShapes = null; mRegenNS = 0; }
	@Override
	public void removeShape(
		CSGShape	pShape
	) {
		if ( mShapes != null ) {
			mShapes.remove( pShape );
		}
	}
	
    /** Accessor to the Light control to apply */
    public Control getLightControl() { return mLightControl; }
    public void setLightControl(
    	Control		pLightControl
    ) {
    	mLightControl = pLightControl;
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
		this.setMesh( pMeshManager.resolveMesh( CSGMeshManager.sMasterMeshIndex ) );
	}

	/** Action to generate the mesh based on the given shapes */
	@Override
	public CSGShape regenerate(
	) throws CSGConstructionException {
		return( regenerate( CSGEnvironment.resolveEnvironment( mEnvironment, this ) ) );
	}
	@Override
	public CSGShape regenerate(
		CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		mRegenNS = 0;
		if ( (mShapes != null) && !mShapes.isEmpty() ) {
			// Time the construction
			mRegenNS = -1;						// Flag REGEN in progress
			long startTimer = System.nanoTime();
			
			// The mesh manager does not do much for a single mesh
			CSGMeshManager meshManager = new CSGMeshManager( this, true );
			
			// Sort the shapes based on their operator (as needed)
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
							// Treat a compound mesh as a single mesh
							aProduct = aProduct.merge( aShape.refresh(), meshManager, tempVars, pEnvironment );
						}
						break;
					}
				}
				if ( aProduct != null ) {
					// The overall, blended mesh represents this Geometry
					// The meshManager will retain the generated meshes and can provide
					// any given mesh based on its index.  For a singleton, we want the master.
					aProduct.toMesh( meshManager, false, tempVars, pEnvironment );

					// Return the final shape
					setError( aProduct.getError() );
					if ( mDeferSceneChanges ) synchronized( this ) {
						// Update the scene later
						mMeshManager = meshManager;
					} else {
						// Update the scene NOW
						applySceneChanges( meshManager );
					}
					return( aProduct );
				} else {
					// Nothing interesting produced, but we have no explicit error
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
		} else if ( this.mesh instanceof CSGMesh ) {
			// If in 'debug' mode, look for a possible delegate
			this.mesh = ((CSGMesh)this.mesh).resolveMesh( mDebugMesh );
			setError( null );
			
		} else if ( this.mesh != null ) {
			// Just a mesh
			setError( null );
		} else {
			// No really sure what this is if we have no shapes and not mesh????
			setError( null );
		}
		// Fall out to here only if no final Shape was produced
		return( null );
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
		aCapsule.writeSavableArrayList( (ArrayList)mFaceProperties, "faceProperties", null );
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
		// Look for specially defined tranform 
		if ( this.localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				localTransform = proxyTransform.getTransform();
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
        // Any physics?
        mPhysics = (PhysicsControl)aCapsule.readSavable( "physics", null );

        // Any custom environment?
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );
        if ( mEnvironment != null ) {
        	// Incorporate this name AND define the standard as needed
        	mEnvironment = CSGEnvironment.resolveEnvironment( mEnvironment, this );
        }
        // Look for possible face properties to apply to interior mesh/subgroup
        mFaceProperties = aCapsule.readSavableArrayList( "faceProperties", null );
        
		// Rebuild based on the shapes just loaded (which also sets mValid)
		regenerate();
		
		if ( this.isValid() ) {
	        // TangentBinormalGenerator directive
	        boolean generate = aCapsule.readBoolean( "generateTangentBinormal", false );
	        if ( generate ) {
	        	TangentBinormalGenerator.generate( this );
	        }
	        if ( mLightControl != null ) {
	        	// Build a control for every local light to keep its position in synch
	        	// with transforms applied to this Node
				CSGLightControl.applyLightControl( mLightControl
													, this.getLocalLightList()
													, null
													, this
													, false );
	        }
		} else {
			CSGEnvironment.sLogger.log( Level.WARNING, "Geometry invalid: " + this );
		}
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGGeometryRevision
													, sCSGGeometryDate
													, pBuffer ) );
	}

	////// Debug support
	@Override
	public String toString(
	) {
		return( (this.name == null) ? getInstanceKey() : getInstanceKey() + "|" + getName() );
	}
}
