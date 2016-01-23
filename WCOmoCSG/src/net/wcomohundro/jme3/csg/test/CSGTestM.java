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
import java.util.List;

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
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
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
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Test case taken from CSG forum on jme -- remove a randomly positioned sphere from a cube
 */
public class CSGTestM 
	extends CSGTestSceneBase
	implements Runnable
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestM();		    
	    app.start();
	}

	protected static List<Vector3f> 	sPositions = new ArrayList<Vector3f>();
	static {
		sPositions.add( new Vector3f(0.80005103f,0.06314170f,0.37440395f));
		sPositions.add( new Vector3f(0.28343803f,0.43108004f,0.48746461f));
		sPositions.add( new Vector3f(0.12868416f,0.01397777f,0.45756876f));
		sPositions.add( new Vector3f(0.77755153f,0.57811391f,0.31153154f));
		sPositions.add( new Vector3f(0.54848605f,0.43463808f,0.08887690f));
		sPositions.add( new Vector3f(0.44293296f,0.26147729f,0.59465259f));
		sPositions.add( new Vector3f(0.49685276f,0.70028597f,0.25030762f));
		sPositions.add( new Vector3f(0.69402820f,0.70010322f,0.17900819f));
		sPositions.add( new Vector3f(0.60632563f,0.76545787f,0.00868839f));
		sPositions.add( new Vector3f(0.07231724f,0.82769102f,0.52386624f));
		sPositions.add( new Vector3f(0.57993245f,0.11181229f,0.35487765f));
		sPositions.add( new Vector3f(0.76333338f,0.57130849f,0.41553390f));
		sPositions.add( new Vector3f(0.90540063f,0.15399516f,0.31880611f));
		sPositions.add( new Vector3f(0.48946434f,0.56392515f,0.92612267f));
		sPositions.add( new Vector3f(0.20164603f,0.28323406f,0.33062303f));
		sPositions.add( new Vector3f(0.48186016f,0.83080268f,0.67133898f));
		sPositions.add( new Vector3f(0.46120077f,0.96177906f,0.63590240f));
		sPositions.add( new Vector3f(0.24107927f,0.82240766f,0.69494921f));
		sPositions.add( new Vector3f(0.80022192f,0.86946529f,0.77864534f));
		sPositions.add( new Vector3f(0.21218884f,0.17488194f,0.89337271f));
		sPositions.add( new Vector3f(0.97576815f,0.74024606f,0.29970086f));
		sPositions.add( new Vector3f(0.17829001f,0.22013688f,0.41068947f));
		sPositions.add( new Vector3f(0.86896533f,0.60720319f,0.40294641f));
		sPositions.add( new Vector3f(0.60520381f,0.94561607f,0.30677772f));
		sPositions.add( new Vector3f(0.79588902f,0.16585821f,0.37771285f));
		sPositions.add( new Vector3f(0.74539816f,0.76406616f,0.57494253f));
		sPositions.add( new Vector3f(0.27781421f,0.94732559f,0.99107915f));
		sPositions.add( new Vector3f(0.55746430f,0.05100375f,0.81786209f));
		sPositions.add( new Vector3f(0.19038093f,0.98681915f,0.40325123f));
		sPositions.add( new Vector3f(0.64273006f,0.25286716f,0.04005140f));
		sPositions.add( new Vector3f(0.50846046f,0.35735011f,0.22371590f));
		sPositions.add( new Vector3f(0.04853362f,0.85462672f,0.32378966f));
		sPositions.add( new Vector3f(0.43576503f,0.99821311f,0.55919290f));
		sPositions.add( new Vector3f(0.33736432f,0.96088225f,0.70555288f));
		sPositions.add( new Vector3f(0.26486641f,0.21563309f,0.56654203f));
		sPositions.add( new Vector3f(0.39975405f,0.90919274f,0.80785227f));
		sPositions.add( new Vector3f(0.54797322f,0.55813938f,0.40844613f));
	}
	
	protected CSGGeometry	mBlendedShape;
	protected CSGShape		mPreviousShape;
	protected CSGShape		mSphere;
	protected int			mPositionIndex;
	protected int			mAction;

	public CSGTestM(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		//super( new FlyCamAppState() );
	}
	
    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
		
		this.mPostText.push( "<SPC> to blend, QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
		
		mBlendedShape = new CSGGeometry( "TheBlend" );
		mBlendedShape.deferSceneChanges( true );

	    Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
	    mBlendedShape.setMaterial( mat_csg );
	  	
	  	CSGShape aCube = new CSGShape( "Box", new CSGBox(1,1,1) );
	  	mBlendedShape.addShape(aCube);
	  	mPreviousShape = mBlendedShape.regenerate();
	  	
	  	mSphere = new CSGShape( "Sphere", new CSGSphere( 5, 5, 0.3f ) );
	  	
	   	rootNode.attachChild( mBlendedShape );
    }
    @Override
    public void simpleUpdate(
    	float tpf
    ) {
    	super.simpleUpdate( tpf );
    	
        // Apply any deferred changes now
    	mBlendedShape.applySceneChanges();
    }

    protected void blendSphere(
    	int		pAction
    ) {
    	mBlendedShape.addShape( mPreviousShape );
      	
    	if ( mPositionIndex < sPositions.size() ) {
      		mSphere.setLocalTranslation( sPositions.get( mPositionIndex++ ) );
      	}else{
      		mSphere.setLocalTranslation( mBlendedShape.getLocalTranslation() );
        	mSphere.move( new Vector3f(   FastMath.rand.nextFloat()
        								, FastMath.rand.nextFloat()
        								, FastMath.rand.nextFloat() ).multLocal(1f) );
      	}
      	System.out.println( "sPositions.add( new Vector3f" + dump( mSphere.getLocalTranslation())+");" );
      	mBlendedShape.addShape( mSphere, CSGOperator.DIFFERENCE );
      	
      	try {
      		mPreviousShape = mBlendedShape.regenerate();
    	    mPostText.push( "Rebuilt in " + (mBlendedShape.getShapeRegenerationNS() / 1000000) + "ms" );    	    
    		mRefreshText = true;
      	} catch( Exception ex ){
      		ex.printStackTrace();
      		System.exit(1);
      	}
      	mBlendedShape.removeAllShapes();
    }
    protected String dump(
    	Vector3f pos
    ) {
		return String.format("(%01.8ff,%01.8ff,%01.8ff)",pos.x,pos.y,pos.z);
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
                	    takeAction( 1 );
                    }
                }
            }
        };  
        inputManager.addListener( aListener, "blend" );
    }
    /** Service routine to trigger the action */
    protected void takeAction(
    	int		pAction
    ) {
        // Confirm we are NOT in the middle of a regen
    	if ( mAction == 0 ) synchronized( this ) {
    		mAction = pAction;
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
	    		if ( mAction == 0 ) try {
	    			this.wait();
	    		} catch( InterruptedException ex ) {
	    			isActive = false;
	    			break;
	    		}
    		}
        	// Blend another shape into the prior result
        	blendSphere( mAction );
    		mAction = 0;
    	}
    }

}