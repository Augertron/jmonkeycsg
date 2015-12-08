/** Copyright (c) 2003-2014 jMonkeyEngine
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
package net.wcomohundro.jme3.csg.shape;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.logging.Level;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState.TestFunction;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGMesh;
import net.wcomohundro.jme3.csg.shape.CSGSphere.TextureMode;
import net.wcomohundro.jme3.math.CSGTransform;


/**  CSGSkyDome is a helper class that operates somewhat like the SkyFactory, but leverages
 	 what it knows about CSGMesh shapes.  It requires the underlying Mesh to be a CSGMesh.
 	 
 	 In particular, it knows how to create its own CSGSphere to mimic a 'dome' for creating 
 	 a simple sky.
 	 
*/
public class CSGSkyDome
	extends Geometry 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSkyDomeRevision="$Rev$";
	public static final String sCSGSkyDomeDate="$Date$";
	
	/** The standard transform the tilts the mesh 90 degrees */
	protected static final Transform	sStandardTransform;
	static {
		Quaternion aRotation = new Quaternion();
		aRotation.fromAngles( -FastMath.HALF_PI, 0, 0 );
		sStandardTransform = new Transform( aRotation );
	}
	
	/** Basic mesh parameters as needed */
	protected float				mRadius;
	protected int				mAxisSamples;
	protected int				mRadialSamples;
	/** Gradient colors */
	protected List<ColorRGBA>	mColors;
	

	/** Basic null constructor */
	public CSGSkyDome(
	) {
		this( "CSGSkyDome", (AssetManager)null );
	}
	/** Constructor based on a given name */
	public CSGSkyDome(
		String			pName
	,	AssetManager	pAssetManager
	) {
		this( pName, 100, 3, 8, null, null, pAssetManager );
	}
	/** Constructor on a name and given mesh */
	public CSGSkyDome(
		String	pName
	,	CSGMesh	pMesh
	) {
		super( pName, pMesh );
	}	
	/** Constructor based on configuration parameters 
	 	NOTE that if you want a material to be generated, you must provide an AssetManager
	 */
	public CSGSkyDome( 
		String			pName
	,	float			pRadius
	,	int				pAxisSamples
	,	int				pRadialSamples
	,	Class			pMeshClass
	,	List<ColorRGBA>	pGradientColors
	,	AssetManager	pAssetManager
	) {
		super( pName );
		
		mRadius = pRadius;
		mAxisSamples = pAxisSamples;
		mRadialSamples = pRadialSamples;
		
		mColors = pGradientColors;
		
		// Create the sky
		createStandardSky( pAssetManager, pMeshClass );
	}
	
    /** Apply gradient vertex colors */
    public void applyGradient(
    	List<ColorRGBA>	pColors
    ) {
    	if ( this.mesh instanceof CSGMesh ) {
    		((CSGMesh)this.mesh).applyGradient( pColors );
    	}
    }

	/** Service routine to create a standard mesh */
	protected void createStandardSky(
		AssetManager	pAssetManager
	,	Class			pSolidClass
	) {
		if ( this.mesh == null ) try {
			// Build the mesh, expected to be a variant of CSGMesh
//CSGSphere aSky = new CSGSphere( mAxisSamples, mRadialSamples, mRadius, true, true, CSGSphere.TextureMode.ZAXIS, true );

			if ( pSolidClass == null ) pSolidClass = CSGSphere.class;
			CSGMesh aSky = (CSGMesh)pSolidClass.newInstance();
			aSky.setInverted( true );

			// Apply individual customizations
			if ( aSky instanceof CSGRadial ) {
				CSGRadial skyRadial = (CSGRadial)aSky;
				skyRadial.setAxisSamples( mAxisSamples );
				skyRadial.setRadialSamples( mRadialSamples );
				skyRadial.setRadius( mRadius );
				
				if ( aSky instanceof CSGSphere ) {
					CSGSphere skySphere = (CSGSphere)aSky;
					skySphere.setTextureMode( CSGSphere.TextureMode.ZAXIS );
					skySphere.setEvenSlices( true );
				} else if ( aSky instanceof CSGRadialCapped ) {
					CSGRadialCapped skyCapped = (CSGRadialCapped)aSky;
					skyCapped.setRadiusBack( mRadius );
					skyCapped.setRadiusFront( mRadius / 10f );
					skyCapped.setZExtent( mRadius / 2.0f );
				}
			}
			// Generate the solid
			aSky.updateGeometry();		
			this.mesh = aSky;
		} catch( Exception ex ) {
			CSGEnvironment.sLogger.log( Level.CONFIG, "SkyDome failed to generate mesh: " + ex, ex );
		}
		if ( this.localTransform.equals( Transform.IDENTITY ) ) {
			// Apply our default to rotate the pole pointing north
			localTransform = sStandardTransform.clone();			
		}
		// Stolen directly from SkyFactory
        setQueueBucket( Bucket.Sky );
        setCullHint( Spatial.CullHint.Never );
        setModelBound( new BoundingSphere( Float.POSITIVE_INFINITY, Vector3f.ZERO ) );
        
        // Apply colors as needed
        if ( mColors != null ) {
        	applyGradient( mColors );
        }
        // Supply a default Material
        if ( (this.material == null) && (pAssetManager != null) ) {
        	// Various things about the material I have been gleaning from SkyFactory
        	Material skyMaterial = new Material( pAssetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
        	if ( mColors != null ) {
        		skyMaterial.setBoolean( "VertexColor", true );
        	}
        	skyMaterial.getAdditionalRenderState().setDepthWrite( false );
        	skyMaterial.getAdditionalRenderState().setDepthFunc( TestFunction.Equal );
        	this.material = skyMaterial;
        }
	}
	
	/** Support the persistence of this Geometry */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// We rely on CSGMesh.write() processing to dump the appropriate parameters
		// rather than the raw mesh buffers
		super.write( pExporter );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// Let the super do its thing
		super.read( pImporter );
		
		Class meshClass = null;
		if ( this.mesh == null ) {
			// No explicit mesh was defined
			mRadius = aCapsule.readFloat( "radius", 100 );
			mAxisSamples = aCapsule.readInt( "axisSamples", 5 );
			mRadialSamples = aCapsule.readInt( "radialSamples", 8 );
			
			String className = aCapsule.readString( "meshClass", null );
			if ( className != null ) try {
				meshClass = Class.forName( className );
			} catch( Exception ex ) {
				throw new IOException( "Invalid Mesh Class: " + ex, ex );
			}
		}
		// Look for specially defined tranform 
		if ( this.localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				// Interpret the proxy
				localTransform = proxyTransform.getTransform();
			}
		}
        // Any special color processing?
        mColors = aCapsule.readSavableArrayList( "colorGradient", null );

        // Create the standard as needed
		createStandardSky( pImporter.getAssetManager(), meshClass );
		
		// Anything special for the Material?
        boolean repeatTexture = aCapsule.readBoolean( "repeatTexture", false );
        if ( repeatTexture && (this.material != null)) {
        	MatParamTexture aParam = this.getMaterial().getTextureParam( "DiffuseMap" );
        	if ( aParam != null ) {
        		aParam.getTextureValue().setWrap( Texture.WrapMode.Repeat );
        	}
        }
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGSkyDomeRevision
													, sCSGSkyDomeDate
													, pBuffer ) );
	}
}
