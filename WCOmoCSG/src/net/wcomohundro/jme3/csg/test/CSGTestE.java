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
import java.util.List;
import java.util.ArrayList;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.AppState;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.NonCachingKey;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.font.BitmapText;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;


/** Exercise the import facility */
public class CSGTestE 
	extends SimpleApplication 
{
	/** Default list of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {
		null

//	,	"Models/CSGLoadSimpleUnit.xml"
//	,	"Models/CSGLoadSimple.xml"
	,	"Models/CSGLoadMultiTexture.xml"
	
//	,	"Models/CSGLoadTextureCylinders.xml"
//	,	"Models/CSGLoadLighted.xml"
		
//	,	"Models/CSGLoadLOD.xml"
//	,	"Models/CSGLoadSmoothedPipes.xml"

	};
	
	/** List of available scenes */
	protected List<String>	mSceneList;
	/** Which scene is currently being viewed */
	protected int			mSceneIndex;
	/** Spot for a bit of text */
	protected BitmapText	mTextDisplay;
	/** Video capture */
	protected AppState		mVideo;

	public CSGTestE(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		
		// Initialize the scene list
		mSceneList = new ArrayList();
		mSceneList.add( null );		// Start with a blank
		CSGTestDriver.readStrings( "./CSGTestE.txt", mSceneList, sSceneList );
	}
	
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
	    // Establish the text display
	    mTextDisplay = CSGTestDriver.defineTextDisplay( this, this.guiFont );
	    
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
    
    /** Service routine to load the scene */
    protected Spatial loadScene(
    ) {
    	// For now, rely on a FilterKey which does not support caching
    	Object aNode = null;
    	String sceneName = mSceneList.get( mSceneIndex );
    	if ( sceneName != null ) try {
    		// For testing, suppress the cache
	    	NonCachingKey aKey = new NonCachingKey( sceneName );
	    	aNode = assetManager.loadAsset( aKey );
	    	
	    	if ( aNode instanceof CSGEnvironment ) {
	    		CSGEnvironment.sStandardEnvironment = (CSGEnvironment)aNode;
	    		ConstructiveSolidGeometry.reportVersion();

	    		sceneName += " *** Processing Environment Reset";
	    		aNode = null;
	    	} else if ( (aNode instanceof ConstructiveSolidGeometry.CSGSpatial)
	    	&& !((ConstructiveSolidGeometry.CSGSpatial)aNode).isValid() ) {
	    		sceneName += " ***Invalid shape";
	    	}
    	} catch( Exception ex ) {
    		sceneName += " ***Load Scene Failed: " + ex;
    	} else {
    		sceneName = "<ENTER> to cycle through the scenes, QWASDZ to move, <ESC> to exit";
    	}
    	CSGTestDriver.postText( this, mTextDisplay, sceneName );
    	return( (Spatial)aNode );
    }
    
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
    	SimpleApplication thisApp = this;
    	
        inputManager.addMapping( "nextScene"
        ,   new KeyTrigger( KeyInput.KEY_RETURN ) );
        inputManager.addMapping( "video"
        ,   new KeyTrigger( KeyInput.KEY_R ) );
        
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
                        if ( mSceneIndex >= mSceneList.size() ) mSceneIndex = 0;
                        
                        // And load it
                	    Spatial aScene = loadScene();
                	    if ( aScene != null ) {
                	    	rootNode.attachChild( aScene );
                	    }
                    } else if ( pName.equals( "video" ) ) {
                    	// Toggle the video capture
                    	if ( mVideo == null ) {
                        	CSGTestDriver.postText( thisApp, mTextDisplay, "Recording" );

                    		mVideo = new VideoRecorderAppState( new File( "C:/Temp/JME3/CSGTestE.mpeg" ));
                    		stateManager.attach( mVideo );
                    	} else {
                    		stateManager.detach( mVideo );
                    		mVideo = null;
                        	CSGTestDriver.postText( thisApp, mTextDisplay, "Recording Complete" );
                    	}
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "nextScene" );
        inputManager.addListener( aListener, "video" );
    }

}