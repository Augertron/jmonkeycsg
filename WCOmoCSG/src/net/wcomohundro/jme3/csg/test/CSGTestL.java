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

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.AppState;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.asset.NonCachingKey;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.PointLightShadowFilter;
import com.jme3.shadow.PointLightShadowRenderer;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGGeonode;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Run progressive regeneration
 */
public class CSGTestL 
	extends CSGTestSceneBase
	implements Runnable
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestL();		    
	    app.start();
	}

	/** Dynamic shapes */
	protected int			mUpdateCounter;
	protected CSGShape		mShape1, mShape2, mShapePrior;
	protected CSGSpatial 	mCSGBlend;
	/** Flag controlling the rebuilding process */
	protected boolean		mActionActive;

	public CSGTestL(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		//super( new FlyCamAppState() );
	}
	
    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
		
		this.mPostText.push( "<SPC> to add a cylinder, QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;

	    Mesh mesh1 = new Box( 1, 1, 1 );
	    mShape1 = new CSGShape( "ABox", mesh1 );
	    
	    //Mesh mesh2 = new Box( 0.5f, 2f, 0.5f );
	    //Mesh mesh2 = new CSGCylinder( 32, 32, 0.5f, 1.5f );
	    Mesh mesh2 = new CSGCylinder( 16, 16, 0.5f, 1.5f );
	    mShape2 = new CSGShape( "ACylinder", mesh2 );
	    if ( true ) {
	    	Material mat1 = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
	    	mat1.setColor( "Color", ColorRGBA.Yellow );
	    	mShape2.setMaterial( mat1 );
	    }
	    mCSGBlend = new CSGGeonode( "ABlend" ); // new CSGGeometry( "ABlend" );
	    mCSGBlend.setDefaultMaterial( new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" ) );
	    ((CSGGeonode)mCSGBlend).forceSingleMaterial( true );
	    rootNode.attachChild( mCSGBlend.asSpatial() );
	    
	    mCSGBlend.addShape( mShape1 );
	    mShapePrior = mCSGBlend.regenerate();
	    
	    // The blend is the scene, and we will regenerating on a background thread
	    mCSGBlend.deferSceneChanges( true );
	    mLastScene = mCSGBlend.asSpatial();
    }

    @Override
    public void simpleUpdate(
    	float tpf
    ) {
    	super.simpleUpdate( tpf );
    	
    	mUpdateCounter += 1;
        if ( mUpdateCounter == 10 ) {
        	mShape2.rotate( tpf, tpf, tpf );
            mUpdateCounter = 0;
        }
        // Apply any deferred changes now
        mCSGBlend.applySceneChanges();
    }
    
    /** Service routine to activate the interactive listeners */
    @Override
    protected void createListeners(
    ) {
    	super.createListeners();
    	
    	final SimpleApplication thisApp = this;
        inputManager.addMapping( "blend", new KeyTrigger( KeyInput.KEY_SPACE ) );
        
        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "blend" ) ) {
                	    addCylinder();
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "blend" );
    }
    
    /** Service routine to add another cylinder */
    protected void addCylinder(
    ) {
        // Confirm we are NOT in the middle of a regen
    	if ( !mActionActive ) synchronized( this ) {
    		mActionActive = true;
    		CSGTestDriver.postText( this, mTextDisplay, "** Rebuilding Shape" );
    		this.notifyAll();
    	}
    }

    /////////////////////// Implement Runnable ////////////////
    public void run(
    ) {
    	boolean isActive = true;
    	while( isActive ) {
    		synchronized( this ) {
	    		if ( !mActionActive ) try {
	    			this.wait();
	    		} catch( InterruptedException ex ) {
	    			isActive = false;
	    			break;
	    		}
    		}
        	// Blend another shape into the prior result
        	mCSGBlend.removeAllShapes();
        	mCSGBlend.addShape( mShapePrior );
        	
        	mCSGBlend.addShape( mShape2 );
    	    mShapePrior = mCSGBlend.regenerate();
    	    
    	    mPostText.push( "Rebuilt in " + (mCSGBlend.getShapeRegenerationNS() / 1000000) + "ms" );
    		mRefreshText = true;
    		mActionActive = false;
    	}
    }

}