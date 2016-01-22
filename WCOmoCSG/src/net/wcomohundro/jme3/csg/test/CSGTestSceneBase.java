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
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.AppState;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.NonCachingKey;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bullet.BulletAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Triangle;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.shadow.AbstractShadowRenderer;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.PointLightShadowFilter;
import com.jme3.shadow.PointLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.font.BitmapText;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;


/** Base application that can provides some standard services for Test Scene displays */
public abstract class CSGTestSceneBase 
	extends SimpleApplication 
{
	/** Scene being displayed */
	protected Spatial			mLastScene;
	/** Spot for a bit of text */
	protected BitmapText		mTextDisplay;
	/** String to post */
	protected Stack<String>		mPostText;
	protected boolean			mRefreshText;
	/** Background action thread */
	protected Thread			mActionThread;
	/** Active item for wireframe display */
	protected Geometry			mWireframe;
    /** Prepared Physics Environment, using jBullet */
    protected BulletAppState    mPhysicsState;
	/** Capture file path */
	protected String			mCapturePath;
	/** Video capture */
	protected AppState			mVideoCapture;
	/** Image capture */
	protected AppState			mImageCapture;
	

	/** This is a base class designed for subclass override */
	protected CSGTestSceneBase(
		AppState...	pInitialStates
	) {
		super( pInitialStates );
		
		mPostText = new Stack();
		
		// Default capture path for video and image
		mCapturePath = "C:/Temp/JME3/";
	}
	
    @Override
    public void simpleInitApp(
    ) {
    	// Standard init
	    commonApplicationInit();
    }
    /** FOR POSSIBLE SUBCLASS OVERRIDE */
    protected void commonApplicationInit(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
        cam.setFrustumPerspective( 45f, (float)cam.getWidth() / cam.getHeight(), 0.1f, 250f);
	    
	    // Establish the text display
	    mTextDisplay = CSGTestDriver.defineTextDisplay( this, this.guiFont );
	    
	    // Support Sceen shots
	    if ( mCapturePath != null ) {
	    	mImageCapture = new ScreenshotAppState( mCapturePath );
	    	this.stateManager.attach( mImageCapture );
	    }
        // Ready interaction
        createListeners();
        
        // Register the XML handlers
        File aDir = new File( "./Assets" );
        if ( aDir.isDirectory() ) {
        	assetManager.registerLocator( "./Assets", FileLocator.class );
        }
        assetManager.registerLoader( com.jme3.scene.plugins.XMLLoader.class, "xml" );
        
        // By default, assume the entire root is the scene we use for click-on
        mLastScene = this.rootNode;
        
        // If the subclass is Runnable, then start a thread to service it
        if ( this instanceof Runnable ) {
    		// Start the background thread that loads the scenes
        	mActionThread = new Thread( null, (Runnable)this, "BackgroundTestAction" );
        	mActionThread.setPriority( Thread.MIN_PRIORITY );
        	mActionThread.setDaemon( true );
        	mActionThread.start();
        }
    }
      
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
        inputManager.addMapping( "video"
        ,   new KeyTrigger( KeyInput.KEY_R ) );
        inputManager.addMapping( "pickItem"
        ,   new MouseButtonTrigger( MouseInput.BUTTON_RIGHT ) );
        inputManager.addMapping( "wireframe"
        ,   new MouseButtonTrigger( 3 ) );
        inputManager.addMapping( "wireframe"
        ,   new MouseButtonTrigger( 4 ) );
        
        ActionListener aListener = new CSGTestActionListener(); 
        inputManager.addListener( aListener, "pickItem" );
        inputManager.addListener( aListener, "wireframe" );
        
        if ( mCapturePath != null ) {
        	inputManager.addListener( aListener, "video" );
        }
    }  
    
    /** Inner helper class that processes user input */
    protected class CSGTestActionListener
    	implements ActionListener
    {
        public void onAction(
            String      pName
        ,   boolean     pKeyPressed
        ,   float       pTimePerFrame
        ) {
            if ( pKeyPressed ) {
                if ( pName.equals( "pickItem" ) ) {
                	// Report on the click
                	mPostText.push( resolveSelectedItem() );
                	mRefreshText = true;
                	
                } else if ( pName.equals( "wireframe" ) ) {
                	// Report on the click
                	CollisionResult aCollision = resolveSelectedCollision();
                	if ( aCollision != null ) {
	                	mWireframe = aCollision.getGeometry();
	                	mWireframe.getMaterial().getAdditionalRenderState().setWireframe( true );
	                	
	                	Triangle aTriangle = aCollision.getTriangle( null );
	                	StringBuilder aBuffer = new StringBuilder( 128 );
	                	aBuffer.append( aTriangle.get1() ).append( " / " )
	                		.append( aTriangle.get2() ).append( " / " )
	                		.append( aTriangle.get3() );
	                	mPostText.push( aBuffer.toString() );
	                	mRefreshText = true;
                	}
                } else if ( pName.equals( "video" ) ) {
                	// Toggle the video capture
                	if ( mVideoCapture == null ) {
                    	CSGTestDriver.postText( CSGTestSceneBase.this, mTextDisplay, "Recording" );

                    	mVideoCapture = new VideoRecorderAppState( new File( mCapturePath + "CSGCapture.mpeg" ));
                		stateManager.attach( mVideoCapture );
                	} else {
                		stateManager.detach( mVideoCapture );
                		mVideoCapture = null;
                    	CSGTestDriver.postText( CSGTestSceneBase.this, mTextDisplay, "Recording Complete" );
                	}
                }
            } else {
            	if ( pName.equals( "pickItem" ) ) {
            		if ( !mPostText.isEmpty() ) mPostText.pop();
            		mRefreshText = true;
            		
            	} else if ( pName.equals( "wireframe" ) ) {
            		if ( mWireframe != null ) {
                		mWireframe.getMaterial().getAdditionalRenderState().setWireframe( false );            			
            			mWireframe = null;
            			
                		if ( !mPostText.isEmpty() ) mPostText.pop();
                		mRefreshText = true;
            		}
            	}
            }
        }
    }

    @Override
    public void update(
    ) {
    	super.update();
    	
    	if ( mRefreshText ) {
    		String aMessage = (mPostText.isEmpty()) ? "" : mPostText.peek();
        	CSGTestDriver.postText( this, mTextDisplay, aMessage );    			
        	mRefreshText = false;
    	}
    }
    
    /** What is the user looking at? */
    protected String resolveSelectedItem(
    ) {
    	String itemName = "...nothing...";
    	Geometry picked = resolveSelectedGeometry();
    	if ( picked != null ) {
    		itemName = picked.getName();
    	}
    	return( itemName );
    }
    protected Geometry resolveSelectedGeometry(
    ) {
    	CollisionResult aCollision = resolveSelectedCollision();
    	return( (aCollision == null) ? null : aCollision.getGeometry() );
    }
    protected CollisionResult resolveSelectedCollision(
    ) {
    	CollisionResults results = new CollisionResults();
    	
        // To pick what the camera is directly looking at
        //Ray aRay = new Ray( cam.getLocation(), cam.getDirection() );
         
        // To pick what the mouse is over
        Vector2f click2d = inputManager.getCursorPosition();
        Vector3f click3d = cam.getWorldCoordinates( new Vector2f(click2d.x, click2d.y), 0f).clone();
        Vector3f dir = cam.getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();
        Ray aRay = new Ray( click3d, dir );
        
        // What all was selected
        if ( mLastScene != null ) {
	        mLastScene.collideWith( aRay, results );
	        if ( results.size() > 0 ) {
	        	return( results.getClosestCollision() );
	        }
        }
    	return( null );
    }

}