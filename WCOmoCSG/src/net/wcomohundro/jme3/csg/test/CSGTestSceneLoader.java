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
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;


/** Base application that can "load" scenes via the XML asset loader */
public class CSGTestSceneLoader 
	extends CSGTestSceneBase 
	implements Runnable
{
	public static void main(
		String[] args
	) {
		System.out.println( "logging config: " 
					+ System.getProperty( "java.util.logging.config.file" )
					+ "/" + System.getProperty( "java.util.logging.config.class" ) );
		CSGVersion.reportVersion();
		
	    SimpleApplication app;
	    app = new CSGTestE( args );		// Cycle through imports listed in CSGTestE.txt
	    app.start();
	}
	
	
	/** List of available scenes */
	protected List<String>		mSceneList;
	/** Which scene is currently being viewed */
	protected int				mSceneIndex;
	/** Which scene is currently being loaded */
	protected String			mLoadingScene;
	/** Background loaded scene */
	protected Spatial			mLoadedSpatial;
	protected String			mNullSceneMsg;


	/** Args are expected to tell us what to load */
	public CSGTestSceneLoader(
		String[]	pArgs
	) {
		//this( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		this( new FlyCamAppState() );
		
		if ( pArgs.length == 1 ) {
			CSGTestDriver.readStrings( pArgs[ 0 ], mSceneList, null );		
		} else if ( pArgs.length > 1 ) {
			mSceneList.addAll( Arrays.asList( pArgs ) );
		}
	}
	
	/** This is a base class designed for subclass override */
	protected CSGTestSceneLoader(
		AppState...	pInitialStates
	) {
		super( pInitialStates );
		
		// Initialize the scene list
		mSceneList = new ArrayList();
		mNullSceneMsg = "<ENTER> to cycle through the scenes, QWASDZ to move, <ESC> to exit";
	}
	
    /** OVERRIDE: to load the first scene */
    @Override
    protected void commonApplicationInit(
    ) {
    	// Do the standard setup
		super.commonApplicationInit();   	
        
        // Load the scene, leveraging the XMLImporter
		mLastScene = null;
	    loadScene();
    }
    
    /** Service routine to load the scene */
    protected void loadScene(
    ) {
        // Remove the old scene as needed
    	if ( mLastScene != null ) {
    		if ( mPhysicsState != null ) {
    			mPhysicsState.getPhysicsSpace().removeAll( mLastScene );
    		}
    		rootNode.detachChild( mLastScene );
    		mLastScene = null;
    		
    		if ( !mPostText.isEmpty() ) mPostText.pop();
    	}
    	String sceneName = mSceneList.get( mSceneIndex );
    	if ( sceneName != null ) synchronized( this ) {
    		if ( mLoadingScene == null ) {
    			mLoadingScene = sceneName;
    			CSGTestDriver.postText( this, mTextDisplay, "** LOADING ==> " + mLoadingScene );
    			this.notifyAll();
    		}
    	} else {
    		CSGTestDriver.postText( this, mTextDisplay, mNullSceneMsg );
    	}
    }
    
    /** Service routine to activate the interactive listeners */
    @Override
    protected void createListeners(
    ) {
    	super.createListeners();
    	
        inputManager.addMapping( "nextScene"
        ,   new KeyTrigger( KeyInput.KEY_RETURN ) );
        inputManager.addMapping( "priorScene"
        ,   new KeyTrigger( KeyInput.KEY_BACKSLASH ) );
        inputManager.addMapping( "abortLoad"
        ,   new KeyTrigger( KeyInput.KEY_DELETE ) );
        
        ActionListener aListener = new CSGTestActionListener(); 
        inputManager.addListener( aListener, "nextScene" );
        inputManager.addListener( aListener, "priorScene" );
        inputManager.addListener( aListener, "abortLoad" );
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
                if ( pName.equals( "nextScene" ) ) {
                    // Select next scene
                    mSceneIndex += 1;
                    if ( mSceneIndex >= mSceneList.size() ) mSceneIndex = 0;
                    
                    // And load it
            	    loadScene();
            	    
                } else if ( pName.equals( "priorScene" ) ) {
                    // Select prior scene
                    mSceneIndex -= 1;
                    if ( mSceneIndex < 0 ) mSceneIndex = mSceneList.size() -1;
                    
                    // And load it
            	    loadScene();
                } else if ( pName.equals( "abortLoad" ) ) {
            		if ( mLoadingScene != null ) {
            			CSGTestSceneLoader.this.mActionThread.interrupt();
            		}
                }
            } else {
            }
        }
    }

    @Override
    public void update(
    ) {
    	super.update();
    	
    	if ( mLoadedSpatial != null ) {
    		rootNode.attachChild( mLoadedSpatial );
    		mLastScene = mLoadedSpatial;
    		mLoadedSpatial = null;
    	}
    }
    
    /////////////////////// Implement Runnable ////////////////
    public void run(
    ) {
    	boolean isActive = true;
    	StringBuilder aBuffer = new StringBuilder( 256 );
    	
    	while( isActive ) {
    		synchronized( this ) {
	    		if ( mLoadingScene == null ) try {
	    			this.wait();
	    		} catch( InterruptedException ex ) {
	    			isActive = false;
	    			mLoadingScene = null;
	    		}
    		}
    		aBuffer.setLength( 0 );
    		StringBuilder reportString = null;
    		try {
    			reportString = loadElement( mLoadingScene, aBuffer );
    		} catch( CSGConstructionException ex ) {
    			reportString = CSGConstructionException.reportError( ex, " // ", aBuffer );
    		}
    		mLoadingScene = null;
    		
    		if ( reportString != null ) {
    			mPostText.push( reportString.toString() );
    			mRefreshText = true;
    		}
    	}
    }
    
    /** Service routine to load and process an element */
    protected StringBuilder loadElement(
    	String			pElementFile
    ,	StringBuilder	pBuffer
    ) {
		if ( pElementFile != null ) try {
			pBuffer.append( pElementFile );
			
			// Suppress any asset caching
	    	NonCachingKey aKey = new NonCachingKey( pElementFile );
	    	Object aNode = assetManager.loadAsset( aKey );
	    	
	    	if ( aNode instanceof CSGEnvironment ) {
	    		CSGEnvironment.resetEnvironment( (CSGEnvironment)aNode );
	    		CSGVersion.reportVersion();

	    		pBuffer.append( " *** Processing Environment Reset" );
	    		
	    	} else if ( aNode instanceof Spatial ) {
	        	if ( aNode instanceof CSGElement ) {
	        		// We know a bit more about Elements
	        		CSGElement csgSpatial = (CSGElement)aNode;
	    			if ( csgSpatial.isValid() ) {
	    				// Include timing
	    				pBuffer.append( " (" + (csgSpatial.getShapeRegenerationNS() / 1000000) + "ms)" );
	    				
	    				// Assign physics if active
	    				if ( mPhysicsState != null ) {
	    					this.applyPhysics( csgSpatial );
	    				}
	    			} else {
	    				// Something bogus in the construction
	    				pBuffer.append( " *** Invalid shape: " );
	    				CSGConstructionException.reportError( csgSpatial.getError(), " // ", pBuffer );
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
	    		
	    		pBuffer.append( " *** SceneProcessor added" );
	    		
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

	            pBuffer.append( " *** PostFilter added" );
	    	}
    	} catch( Exception ex ) {
    		pBuffer.append( " ***Load Scene Failed: " + ex );
    		ex.printStackTrace( System.err );

		} finally {
			mLoadingScene = null;
		} else {
			// Nothing loaded
			return( null );
		}
		return( pBuffer );
    }
    
    /** FOR SUBCLASS OVERRIDE: apply physics */
    protected void applyPhysics(
    	CSGElement 		pSpatial
    ) {
    	// Let the element decide how to adjust its physics
    	pSpatial.applyPhysics( mPhysicsState.getPhysicsSpace(), null );
    }
}