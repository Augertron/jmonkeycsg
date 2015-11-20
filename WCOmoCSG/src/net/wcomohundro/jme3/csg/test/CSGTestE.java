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
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;


/** Exercise the import facility */
public class CSGTestE 
	extends SimpleApplication 
	implements Runnable
{
	/** Default list of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {

		"Models/CSGLoadSimpleUnit.xml"
	,	"Models/CSGLoadSimple.xml"
	,	"Models/CSGLoadMultiTexture.xml"
	,	"Models/CSGLoadCSGSamples.xml"
	
//	,	"Models/CSGLoadTextureCylinders.xml"
//	,	"Models/CSGLoadLighted.xml"
		
//	,	"Models/CSGLoadLOD.xml"
//	,	"Models/CSGLoadSmoothedPipes.xml"

	};
	
	/** List of available scenes */
	protected List<String>	mSceneList;
	/** Which scene is currently being viewed */
	protected int			mSceneIndex;
	/** Scene loader thread */
	protected Thread		mLoaderThread;
	/** Which scene is currently being loaded */
	protected String		mLoadingScene;
	/** Background loaded scene */
	protected Spatial		mLoadedSpatial;
	protected Spatial		mLastScene;
	/** Spot for a bit of text */
	protected BitmapText	mTextDisplay;
	/** String to post */
	protected Stack<String>	mPostText;
	protected boolean		mRefreshText;
	/** Video capture */
	protected AppState		mVideo;

	public CSGTestE(
		String[]	pArgs
	) {
		//super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		super( new FlyCamAppState() );
		
		mPostText = new Stack();
		
		// Initialize the scene list
		mSceneList = new ArrayList();
		mSceneList.add( null );		// Start with a blank

		if ( pArgs.length == 0 ) {
			CSGTestDriver.readStrings( "./Assets/Tests/CSGTestE.txt", mSceneList, sSceneList );			
		} else if ( pArgs.length == 1 ) {
			CSGTestDriver.readStrings( pArgs[ 0 ], mSceneList, sSceneList );		
		} else if ( pArgs.length > 1 ) {
			mSceneList.addAll( Arrays.asList( pArgs ) );
		}
		
		// Start the background thread that loads the scenes
		mLoaderThread = new Thread( null, this, "SceneLoader" );
		mLoaderThread.setPriority( Thread.MIN_PRIORITY );
		mLoaderThread.setDaemon( true );
		mLoaderThread.start();
	}
	
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
        cam.setFrustumPerspective( 45f, (float)cam.getWidth() / cam.getHeight(), 0.1f, 100f);
	    
	    // Establish the text display
	    mTextDisplay = CSGTestDriver.defineTextDisplay( this, this.guiFont );
	    
	    // Support Sceen shots
	    ScreenshotAppState screenShotState = new ScreenshotAppState( "C:/Temp/JME3/" );
	    this.stateManager.attach( screenShotState );
	    
        /** Ready interaction */
        createListeners();
        
        /** Load the scene, leveraging the XMLImporter */
        File aDir = new File( "./Assets" );
        if ( aDir.isDirectory() ) {
        	assetManager.registerLocator( "./Assets", FileLocator.class );
        }
        assetManager.registerLoader( com.jme3.scene.plugins.XMLLoader.class, "xml" );
	    
	    loadScene();
    }
    
    /** Service routine to load the scene */
    protected void loadScene(
    ) {
    	// For now, rely on a FilterKey which does not support caching
    	Object aNode = null;
    	String sceneName = mSceneList.get( mSceneIndex );
    	if ( sceneName != null ) synchronized( this ) {
    		if ( mLoadingScene == null ) {
    			mLoadingScene = sceneName;
    			CSGTestDriver.postText( this, mTextDisplay, "** LOADING ==> " + mLoadingScene );
    			this.notifyAll();
    		}
    	} else {
    		CSGTestDriver.postText( this, mTextDisplay
    		, "<ENTER> to cycle through the scenes, QWASDZ to move, <ESC> to exit" );
    	}
    }
    
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
    	final SimpleApplication thisApp = this;
    	
        inputManager.addMapping( "nextScene"
        ,   new KeyTrigger( KeyInput.KEY_RETURN ) );
        inputManager.addMapping( "priorScene"
        ,   new KeyTrigger( KeyInput.KEY_BACKSLASH ) );
        inputManager.addMapping( "video"
        ,   new KeyTrigger( KeyInput.KEY_R ) );
        inputManager.addMapping( "pickItem"
        ,   new MouseButtonTrigger( MouseInput.BUTTON_RIGHT ) );
        
        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "nextScene" ) ) {
                        // Remove the old scene
                    	if ( mLastScene != null ) {
                    		rootNode.detachChild( mLastScene );
                    		mLastScene = null;
                    		if ( !mPostText.isEmpty() ) mPostText.pop();
                    	}
                        // Select next scene
                        mSceneIndex += 1;
                        if ( mSceneIndex >= mSceneList.size() ) mSceneIndex = 0;
                        
                        // And load it
                	    loadScene();
                    } else if ( pName.equals( "priorScene" ) ) {
                        // Remove the old scene
                    	if ( mLastScene != null ) {
                    		rootNode.detachChild( mLastScene );
                    		mLastScene = null;
                    		if ( !mPostText.isEmpty() ) mPostText.pop();
                    	}
                        // Select next scene
                        mSceneIndex -= 1;
                        if ( mSceneIndex < 0 ) mSceneIndex = mSceneList.size() -1;
                        
                        // And load it
                	    loadScene();
                	    
                    } else if ( pName.equals( "pickItem" ) ) {
                    	// Report on the click
                    	mPostText.push( resolveSelectedItem() );
                    	mRefreshText = true;

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
                } else {
                	if ( pName.equals( "pickItem" ) ) {
                		if ( !mPostText.isEmpty() ) mPostText.pop();
                		mRefreshText = true;
                	}
                }
            }
        };  
        inputManager.addListener( aListener, "nextScene" );
        inputManager.addListener( aListener, "priorScene" );
        inputManager.addListener( aListener, "pickItem" );
        inputManager.addListener( aListener, "video" );
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
    	if ( mLoadedSpatial != null ) {
    		rootNode.attachChild( mLoadedSpatial );
    		mLastScene = mLoadedSpatial;
    		mLoadedSpatial = null;
    	}
    }
    
    /** What is the user looking at? */
    protected String resolveSelectedItem(
    ) {
    	String itemName = "...nothiing...";
    	
    	// Cast a ray in the direction of the camera and see what gets hit
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
	        	Geometry selectedItem = results.getClosestCollision().getGeometry();
	        	itemName = selectedItem.getName();
	        }
        }
    	return( itemName );
    }

    /////////////////////// Implement Runnable ////////////////
    public void run(
    ) {
    	boolean isActive = true;
    	while( isActive ) {
    		synchronized( this ) {
	    		if ( mLoadingScene == null ) try {
	    			this.wait();
	    		} catch( InterruptedException ex ) {
	    			isActive = false;
	    			mLoadingScene = null;
	    		}
    		}
    		String reportString = mLoadingScene;
    		if ( mLoadingScene != null ) try {
    			// Suppress any asset caching
    	    	NonCachingKey aKey = new NonCachingKey( mLoadingScene );
    	    	Object aNode = assetManager.loadAsset( aKey );
    	    	
    	    	if ( aNode instanceof CSGEnvironment ) {
    	    		CSGEnvironment.sStandardEnvironment = (CSGEnvironment)aNode;
    	    		CSGVersion.reportVersion();

    	    		reportString += " *** Processing Environment Reset";
    	    		
    	    	} else if ( aNode instanceof Spatial ) {
    	    		if ( aNode instanceof CSGSpatial ) {
    	    			CSGSpatial csgSpatial = (CSGSpatial)aNode;
    	    			if ( csgSpatial.isValid() ) {
    	    				// Include timing
    	    				reportString += ( " (" + (csgSpatial.getShapeRegenerationNS() / 1000000) + "ms)" );
    	    			} else {
    	    				// Something bogus in the construction
    	    				reportString += " ***Invalid shape";
    	    			}
    	    		}
    	    		mLoadedSpatial = (Spatial)aNode;
    	    		
    	    	} else if ( aNode instanceof SceneProcessor ) {
    	    		Light aLight = null;
    	    		if ( aNode instanceof PointLightShadowRenderer ) {
    	    			aLight = ((PointLightShadowRenderer)aNode).getLight();
    	    		} else if ( aNode instanceof DirectionalLightShadowRenderer ) {
    	    			aLight = ((DirectionalLightShadowRenderer)aNode).getLight();
    	    		}
    	    		if ( aLight != null ) {
    	    			rootNode.addLight( aLight );
    	    		}
    	    		viewPort.addProcessor( (SceneProcessor)aNode );
    	    		
    	    		reportString += " *** SceneProcessor added";
    	    		
    	    	} else if ( aNode instanceof Filter ) {
    	    		Light aLight = null;
    	    		if ( aNode instanceof PointLightShadowFilter ) {
    	    			aLight = ((PointLightShadowFilter)aNode).getLight();
    	    		}
    	    		if ( aLight != null ) {
    	    			rootNode.addLight( aLight );
    	    		}
    	            FilterPostProcessor fpp = new FilterPostProcessor( assetManager );
    	            fpp.addFilter( (Filter)aNode );
    	            viewPort.addProcessor( fpp );

    	            reportString += " *** PostFilter added";
    	    	}
        	} catch( Exception ex ) {
        		reportString += " ***Load Scene Failed: " + ex;

    		} finally {
    			mLoadingScene = null;
    		}
    		if ( reportString != null ) {
    			mPostText.push( reportString );
    			mRefreshText = true;
    		}
    	}
    }
}