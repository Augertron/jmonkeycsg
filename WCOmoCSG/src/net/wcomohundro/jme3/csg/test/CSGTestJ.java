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

import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Run dynamic animation
 */
public class CSGTestJ 
	extends SimpleApplication 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestJ();		    
	    app.start();
	}

	/** Dynamic shapes */
	protected int			mUpdateCounter;
	protected CSGShape		mShape1, mShape2;
	protected CSGGeometry 	mCSGBlend;
	/** Video capture */
	protected AppState		mVideo;

	
	public CSGTestJ(
	) {
		//super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		super( new FlyCamAppState() );
	}
	
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
        /** Ready interaction */
        createListeners();
	    
	    Mesh mesh1 = new Box( 1, 1, 1 );
	    //Mesh mesh2 = new Box( 0.5f, 2f, 0.5f );
	    //Mesh mesh2 = new CSGCylinder( 32, 32, 0.5f, 1.5f );
	    Mesh mesh2 = new CSGCylinder( 16, 16, 0.5f, 1.5f );
	    mShape1 = attachShape( mesh1
	    						, new Vector3f(0, 0, 0)
	    						, ColorRGBA.Blue
	    						, "Box1" );
	    mShape2 = attachShape( mesh2
	    						, new Vector3f(0, 0, 0)
	    						, ColorRGBA.Yellow
	    						, "Box2" );
	    
	    mCSGBlend = new CSGGeometry( "ABlend" );
	    mCSGBlend.setMaterial( new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" ) );
	    rootNode.attachChild( mCSGBlend );
	    
	    mCSGBlend.addShape( mShape1 );
	    mCSGBlend.intersectShape( mShape2 );
	    mCSGBlend.regenerate();
    }

    protected CSGShape attachShape( 
    	Mesh 		pMesh
    , 	Vector3f 	pPosition
    , 	ColorRGBA 	pColor
    , 	String 		pName
    ) {
        CSGShape aShape = new CSGShape( pName, pMesh );
        aShape.setLocalTranslation( pPosition );
        
        // Draw the wireframe of the basic shape in the given color
        Material aMaterial = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
        aMaterial.getAdditionalRenderState().setWireframe( true );
        aMaterial.setColor( "Color", pColor );
        aShape.setMaterial( aMaterial );
        
        rootNode.attachChild( aShape );
        return aShape;
    }

    @Override
    public void simpleUpdate(
    	float tpf
    ) {
    	mUpdateCounter += 1;
        if ( mUpdateCounter == 10 ) {
        	mShape1.rotate( tpf, tpf, tpf );
            mShape2.rotate( -tpf, -tpf, -tpf );

            mCSGBlend.regenerate();
            mUpdateCounter = 0;
        }
    }
    
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
    	final SimpleApplication thisApp = this;
    	
        inputManager.addMapping( "video"
        ,   new KeyTrigger( KeyInput.KEY_R ) );
        
        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "video" ) ) {
                    	// Toggle the video capture
                    	if ( mVideo == null ) {
                    		mVideo = new VideoRecorderAppState( new File( "C:/Temp/JME3/CSGTestJ.mpeg" ));
                    		stateManager.attach( mVideo );
                    	} else {
                    		stateManager.detach( mVideo );
                    		mVideo = null;
                    	}
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "video" );
    }

}