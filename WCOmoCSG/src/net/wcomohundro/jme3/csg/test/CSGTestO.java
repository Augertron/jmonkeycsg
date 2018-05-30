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


import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGGeonode;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Test of single versus multiple materials */
public class CSGTestO 
	extends CSGTestSceneBase
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestO();		    
	    app.start();
	}

	/** Simple unique name **/
	protected int	mShapeCounter;
	
	public CSGTestO(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		//super( new FlyCamAppState() );
	}

    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
		
		this.mPostText.push( "QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
	    
		CSGGeonode aGeometry;
    	
	    // Basic material for the CSG
        Material matCobbleStone = assetManager.loadMaterial( "Textures/CobbleStone/CobbleStone.xml" );
        Material matPebbles = assetManager.loadMaterial( "Textures/CobbleStone/Pebbles.xml" );

    	aGeometry = new CSGGeonode( "SingleMaterial" );
    	aGeometry.setMaterial( matCobbleStone );
    	
    	CSGShape aCube = new CSGShape( "Box-" + mShapeCounter++, new CSGBox( 1,1,1 ) );
    	aGeometry.addShape( aCube );
    	
    	CSGBox bBox = new CSGBox( 1f, 1f, 1f );
    	CSGShape bCube = new CSGShape( "Box-" + mShapeCounter++, bBox );
    	bCube.move( 0.25f, 0.25f, 0.25f );
    	aGeometry.addShape( bCube );
    	aGeometry.regenerate();
    	
		//rootNode.attachChild( aGeometry );
		
    	aGeometry = new CSGGeonode( "MultiMaterial" );
    	
    	aCube = new CSGShape( "Box-" + mShapeCounter++, new CSGBox( 1,1,1 ) );
    	aCube.setMaterial( matCobbleStone );
    	aGeometry.addShape( aCube );
    	
    	bBox = new CSGBox( 1f, 1f, 1f );
    	bCube = new CSGShape( "Box-" + mShapeCounter++, bBox );
    	bCube.move( 0.25f, 0.25f, 0.25f );
    	bCube.setMaterial( matPebbles );
    	aGeometry.addShape( bCube );
    	aGeometry.regenerate();
    	
    	aGeometry.move( 4, 0, 0 );
		rootNode.attachChild( aGeometry );

		rootNode.addLight( new AmbientLight( ColorRGBA.LightGray ) );
    }
}