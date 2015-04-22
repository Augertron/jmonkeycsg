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
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;

/** Zap the LevelOfDetail */
public class CSGTestG 
	extends SimpleApplication 
{
	/** List of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {
		null

	,	"Models/CSGLoadLOD.xml"

	};
	
	/** Which scene is currently being viewed */
	protected int				mSceneIndex;

	public CSGTestG(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
       /** Ready interaction */
        createListeners();
        
        /** Load the scene, leveraging the XMLImporter */
        assetManager.registerLocator( "./Assets", FileLocator.class );
        assetManager.registerLoader( com.jme3.scene.plugins.XMLLoader.class, "xml" );
	    
	    Spatial aScene = loadScene();
	    if ( aScene != null ) {
	    	rootNode.attachChild( aScene );
	    }
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

    /** Service routine to load the scene */
    protected Spatial loadScene(
    ) {
    	// For now, rely on a FilterKey which does not support caching
    	Object aNode = null;
    	String sceneName = sSceneList[ mSceneIndex ];
    	if ( sceneName != null ) try {
    		// For testing, suppress any caching
	    	NonCachingKey aKey = new NonCachingKey( sceneName );
	    	aNode = assetManager.loadAsset( aKey );
    	} catch( Exception ex ) {
    		System.out.println( "***Load Scene Failed: " + ex );
    	}
    	return( (aNode instanceof Spatial) ? (Spatial)aNode : null );
    }
    
    /** Service routine to adjust the level of detail */
    protected void adjustLOD(
    	Spatial		aSpatial
    ) {
    	if ( aSpatial instanceof Node ) {
    		Node aNode = (Node)aSpatial;
    		for( Spatial aChild : aNode.getChildren() ) {
    			adjustLOD( aChild );
    		}
    	} else {
    		Geometry aGeom = (Geometry)aSpatial;
    		int lodLevels = aGeom.getMesh().getNumLodLevels();
    		if ( lodLevels > 0 ) {
    			int curLevel = aGeom.getLodLevel() + 1;
    			if ( curLevel >= lodLevels ) curLevel = 0;
    			aGeom.setLodLevel( curLevel );
    		}
    	}
    }
    
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
        inputManager.addMapping( "nextScene"
        ,   new KeyTrigger( KeyInput.KEY_RETURN ) );
        
        inputManager.addMapping( "lod"
        ,   new KeyTrigger( KeyInput.KEY_SPACE ) );

        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "nextScene" ) ) {
                        // Remove the old scene
                        rootNode.detachAllChildren();
                        
                        // Select next scene
                        mSceneIndex += 1;
                        if ( mSceneIndex >= sSceneList.length ) mSceneIndex = 0;
                        
                        // And load it
                	    Spatial aScene = loadScene();
                	    if ( aScene != null ) {
                	    	rootNode.attachChild( aScene );
                	    }
                    } else if ( pName.equals( "lod" ) ) {
                    	adjustLOD( rootNode );
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "nextScene" );
        inputManager.addListener( aListener, "lod" );
    }

}