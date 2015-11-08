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
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;

import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Checks UNION / DIFFERENCE / INTERSECTION / SKIP for simple Cube/Sphere
 */
public class CSGTestJ 
	extends SimpleApplication 
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestJ();		    
	    app.start();
	}

	/** Simple unique name **/
	protected int		mUpdateCounter;
	protected CSGShape CSG_obj1, CSG_obj2;
	protected CSGGeometry CSG_obj;

    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
	    flyCam.setMoveSpeed( 20 );			// Move a bit faster
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
	       CSG_obj1 = attachShape(new Box(1, 1, 1), new Vector3f(0, 0, 0), ColorRGBA.Blue, "Box1");
	        CSG_obj2 = attachShape(new Box(.5f, 2, .5f), new Vector3f(0, 0, 0), ColorRGBA.Yellow, "Box2");
	        CSG_obj = new CSGGeometry("Geom");
	        CSG_obj.setMaterial(new Material(assetManager, "Common/MatDefs/Misc/ShowNormals.j3md"));
	        rootNode.attachChild(CSG_obj);
	        CSG_obj.addShape(CSG_obj1);
	        CSG_obj.addShape(CSG_obj2, CSGGeometry.CSGOperator.INTERSECTION);
	        CSG_obj.regenerate();
    }

    private CSGShape attachShape( Mesh shape, Vector3f pos, ColorRGBA color, String name) {
        CSGShape g = new CSGShape(name, shape);
        g.setLocalTranslation(pos);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setWireframe(true);
        mat.setColor("Color", color);
        g.setMaterial(mat);
        rootNode.attachChild(g);
        return g;
    }

    public void simpleUpdate(float tpf) {
    	mUpdateCounter += 1;
        if (mUpdateCounter == 10) {
            CSG_obj1.rotate(tpf, tpf, tpf);
            CSG_obj2.rotate(-tpf, -tpf,  -tpf);

            CSG_obj.regenerate();
            mUpdateCounter = 0;
        }
    }

}