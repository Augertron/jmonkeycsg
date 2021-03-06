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

import java.util.ArrayList;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;

/**	Check for support of a complex mesh, as provided by an exterior object
 	This test is based on the SourceForge ticket where the intersection of two
 	meshes is desired.
*/
public class CSGTestH 
	extends CSGTestSceneBase 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestH();		    
	    app.start();
	}

	public CSGTestH(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}
    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
		
		this.mPostText.push( "QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
		
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );

        // The geometry wrapper
    	CSGGeometry aGeometry = new CSGGeometry();
    	aGeometry.setMaterial( mat_csg );

/****
    	// Start with a sphere
    	CSGShape aSphere = new CSGShape( "Sphere1", new Sphere( 32, 32, 1.3f ) );
    	aGeometry.addShape( aSphere );

    	// Subtract out a cube
    	CSGShape aCube = new CSGShape( "Box", new Box(1,1,1) );
    	aGeometry.substractShape( aCube );
 	
******/
    	// Load the primary shape
    	Spatial aSpatial = assetManager.loadModel("Meshes/outer.obj");
    	aSpatial.setMaterial( mat_csg );
    	aSpatial.move( -5,  0, 0 );
    	rootNode.attachChild( aSpatial );
    	
    	CSGShape aShape = new CSGShape( "OuterShape", ((Geometry)aSpatial).getMesh() );
    	aGeometry.addShape( aShape, CSGGeometry.CSGOperator.UNION );
	    
    	// Load the secondary shape
    	aSpatial = assetManager.loadModel("Meshes/view.obj");
    	aSpatial.setMaterial( mat_csg );
    	aSpatial.move( 5,  0, 0 );
    	rootNode.attachChild( aSpatial );

    	CSGShape bShape = new CSGShape( "ViewShape", ((Geometry)aSpatial).getMesh() );
    	aGeometry.addShape( bShape, CSGGeometry.CSGOperator.INTERSECTION );

    	// Build the shape
    	aGeometry.regenerate();

    	rootNode.attachChild( aGeometry );
    }

}