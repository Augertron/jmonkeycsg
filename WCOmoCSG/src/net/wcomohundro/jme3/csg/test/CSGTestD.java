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
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;

/** Test of import/export
 */
public class CSGTestD 
	extends SimpleApplication 
{

	public CSGTestD(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}
    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
    	Geometry aGeometry;
    	Material mat_csg;
    	Node aNode = new Node( "CSGSamples" );

        mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
        aGeometry = new Geometry( "Box2", new Box( 1, 1, 1 ) );
        //aGeometry = new Geometry( "Cylinder", new Cylinder( 32, 32, 1.1f, 1.5f, true ) );
        aGeometry.setMaterial( mat_csg );
        aGeometry.move( -2f, 0f, 0f );
        aNode.attachChild( aGeometry );

        mat_csg = assetManager.loadMaterial( "Textures/Debug/Wireframe.j3m" );
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/BrickWall/BrickWall.j3m"  );
        aGeometry = new Geometry( "Box1", new Box( 1, 1, 1 ) );
        //aGeometry = new Geometry( "Cylinder", new Cylinder( 32, 32, 1.1f, 1.5f, true ) );
        aGeometry.setMaterial( mat_csg );
        aGeometry.move( 2f, 0f, 0f );
        aNode.attachChild( aGeometry );
        
        //mat_csg = assetManager.loadMaterial( "Textures/Terrain/Rock/Rock.j3m"  );
        Light aLight = new AmbientLight();
        aLight.setColor( ColorRGBA.White );
        aNode.addLight( aLight );

/***
	    // Long cylinder that pokes out of the cube
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 2.5f, CSGGeometry.CSGOperator.UNION, false );
    	aGeometry.move( 0f, 3, 0f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 2.5f, CSGGeometry.CSGOperator.DIFFERENCE, false );
    	aGeometry.move( 3f, 0f, 0f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 2.5f, CSGGeometry.CSGOperator.INTERSECTION, false );
    	aGeometry.move( 0f, 0f, 3f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 2.5f, CSGGeometry.CSGOperator.SKIP, false );
    	aGeometry.move( -3f, 0f, 0f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.SKIP, 2.5f, CSGGeometry.CSGOperator.UNION, false );
    	aGeometry.move( 0f, -3f, 0f );
    	aNode.attachChild( aGeometry );
    	
    	// Short cylinder that does not span out of the cube
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 1.5f, CSGGeometry.CSGOperator.UNION, true );
    	aGeometry.move( 0f, 3f, -8f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 1.5f, CSGGeometry.CSGOperator.DIFFERENCE, true );
    	aGeometry.move( 3f, 0f, -8f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION, 1.5f, CSGGeometry.CSGOperator.INTERSECTION, false );
    	aGeometry.move( 0f, 0f, -8f );
    	aNode.attachChild( aGeometry );
    	
    	aGeometry = buildSimpleShape( 0, false );
    	aGeometry.move( -3f, -3f, 0f );
    	aNode.attachChild( aGeometry );
****/
    	// Preserve the samples
    	File aFile = new File( "CSGTestD.xml" );
    	JmeExporter anExporter = new XMLExporter();
    	try {
    		anExporter.save( aNode, aFile );
    		System.out.println( "Export to: " + aFile.getCanonicalPath() );
    	} catch( Exception ex ) {
    		System.out.println( "***Export Failed: " + ex );
    	}
    	// Display what we have
    	rootNode.attachChild( aNode );
    }

    protected Geometry buildShape(
        CSGGeometry.CSGOperator		pOperator1
    ,	float						pLength
    ,	CSGGeometry.CSGOperator		pOperator2
    ,	boolean						pRotate
    ) {
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
    	//mat_csg.getAdditionalRenderState().setFaceCullMode( FaceCullMode.Off );

    	CSGGeometry aGeometry = new CSGGeometry();
    	aGeometry.setMaterial( mat_csg );

    	CSGShape aCube = new CSGShape( "Box", new Box(1,1,1) );
    	if ( pRotate ) aCube.rotate( 0.4f, 0.4f, 0f );
    	aGeometry.addShape( aCube, pOperator1 );

    	CSGShape aCylinder = new CSGShape( "Cylinder", new Cylinder( 32, 32, 1.1f, pLength, true ) );
    	aGeometry.addShape( aCylinder, pOperator2 );
    	
    	aGeometry.regenerate();
    	return( aGeometry );
    }
    
    protected Geometry buildSimpleShape(
    	float		pLength
    ,	boolean		pRotate
    ) {
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
    	if ( pLength > 0 ) {
        	Geometry aCylinder = new Geometry( "Cylinder", new Cylinder( 32, 32, 1.1f, pLength, true ) );
        	aCylinder.setMaterial( mat_csg );
	    	if ( pRotate ) aCylinder.rotate( 0.4f, 0.4f, 0f );
	    	return( aCylinder );
    		
    	} else {
    		// Just a cube
	    	Geometry aCube = new Geometry( "Box", new Box(1,1,1) );
	    	aCube.setMaterial( mat_csg );
	
	    	if ( pRotate ) aCube.rotate( 0.4f, 0.4f, 0f );
	    	return( aCube );
    	}

    }
}