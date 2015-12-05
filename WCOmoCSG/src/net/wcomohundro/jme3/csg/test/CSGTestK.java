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
import java.util.Arrays;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.shape.CSGBox;
import net.wcomohundro.jme3.csg.shape.CSGSkyDome;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector4f;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Surface;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.TextureCubeMap;
import com.jme3.util.SkyFactory;
import com.jme3.util.SkyFactory.EnvMapType;
import com.jme3.util.TangentBinormalGenerator;



/** Simple test of the CSG support 
 		Blend with a 2D surface rather than a 3D solid
 */
public class CSGTestK 
	extends SimpleApplication 
{
	protected static Vector4f[][] sControlPoints = {
		{ new Vector4f( -10, 0, -10, 1 ), new Vector4f( 0, 0, -10, 1 ), new Vector4f( 10, 0, -10, 1 ) }	
	,	{ new Vector4f( -10, 0,   0, 1 ), new Vector4f( 0, 0,   0, 1 ), new Vector4f( 10, 0,   0, 1 ) }	
	,	{ new Vector4f( -10, 0,  10, 1 ), new Vector4f( 0, 0,  10, 1 ), new Vector4f( 10, 0,  10, 1 ) }	
	};
	protected static float[][] sNurbKnots = {
		{ 1, 1, 1 }
	,	{ 1, 1, 1 }
	};
	
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestK();		    
	    app.start();
	}


    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
    	CSGGeometry aGeometry = new CSGGeometry( "AScene" );
        Material aMaterial = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
        aMaterial.getAdditionalRenderState().setWireframe( true );
        aGeometry.setMaterial( aMaterial.clone() );
	
    	List<List<Vector4f>> controlPoints = new ArrayList( sControlPoints.length );
    	for( Vector4f[] aRow : sControlPoints ) {
    		List rowList = new ArrayList( aRow.length );
    		rowList.addAll( Arrays.asList( aRow ) );
    		controlPoints.add( rowList );
    	}
    	int index = 0;
    	List<Float>[] nurbKnots = new List[2];
    	for( float[] aRow : sNurbKnots ) {
    		List<Float> rowList = new ArrayList( aRow.length );
    		for( float aValue : aRow ) {
    			rowList.add( new Float( aValue ) );
    		}
    		nurbKnots[ index++ ] = rowList;
    	}
    	
    	Surface aSurface
    		= Surface.createNurbsSurface( controlPoints
    										, nurbKnots
    										, 3
    										, 3
    										, 1
    										, 1 );
        //aGeometry.addShape( new CSGShape( "ASurface", aSurface ) );
        
    	TerrainQuad aTerrain = new TerrainQuad( "ATerrain", 65, 129, null );
    	aTerrain.setLocked( true );
    	//aTerrain.setMaterial( aMaterial.clone() );
    	//rootNode.attachChild( aTerrain );

    	aGeometry.addShape( new CSGShape( "ATerrain", aTerrain ) );

        CSGShape aBox = new CSGShape( "ABox", new CSGBox( 1, 1, 2 ) );
        aBox.setLocalTranslation( -2, -0.5f, -1 );
    	aGeometry.subtractShape( aBox );

    	aGeometry.regenerate();
    	rootNode.attachChild( aGeometry );
    	
    	CSGSkyDome aSky = new CSGSkyDome( "MySky" );
    	aSky.applyGradient( new ColorRGBA( 0.1f, 0.1f, 0.2f, 1.0f )
    						, new ColorRGBA( 0.2f, 0.2f, 0.9f, 1.0f ) );
    	
    	Material skyMaterial = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
    	skyMaterial.setBoolean( "VertexColor", true );

//    	Material skyMaterial = new Material( assetManager, "Common/MatDefs/Misc/Sky.j3md" );
//    	skyMaterial.setBoolean( "SphereMap", true );
    	aSky.setMaterial( skyMaterial );
    	
//			= SkyFactory.createSky( assetManager, aTexture, EnvMapType.CubeMap );
	
//			= SkyFactory.createSky( assetManager
//				              	, "Textures/Sky/Bright/FullskiesBlueClear03.dds"
//				              	, EnvMapType.CubeMap );
//			= SkyFactory.createSky( assetManager
//        						, "Textures/Sky/Bright/BrightSky.dds"
//        						, EnvMapType.CubeMap );
    	
    	rootNode.attachChild( aSky );
    }

    
}