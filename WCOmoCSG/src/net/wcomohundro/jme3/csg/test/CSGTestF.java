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
package net.wcomohundro.jme3.csg.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.NonCachingKey;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.GhostControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;
import com.jme3.font.BitmapText;
import com.jme3.input.ChaseCamera;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;

/** Load various CSG models and try colliding with them */
public class CSGTestF 
	extends CSGTestSceneLoader 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestF();		    
	    app.start();
	}

	/** List of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {
		null

//	,	"Models/CSGLoadSimpleUnit.xml"
//	,	"Models/CSGLoadSimple.xml"
//	,	"Models/CSGLoadCSGCubeCylinder.xml"
//	,	"Models/CSGLoadCSGCubeCube.xml"
	,	"Models/CSGLoadNonMultiTexture.xml"
	,	"Models/CSGLoadLightedCorridor.xml"
	,	"Models/CSGLoadMultiTexture.xml"

	};

	public CSGTestF(
	) {
		super( null, new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		mSceneList = Arrays.asList( sSceneList );
		mNullSceneMsg = "<ENTER> to cycle through the scenes, QWASDZ to move, <SPC> to shoot, <ESC> to exit";
	}
	
    @Override
    protected void commonApplicationInit(
    ) {
        /** Set up Physics environment */
	    mPhysicsState = new BulletAppState();
        stateManager.attach( mPhysicsState );
        
		super.commonApplicationInit();    
    }
    
    /** Handle update customizations */
    @Override
    public void simpleUpdate(
        float   pTimePerFrame
    ) {
    }
    
    /** Handle rendering customizations */
    @Override
    public void simpleRender(
        RenderManager   pRenderManager
    ) {
    }

    
    protected void createProjectile(
    	Vector3f	pLocation
    ,	Vector3f	pTowards
    ) {
        Sphere aSphere = new Sphere( 32, 32, 0.25f, true, false );
        aSphere.setTextureMode( Sphere.TextureMode.Projected );

        Geometry aGeometry = new Geometry( "CannonBall", aSphere );
        aGeometry.setMaterial( assetManager.loadMaterial( "Textures/Debug/Normals.xml" ) );
        aGeometry.setLocalTranslation( pLocation );
        rootNode.attachChild( aGeometry );
        
        RigidBodyControl aBody = new RigidBodyControl( 1 );
        aGeometry.addControl( aBody );
        
        mPhysicsState.getPhysicsSpace().add( aBody );
        aBody.setLinearVelocity( pTowards );
    }

    /** Service routine to activate the interactive listeners */
    @Override
    protected void createListeners(
    ) {
    	super.createListeners();
        
        inputManager.addMapping( "shoot1"
        ,   new KeyTrigger( KeyInput.KEY_SPACE ) );

        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "shoot1" ) ) {
                    	createProjectile( cam.getLocation(), cam.getDirection().mult(10) );
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "shoot1" );
    }
    
    /** OVERRIDE: apply physics */
    @Override
    protected void applyPhysics(
    	CSGElement 		pSpatial
    ) {
    	if ( pSpatial instanceof Node ) {
    		// Make every child a phsyical thing with default settings
    		for( Spatial aChild : ((Node)pSpatial).getChildren() ) {
	        	RigidBodyControl aBody = new RigidBodyControl( 0.0f );
	        	aBody.setApplyPhysicsLocal( true );
	        	aChild.addControl( aBody );
	        	
	        	mPhysicsState.getPhysicsSpace().addAll( aChild );
    		}
    	}
    }

}