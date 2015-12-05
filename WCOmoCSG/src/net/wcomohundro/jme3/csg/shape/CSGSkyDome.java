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
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
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
	public static final String sCSGSkyDomeRevision="$Rev: 132 $";
	public static final String sCSGSkyDomeDate="$Date: 2015-12-01 21:27:59 -0600 (Tue, 01 Dec 2015) $";
	
	/** The standard transform the tilts the mesh 90 degrees */
	protected static final Transform	sStandardTransform;
	static {
		Quaternion aRotation = new Quaternion();
		aRotation.fromAngles( FastMath.HALF_PI, 0, 0 );
		sStandardTransform = new Transform( aRotation );
	}
	
	/** Basic sphere parameters as needed */
	protected float		mRadius;
	protected int		mAxisSamples;
	protected int		mRadialSamples;

	/** Basic null constructor */
	public CSGSkyDome(
	) {
		this( "CSGSkyDome" );
	}
	/** Constructor based on a given name */
	public CSGSkyDome(
		String	pName
	) {
		this( pName, 100, 4, 6 );
	}
	/** Constructor on a name and given mesh */
	public CSGSkyDome(
		String	pName
	,	CSGMesh	pMesh
	) {
		super( pName, pMesh );
	}	
	/** Constructor based on configuration parameters */
	public CSGSkyDome( 
		String	pName
	,	float	pRadius
	,	int		pAxisSamples
	,	int		pRadialSamples
	) {
		super( pName );
		
		mRadius = pRadius;
		mAxisSamples = pAxisSamples;
		mRadialSamples = pRadialSamples;
		
		createStandardSky();
	}
	
    /** Apply gradient vertex colors */
    public void applyGradient(
    	ColorRGBA		pPoleColor
    ,	ColorRGBA		pEquatorColor
    ) {
    	if ( this.mesh instanceof CSGSphere ) {
    		((CSGSphere)this.mesh).applyGradient( pPoleColor, pEquatorColor );
    	}
    }

	/** Service routine to create a standard mesh */
	protected void createStandardSky(
	) {
		if ( this.mesh == null ) {
			CSGSphere aSky = new CSGSphere( mAxisSamples
											    , 	mRadialSamples
											    ,	mRadius
											    , 	true
											    , 	true
											    ,	TextureMode.ZAXIS
											    ,	true );
			this.mesh = aSky;
		}
		if ( this.localTransform.equals( Transform.IDENTITY ) ) {
			// Apply our default
			localTransform = sStandardTransform.clone();			
		}
        setQueueBucket( Bucket.Sky );
        setCullHint( Spatial.CullHint.Never );
        setModelBound( new BoundingSphere(Float.POSITIVE_INFINITY, Vector3f.ZERO) );
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
		
		if ( this.mesh == null ) {
			// No explicit mesh was defined, so base it on our standard cylinder
			mRadius = aCapsule.readFloat( "radius", 100 );
			mAxisSamples = aCapsule.readInt( "axisSamples", 4 );
			mRadialSamples = aCapsule.readInt( "radialSamples", 6 );
			
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
		// Create the standard as needed
		createStandardSky();
		
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
