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
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.AbstractShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.PointLightShadowFilter;
import com.jme3.shadow.PointLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.font.BitmapText;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;


/** Exercise the import facility */
public class CSGTestI 
	extends CSGTestSceneLoader 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestI();		    
	    app.start();
	}
	
	/** Default list of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {
		null
		
	,	"Models/CSGLoadSimpleUnit.xml"
	,	"Models/CSGLoadSimple.xml"
	,	"Models/CSGLoadMultiTexture.xml"
	,	"Models/CSGLoadCSGSamples.xml"
	
//	,	"Models/CSGLoadTextureCylinders.xml"
//	,	"Models/CSGLoadLighted.xml"
		
//	,	"Models/CSGLoadLOD.xml"
//	,	"Models/CSGLoadSmoothedPipes.xml"

	};

	public CSGTestI(
	) {
		super( null, new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		//super( new FlyCamAppState() );
		
		mSceneList = Arrays.asList( sSceneList );
		mNullSceneMsg = "<ENTER> to cycle through the scenes, QWASDZ to move, <ESC> to exit";
	}
	
    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    

        /** Raw shadow elements */
        AmbientLight al = new AmbientLight( ColorRGBA.White.mult(0.2f) );
        rootNode.addLight( al );
        
        Geometry ground = new Geometry( "soil", new Box( 200f, -0.5f, 200f ) );
        ground.setLocalTranslation( 0f, -1.0f, 0f );
        Material unshaded = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        Material matGroundU = unshaded.clone();
        matGroundU.setColor( "Color", ColorRGBA.Green.mult( 0.5f ) );
        ground.setMaterial( matGroundU );
        ground.setShadowMode( ShadowMode.Receive );
        rootNode.attachChild( ground );
        
        Geometry box = new Geometry("box", new Box( 0.5f, 1f, 3f ));
        Material boxMaterial = assetManager.loadMaterial( "Textures/Terrain/BrickWall/BrickWall2.j3m" );
        box.setMaterial( boxMaterial );
        box.setShadowMode( RenderQueue.ShadowMode.CastAndReceive );
        box.setLocalTranslation( 1f, 0.5f, 1f );
        TangentBinormalGenerator.generate( box );
        
        rootNode.attachChild(box);

        // Add the light source
        if ( false ) {
        	loadElement( "Lights/ShadowPointLight.xml", new StringBuilder() );
        	//loadElement( "Lights/ShadowDirectionalLight.xml", new StringBuilder() );
        } else {
	        PointLight aLight = new PointLight( new Vector3f( 10f, 10f, 10f )
	        									, new ColorRGBA( 1f, 0.5f, 0f, 1f )
	        									, 50f );
	        rootNode.addLight( aLight );
	        
	        PointLightShadowRenderer plsr = new PointLightShadowRenderer(assetManager, 512 );
	        //plsr.setLight( aLight );
	        plsr.setEdgeFilteringMode( EdgeFilteringMode.PCF4 );
	        plsr.setShadowZExtend(15);
	        plsr.setShadowZFadeLength(5);
	        //plsr.setFlushQueues(false);
	        //plsr.displayFrustum();
	        //plsr.displayDebug();
	        
	        //viewPort.addProcessor(plsr);
        
	        PointLightShadowFilter plsf = new PointLightShadowFilter(assetManager, 512 );
	        plsf.setLight( aLight );    
	        plsf.setShadowZExtend(15);
	        plsf.setShadowZFadeLength(5);
	        plsf.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
	        plsf.setEnabled(false);
	
	        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
	        fpp.addFilter(plsf);
	        viewPort.addProcessor(fpp);
        }
    }

}