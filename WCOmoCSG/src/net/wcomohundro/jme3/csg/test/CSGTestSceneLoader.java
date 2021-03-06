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
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.AbstractShadowRenderer;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.PointLightShadowFilter;
import com.jme3.shadow.PointLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.font.BitmapText;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGLibrary;
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
	
	/** Initialization args */
	protected String[]			mInitArgs;
	/** List of available scenes */
	protected List<String>		mSceneList;
	/** Which scene is currently being viewed */
	protected int				mSceneIndex;
	/** Which scene is currently being loaded */
	protected String			mLoadingScene;
	/** Background loaded scene */
	protected Spatial			mLoadedSpatial;
	protected String			mNullSceneMsg;
	protected CSGElement 		mActiveElement;
	/** The world 'context' we operate in, as determined by the last loaded scene */
	protected Node				mWorldContext;
	/** Simple counter in the update loop */
	protected int				mUpdateCounter;


	/** Args are expected to tell us what to load */
	public CSGTestSceneLoader(
		String[]	pArgs
	) {
		//this( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		this( pArgs, new FlyCamAppState() );		
	}
	
	/** This is a base class designed for subclass override */
	protected CSGTestSceneLoader(
		String[]		pArgs
	,	AppState...		pInitialStates
	) {
		super( pInitialStates );
		mInitArgs = pArgs;
		
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
		
		mLastScene = null;
		mLoadedSpatial = null;

		if ( mInitArgs == null ) {
			// Nothing defaulted
		} else if ( mInitArgs.length == 1 ) {
			// Singleton read of a file (possibly an Asset)
			Object anAsset = CSGTestDriverBase.readStrings( mInitArgs[ 0 ], mSceneList, null, assetManager );
			if ( anAsset instanceof Spatial ) {
				// Use the element just loaded
				if ( anAsset instanceof CSGElement ) {
					// Apply any deferred regeneration NOW
					((CSGElement)anAsset).regenerate( true, null );
				}
				mLoadedSpatial = (Spatial)anAsset;
				mSceneIndex = 1;
				
				if ( (mPhysicsState != null) && (mLoadedSpatial instanceof CSGElement) ) {
					this.applyPhysics( (CSGElement)mLoadedSpatial );
				}
			}
		} else if ( mInitArgs.length > 1 ) {
			// Literal list of scenes in the arguments
			mSceneList.addAll( Arrays.asList( mInitArgs ) );
		}
        // Load the scene, leveraging the XMLImporter
		if ( mLoadedSpatial == null ) {
			// Start the process of loading scenes
			loadScene();
		} else {
			mPostText.push( mNullSceneMsg );
			mRefreshText = true;
		}
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
    			CSGTestDriver.postText( this, mTextDisplay, "** LOADING ==> " + mLoadingScene, false );
    			this.notifyAll();
    		}
    	} else {
    		CSGTestDriver.postText( this, mTextDisplay, mNullSceneMsg, true );
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

    /** OVERRIDE: to attach any background loaded spatial into the current visuals */
    @Override
    public void update(
    ) {
    	super.update();
    	
		String activeLoading = mLoadingScene;
		if ( (activeLoading != null) && (mActiveElement != null) ) {
	    	mUpdateCounter += 1;
	        if ( mUpdateCounter == 10 ) {
	        	StringBuilder aBuffer = new StringBuilder( 256 );
	        	aBuffer.append( "** LOADING ==> " )
	        	       .append( activeLoading )
	        	       .append( " -- " );
	        	mActiveElement.reportStatus( aBuffer, false );
	        	
				CSGTestDriver.postText( this, mTextDisplay, aBuffer.toString(), false );
	            mUpdateCounter = 0;
	        }
		}	
    	if ( mLoadedSpatial != null ) {
    		attachLoadedSpatial( mLoadedSpatial );
    		mLoadedSpatial = null;
    	}
    }
    /** FOR SUBCLASS OVERRIDE: connect the loaded scene */
    protected void attachLoadedSpatial(
    	Spatial		pSpatial
    ) {
    	if ( pSpatial instanceof CSGElement ) {
	    	// Accept Processors/Filters
	    	FilterPostProcessor aFilterProcessor = new FilterPostProcessor( assetManager );
	    	CSGLibrary.filtersAndProcessors( pSpatial, aFilterProcessor, viewPort, assetManager );
	    	
	    	if ( aFilterProcessor.getFilterList().size() > 0 ) {
	    		// Filters were added
	    		viewPort.addProcessor( aFilterProcessor );
	    	}
    	}
    	// Include this element in the view
		rootNode.attachChild( pSpatial );
		mLastScene = pSpatial; 
		
		if ( pSpatial instanceof Node ) {
			mWorldContext = (Node)pSpatial;
		} else {
			mWorldContext = rootNode;
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
	        		mActiveElement = (CSGElement)aNode;
	        		mActiveElement.regenerate( true, CSGEnvironment.resolveEnvironment() );
	        		
	    			if ( mActiveElement.isValid() ) {
	    				// Include timing
	    				pBuffer.append( " (" + (mActiveElement.getShapeRegenerationNS() / 1000000) + "ms)" );
	    				
	    				// Assign physics if active
	    				if ( mPhysicsState != null ) {
	    					this.applyPhysics( mActiveElement );
	    				}
	    			} else {
	    				// Something bogus in the construction
	    				pBuffer.append( " *** Invalid shape: " );
	    				CSGConstructionException.reportError( mActiveElement.getError(), " // ", pBuffer );
	    			}
	    			mActiveElement = null;
	        	}
	    		// Register this to be attached to the view scene
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