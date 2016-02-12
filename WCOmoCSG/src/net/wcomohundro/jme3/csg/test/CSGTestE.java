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
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.plugins.blender.math.Vector3d;
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
	extends CSGTestSceneLoader 
{
	private static List<Vector3d> sDebugFace = new ArrayList(); 
	private static List<Vector3d> sDebugFace2 = new ArrayList(); 
	private static List<Vector3d> sDebugFace3 = new ArrayList(); 
	private static Vector3d sDebugVertex 
		= new Vector3d( 0.3635494112968445, -0.8776854872703552, -9.949999809265137 );
	static {
		sDebugFace2.add( new Vector3d( 0.3635494, -0.8776855, -9.95 ) );
		sDebugFace2.add( new Vector3d( 0.0, 0.0, -9.95 ) );
		sDebugFace2.add( new Vector3d( 0.6717517, -0.6717512, -9.95 ) );
		
//		sDebugFace.add( new Vector3d( 0.3635494, -0.8776855, -9.95) );
//		sDebugFace.add( new Vector3d( 0.0, 0.0, -9.95) );
//		sDebugFace.add( new Vector3d( 0.6717517, -0.6717512, -9.95) );
		
		sDebugFace.add( new Vector3d( -0.95, 0.0, -9.95 ) );
		sDebugFace.add( new Vector3d( 0.95, 0.0, -9.95 ) );
		sDebugFace.add( new Vector3d( 0.6717517, -0.6717512, -9.95 ) );
				
		sDebugFace.add( new Vector3d( -0.949999988079071, 0.0, -9.949999809265137 ) );
		sDebugFace.add( new Vector3d( 0.949999988079071, 0.0, -9.949999809265137 ) );
		sDebugFace.add( new Vector3d( 0.6717517, -0.6717512, -9.949999809265137 ) );
		
		// This one seems to corrupt the status of the other....
		sDebugFace3.add( new Vector3d( 1.1328636695395744E-8, -0.949999988079071, -9.949999809265137) );
		sDebugFace3.add( new Vector3d( 0.0, 0.0, -9.949999809265137) );
		sDebugFace3.add( new Vector3d( 0.3635494112968445, -0.8776854872703552, -9.949999809265137) );
	};
	
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestE( pArgs );		    
	    app.start();
	}

	/** Default list of input scenes to cycle through */
	protected static String[] sSceneList = new String[] {
		"Tests/CSGTestE.xml"
		
//		"Models/CSGLoadSimpleUnit.xml"
//	,	"Models/CSGLoadSimple.xml"
//	,	"Models/CSGLoadMultiTexture.xml"
//	,	"Models/CSGLoadCSGSamples.xml"
	
//	,	"Models/CSGLoadTextureCylinders.xml"
//	,	"Models/CSGLoadLighted.xml"
		
//	,	"Models/CSGLoadLOD.xml"
//	,	"Models/CSGLoadSmoothedPipes.xml"

	};

	public CSGTestE(
		String[]	pArgs
	) {
		//super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		super( pArgs, new FlyCamAppState() );
		if ( pArgs.length == 0 ) {
			mInitArgs = sSceneList;
		}
		mSceneList.add( null );		// Start with a blank
		mNullSceneMsg = "<ENTER> to cycle through the scenes, QWASDZ to move, <ESC> to exit";
	}
    
}