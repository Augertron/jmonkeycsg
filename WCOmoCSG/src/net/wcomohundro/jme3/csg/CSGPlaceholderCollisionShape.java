/** Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2015, WCOmohundro
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
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.HeightfieldCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.UserData;
import com.jme3.terrain.geomipmap.TerrainPatch;
import com.jme3.terrain.geomipmap.TerrainQuad;

/** Define a collision shape that just get us through the .read() process, expecting to
 	be dynamically replaced.
 	
 	We seem to require a valid 'objectID', so extend BOX and just build a generic.
 	Other processing can then check for an instanceof CGPlaceholderCollisionShape
 	and know that it must be replaced.
 	
 	This class is also used for factory services dealing with Physics and 
 	CollisionShapes.  Unfortunately, many of the interesting support methods in
 	the jme3 CollisionShapeFactory are private and unavailable.  Therefore, we
 	are forced to duplicate some of the functionality here.
 	
 */
public class CSGPlaceholderCollisionShape 
	extends BoxCollisionShape
{
	/** Service routine to apply Physics to a component 
	 	The pPhysics object is expected to be dedicated to the given Spatial and
	 	can be used as-is.
	 	The pDefaultPhysics object is expected to be utilized by multiple Spatials
	 	and must be cloned before use.
	 	
	 	If the CollisionObject behind the Physics object is an instance of this
	 	placeholder class, then we assume it was used during .read() processing and
	 	must be replaced by a proper CollisionShape.
	 */
    public static CollisionShape applyPhysics(
    	PhysicsSpace		pPhysicsSpace
    ,	PhysicsControl		pPhysics
    ,	Spatial				pSpatial
    ,	Node				pRoot
    ) {
    	CollisionShape aCollisionShape = null;
    	if ( pPhysics != null ) {
    		// Refresh the CollisionShape as needed
    		if ( pPhysics instanceof PhysicsCollisionObject ) {
    			PhysicsCollisionObject aCollision = (PhysicsCollisionObject)pPhysics;
    			aCollisionShape = aCollision.getCollisionShape();
	    		if ( (aCollisionShape == null) || (aCollisionShape instanceof CSGPlaceholderCollisionShape) ) {
	    			// Reset the shape to match this items mesh
	    			aCollisionShape = createMeshShape( pSpatial, pRoot );
	    			aCollision.setCollisionShape( aCollisionShape );
	    		}
    		}
    		pSpatial.addControl( (PhysicsControl)pPhysics );
    		pPhysicsSpace.add( pPhysics );
    	}
    	return( aCollisionShape );
    }
    
    /** Service routine to construct an appropriate collision shape */
    public static CollisionShape createMeshShape(
    	Spatial 	pSpatial
    ,	Node		pRoot
    ) {
        if ( pSpatial instanceof Geometry ) {
        	// Simple geometry includes nothing of the parent transform except for scale
            return createSingleMeshShape( pRoot, (Geometry)pSpatial, new Transform() );
            
        } else if ( pSpatial instanceof Node ) {
        	// Create a compound shape from all the children
            return createCompoundShape( 	pRoot
            							, 	(Node)pSpatial
            							, 	new CompoundCollisionShape()
            							,	new Transform() );
            
        } else {
        	// No appropriate shape
            return( null );
        }
    }

    /** Service routine that builds up a Compound collision shape based on the children
     	of a given Node
     */
    protected static CompoundCollisionShape createCompoundShape(
    	Node					pRoot
    ,	Node 					pSourceNode
    , 	CompoundCollisionShape	pShape
    ,	Transform				pTempTransform
    ) {
    	if ( pRoot == null ) pRoot = pSourceNode;
    	
        for( Spatial aSpatial : pSourceNode.getChildren() ) {
        	if ( (aSpatial instanceof CSGElement) && ((CSGElement)aSpatial).hasPhysics() ) {
        		// The spatial has its own custom physics so it is NOT part of the whole
        		continue;
        		
        	} else if ( aSpatial instanceof Node ) {
            	// Keep blending into what we have
            	Node aNode = (Node)aSpatial;
                createCompoundShape( pRoot, aNode, pShape, pTempTransform );

            } else if ( aSpatial instanceof Geometry ) {
                Boolean ignoreFlag = aSpatial.getUserData( UserData.JME_PHYSICSIGNORE );
                if ( (ignoreFlag != null) && ignoreFlag.booleanValue() ) {
                	// This Geometry does not want to play
                    continue;
                }
                // Get the appropriate shape for this Geometry
                Geometry aGeometry = (Geometry)aSpatial;
                CollisionShape childShape = createSingleMeshShape( pRoot, aGeometry, pTempTransform );
                if ( childShape != null ) {
                    pShape.addChildShape( childShape
                                		,	pTempTransform.getTranslation()
                                		,	pTempTransform.getRotation().toRotationMatrix() );
                }
            }
        }
        return( pShape );
    }
    /** Service routine the builds a single mesh-based collision shape 
     	returns NULL if a shape cannot be built
     	
     	pTempTransform is set with the appropriate parent level transform as a side effect
     */
    protected static CollisionShape createSingleMeshShape(
    	Node			pRoot
    ,	Geometry		pGeometry
    ,	Transform		pTempTransform
    ) {
    	Spatial useRoot = (pRoot == null) ? pGeometry : pRoot;
    	
    	// A MeshCollsionShape is what is desired
        Mesh aMesh = pGeometry.getMesh();
        if ( (aMesh != null) && (aMesh.getMode() == Mesh.Mode.Triangles) ) {
            CollisionShape childShape = new MeshCollisionShape( aMesh );
            
            Transform aTransform = getTransform( pGeometry, useRoot, pTempTransform );
            childShape.setScale( aTransform.getScale() );
            return( childShape );
        } else {
        	// No mesh to build from
        	return( null );
        }
    }

    /** Calculate the appropriate transform for a CollisionShape in relation to the 
	 	ancestor for which the CollisionShape is generated
	 */
	protected static Transform getTransform(
		Spatial		pSpatial
	, 	Spatial		pRealParent
	,	Transform	pShapeTransform
	) {
	    if ( pShapeTransform == null ) {
	    	// Allocate new
	    	pShapeTransform = new Transform();
	    } else {
	    	// Reset 
	    	pShapeTransform.setRotation( Quaternion.IDENTITY );
	    	pShapeTransform.setScale( 1 );
	    	pShapeTransform.setTranslation( 0, 0, 0 );
	    }
		// Blend transforms until we reach the desired parent level
	    Spatial parentNode = pSpatial.getParent();
	    if ( parentNode == null ) parentNode = pSpatial;
	    
	    Spatial currentSpatial = pSpatial;
	    while( parentNode != null ) {
	        if ( pRealParent == currentSpatial ) {
	            // Processing the real parent, so only apply scale
	            Transform scaleOnlyTransform = new Transform();
	            scaleOnlyTransform.setScale( pSpatial.getLocalScale() );
	            pShapeTransform.combineWithParent( scaleOnlyTransform );        	
	            break;
	        } else {
	        	// Blend in the current info
	            pShapeTransform.combineWithParent( currentSpatial.getLocalTransform() );
	            parentNode = currentSpatial.getParent();
	            currentSpatial = parentNode;
	        }
	    }
	    return( pShapeTransform );
	}


	/** Standard null constructor as used by .read() processing */
	public CSGPlaceholderCollisionShape(
	) {
		super( Vector3f.UNIT_XYZ );
	}
}
