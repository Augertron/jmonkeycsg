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

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.TextureKey;
import com.jme3.export.JmeExporter;
import com.jme3.export.xml.XMLExporter;
import com.jme3.light.AmbientLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;

/** Test of BinormalTangent generation */
public class CSGTestBinormalTangent 
	extends SimpleApplication 
{
	public static void main(
		String[] args
	) {
		SimpleApplication app = new CSGTestBinormalTangent();
		app.start();
	}

	public CSGTestBinormalTangent(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
        assetManager.registerLoader( com.jme3.scene.plugins.XMLLoader.class, "xml" );
	    
    	Geometry aGeometry, bGeometry, cGeometry;
    	Box aBox, bBox, cBox;
    	Material mat_csg;
        //mat_csg = assetManager.loadMaterial( "Textures/Debug/Wireframe.j3m" );
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/Rock/Rock.j3m"  );
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/BrickWall/BrickWall.j3m"  );
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/Pond/Pond.j3m"  );
        
        //mat_csg = assetManager.loadMaterial( "Textures/BrickWall/BrickWallNormalRpt.xml"  );
        //mat_csg = assetManager.loadMaterial( "Textures/CobbleStone/CobbleStoneNormalRpt.xml"  );
        //mat_csg = assetManager.loadMaterial( "Textures/CobbleStone/PebblesRpt.xml"  );
        //mat_csg = assetManager.loadMaterial( "Textures/Rock/Rock1NormalRpt.xml"  );		// Rather poor tiling
        mat_csg = assetManager.loadMaterial( "Textures/Rock/Rock2Rpt.xml"  );		// Not the best tiling

        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/Rock/Rock.j3m"  );  // Does NOT repeat
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/Rocky/Rocky.j3m"  );	 // Does NOT repeat
        
    	Node aNode = new Node( "CSGSamples" );
        aGeometry = new Geometry( "Box1", aBox = new Box( 1, 1, 1 ) );
        aGeometry.setMaterial( mat_csg );
        aGeometry.move( 0f, 0f, -6f );
        
        TangentBinormalGenerator.generate( aGeometry );
        aNode.attachChild( aGeometry );
        
        
        bGeometry = new Geometry( "Box2", bBox = new Box( 2, 2, 2 ) );
        bGeometry.setMaterial( mat_csg );
        bGeometry.move( 5f, 0f, -6f );
        
        TangentBinormalGenerator.generate( bGeometry );
        aNode.attachChild( bGeometry );

        cGeometry = new Geometry( "Box3", cBox = new Box( 2, 2, 2 ) );
        cBox.scaleTextureCoordinates( new Vector2f( 2.0f, 2.0f ) );
        cGeometry.setMaterial( mat_csg );
        cGeometry.move( -5f, 0f, -6f );
        
        TangentBinormalGenerator.generate( cGeometry );
        aNode.attachChild( cGeometry );
    
        Light aLight = new AmbientLight();
        aLight.setColor( ColorRGBA.Gray );
        aNode.addLight( aLight );
        
        PointLight bLight = new PointLight();
        bLight.setColor( ColorRGBA.Yellow );
        bLight.setPosition( new Vector3f( 0f, 20f, 20f) );
        aNode.addLight( bLight );

        
    	// Display what we have
    	rootNode.attachChild( aNode );
    }

}