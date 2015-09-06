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


import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Checks UNION / DIFFERENCE / INTERSECTION / SKIP for simple Cube/Sphere
 */
public class CSGTestA 
	extends SimpleApplication 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestA();		    
	    app.start();
	}

    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
    	CSGGeometry aGeometry;
    	
    	// Start with the cube, add the sphere
    	aGeometry = buildShape( CSGGeometry.CSGOperator.SKIP
    							,	CSGGeometry.CSGOperator.UNION
    							,	CSGGeometry.CSGOperator.UNION );
    	aGeometry.move( -3f, 0f, 0f );
    	rootNode.attachChild( aGeometry );
    	
    	// Start with the cube, substract the sphere
    	aGeometry = buildShape(  CSGGeometry.CSGOperator.SKIP
								,	CSGGeometry.CSGOperator.UNION
								,	CSGGeometry.CSGOperator.DIFFERENCE );
    	aGeometry.move( 0f, 0f, 0f );
    	rootNode.attachChild( aGeometry );
    	
    	// Start with the cube, intersect the sphere
    	aGeometry = buildShape(  CSGGeometry.CSGOperator.SKIP
								,	CSGGeometry.CSGOperator.UNION
								,	CSGGeometry.CSGOperator.INTERSECTION );
    	aGeometry.move( 3f, 0f, 0f );
    	rootNode.attachChild( aGeometry );
    	
    	// Start with the sphere, add the cube
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION
						,	CSGGeometry.CSGOperator.UNION
						,	CSGGeometry.CSGOperator.SKIP );
		aGeometry.move( -3f, 0f, -5f );
		rootNode.attachChild( aGeometry );
		
		// Start with the sphere, remove the cube
		aGeometry = buildShape(  CSGGeometry.CSGOperator.UNION
						,	CSGGeometry.CSGOperator.DIFFERENCE
						,	CSGGeometry.CSGOperator.SKIP );
		aGeometry.move( 0f, 0f, -5f );
		rootNode.attachChild( aGeometry );
		
		// Start with the sphere, intersect the cube
		aGeometry = buildShape(  CSGGeometry.CSGOperator.UNION
						,	CSGGeometry.CSGOperator.INTERSECTION
						,	CSGGeometry.CSGOperator.SKIP );
		aGeometry.move( 3f, 0f, -5f );
		rootNode.attachChild( aGeometry );

    	// Just the cube
    	aGeometry = buildShape( CSGGeometry.CSGOperator.SKIP
    							,	CSGGeometry.CSGOperator.UNION 
    							,	CSGGeometry.CSGOperator.SKIP );
    	aGeometry.move( -3f, 0f, 5f );
    	rootNode.attachChild( aGeometry );
    	
    	// Just the sphere
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION
    							,	CSGGeometry.CSGOperator.SKIP 
    							,	CSGGeometry.CSGOperator.SKIP );
    	aGeometry.move( 3f, 0f, 5f );
    	rootNode.attachChild( aGeometry );
    	
    	// Start with the sphere, subtract the cube, intersect with the sphere
    	aGeometry = buildShape( CSGGeometry.CSGOperator.UNION
    							,	CSGGeometry.CSGOperator.DIFFERENCE 
    							,	CSGGeometry.CSGOperator.INTERSECTION );
    	aGeometry.move( 0f, 0f, -10f );
    	rootNode.attachChild( aGeometry );
    }

    protected CSGGeometry buildShape(
        CSGGeometry.CSGOperator		pOperator1
    ,	CSGGeometry.CSGOperator		pOperator2
    ,	CSGGeometry.CSGOperator		pOperator3
    ) {
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );

    	CSGGeometry aGeometry = new CSGGeometry();
    	aGeometry.setMaterial( mat_csg );

    	CSGShape aSphere = new CSGShape( "Sphere1", new Sphere( 32, 32, 1.3f ) );
    	aGeometry.addShape( aSphere, pOperator1 );

    	CSGShape aCube = new CSGShape( "Box", new Box( 1,1,1 ) );
    	aGeometry.addShape( aCube, pOperator2 );

    	aSphere = new CSGShape( "Sphere2", new Sphere( 32, 32, 1.3f ) );
    	aGeometry.addShape( aSphere, pOperator3 );
    	
    	aGeometry.regenerate();
    	return( aGeometry );
    }
    
}