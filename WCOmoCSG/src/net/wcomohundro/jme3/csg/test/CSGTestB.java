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
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import net.wcomohundro.jme3.csg.CSGGeonode;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.CSGBox;
import net.wcomohundro.jme3.csg.shape.CSGCylinder;

/** Simple test of the CSG suport 
 		Blend together cubes and cylinders, with result having multiple textures
 */
public class CSGTestB 
	extends CSGTestSceneBase 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestB( false );		    
	    app.start();
	}
	
	/** Simple unique name **/
	protected int		mShapeCounter;
	/** Multi-Material test only */
	protected boolean	mMultiMatOnly;

	public CSGTestB(
		boolean		pMultiMatOnly
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		
		mMultiMatOnly = pMultiMatOnly;
	}

    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
		
		this.mPostText.push( "QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
	    
	    // Long cylinder that pokes out of the cube
    	Spatial aGeometry;

    	if ( mMultiMatOnly ) {
    		// Colored cylinder forces multiple materials
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.INTERSECTION, true );
	    	aGeometry.move( 3f, 0f, -5f );
	    	rootNode.attachChild( aGeometry );    		
    	} else {
    		// All the various combinations
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.UNION, false );
	    	aGeometry.move( -3f, 0, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
	    	aGeometry.move( 0f, 0f, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
	    	aGeometry.move( 3f, 0f, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	// Short cylinder that does not span out of the cube
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.UNION, false );
	    	aGeometry.move( -3f, 3f, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
	    	aGeometry.move( 0f, 3f, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
	    	aGeometry.move( 3f, 3f, 0f );
	    	rootNode.attachChild( aGeometry );
	    	
		    // Long cylinder that pokes out of the cube
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.UNION, false );
	    	aGeometry.move( -3f, 0, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
	    	aGeometry.move( 0f, 0f, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.INTERSECTION, true );
	    	aGeometry.move( 3f, 0f, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	// Short cylinder that does not span out of the cube
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.UNION, false );
	    	aGeometry.move( -3f, 3f, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
	    	aGeometry.move( 0f, 3f, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
	    	aGeometry.move( 3f, 3f, -5f );
	    	rootNode.attachChild( aGeometry );
	    	
	    	// Just the box
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.SKIP, false );
	    	aGeometry.move( -3f, 0f, 5f );
	    	rootNode.attachChild( aGeometry );
	
	    	// Just the cylinder
	    	aGeometry = buildShape( CSGGeonode.CSGOperator.SKIP, 2.5f, CSGGeonode.CSGOperator.UNION, true );
	    	aGeometry.move( 3f, 0f, 5f );
	    	rootNode.attachChild( aGeometry );
    	}
    }

    protected Spatial buildShape(
    	CSGGeonode.CSGOperator		pOperator1
    ,	float						pLength
    ,	CSGGeonode.CSGOperator		pOperator2
    ,	boolean						pColored
    ) {
    	mShapeCounter += 1;
    	
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );

    	CSGGeonode aGeometry = new CSGGeonode( "Blend-" + mShapeCounter );
    	aGeometry.setMaterial( mat_csg );

    	CSGShape aCube 
    		= new CSGShape( "Box-" + mShapeCounter, new CSGBox(1,1,1) );
    	aGeometry.addShape( aCube, pOperator1 );

    	CSGShape aCylinder 
    		= new CSGShape( "Cylinder-" + mShapeCounter, new CSGCylinder( 32, 32, 1.1f, pLength/2f ) );
    	if ( pColored ) {
            Material mat1 = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
            mat1.setColor( "Color", ColorRGBA.Yellow );
            aCylinder.setMaterial( mat1 );
    	}
    	aGeometry.addShape( aCylinder, pOperator2 );
    	
    	aGeometry.regenerate();
    	return( aGeometry );
    }
    
}