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

import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;
import com.jme3.scene.control.LightControl;
import com.jme3.util.TempVars;

/** I do not understand the rationale behind the standard JME3 LightControl.  If I add a localLight
 	to a Spatial, I expect to position and configure it in relation to that Spatial.  If that Spatial
 	is subsequently transformed, I expect the Light to be reconfigured to match.
 	
 	Lights are supposedly in world coordinates, but a CSGLightControl assumes it is used within
 	a Spatial with Lights positioned relative to that spatial.  Any transform applied to the Spatial
 	is then applied to the enclosed light.
 */
public class CSGLightControl 
	extends AbstractControl
	implements Cloneable
{
	/** Factory level service routine that understands applying a light control to a 
	 	set of lights
	 */
	public static void applyLightControl(
		Control			pLightControl
	,	LightList		pLightList
	,	Transform		pLocalTransform
	,	Spatial			pNode
	,	boolean			pAttachToNode
	) {
		// Walk the list of lights
		for( Light aLight : pLightList ) {
			if ( pAttachToNode ) {
				// Attach the light to the node
				aLight = aLight.clone();
				pNode.addLight( aLight );
			}
    		// Match the light to this node so that transforms apply
			if ( pLightControl != null ) {
				if ( pLightControl instanceof CSGLightControl ) {
					CSGLightControl aControl = ((CSGLightControl)pLightControl).clone( aLight );
					aControl.setLocalTransform( pLocalTransform );
					pNode.addControl( aControl );
					
				} else if ( pLightControl instanceof LightControl ) {
					LightControl aControl = (LightControl)pLightControl.cloneForSpatial( null );
					aControl.setLight( aLight );
					pNode.addControl( aControl );
				}
			}
		}
	}
	
	/** The Light that is local to the Spatial */
    protected Light		mLight;
    /** Local transfrom to apply */
    protected Transform	mLocalTransform;
    /** The original local position of the light */
    protected Vector3f	mOriginalPosition;
    /** The original local direction of the light */
    protected Vector3f	mOriginalDirection;

    /** Null constructor where the light must be defined later */
    public CSGLightControl(
    ) {
    }
    /** Standard constructor that knows its light */
    public CSGLightControl(
    	Light	pLight
    ) {
        this.mLight = pLight;
    }
    
    /** Clone this control for a given light */
    public CSGLightControl clone(
    	Light	pLight
    ) {
    	CSGLightControl aCopy = (CSGLightControl)this.cloneForSpatial( null );
    	aCopy.mLight = pLight;
    	return( aCopy );
    }
    
    /** Accessor to the transform */
    public Transform getLocalTransform() { return mLocalTransform; }
    public void setLocalTransform(
    	Transform		pLocalTransform
    ) {
    	mLocalTransform = pLocalTransform;
    	if ( Transform.IDENTITY.equals( mLocalTransform ) ) {
    		mLocalTransform = null;
    	}
    }
    
    /** Adjust the light to match the operation on the controlling Spatial */
    @Override
    protected void controlUpdate(
    	float tpf
    ) {
        if ( (this.spatial != null) && (this.mLight != null) ) {
        	// A Transform affects every kind of light, which we must define in World coordinates
        	Transform aTransform = spatial.getWorldTransform();
        	if ( mLocalTransform != null ) {
        		aTransform = mLocalTransform.clone().combineWithParent( aTransform );
        	}
	        if ( mLight instanceof PointLight ) {
	        	// A PointLight has a position that we drag along with the change
	        	PointLight aLight = (PointLight)mLight;
	        	if ( mOriginalPosition == null ) {
	        		mOriginalPosition = aLight.getPosition().clone();
	        	}
	        	Vector3f newPosition = aTransform.transformVector( mOriginalPosition, null );
	            aLight.setPosition( newPosition );
	            
	        } else if ( mLight instanceof DirectionalLight ) {
	        	// A DirectionalLight has a direction that must follow any rotation
	        	DirectionalLight aLight = (DirectionalLight)mLight;
	        	if ( mOriginalDirection == null ) {
	        		mOriginalDirection = aLight.getDirection().clone();
	        	}
	            aLight.setDirection( aTransform.getRotation().mult( mOriginalDirection ) );
	            
	        } else if ( mLight instanceof SpotLight ) {
	        	// A SpotLight has a position and direction that must be kept up to date
	        	SpotLight aLight = (SpotLight)mLight;
	        	if ( mOriginalPosition == null ) {
	        		mOriginalPosition = aLight.getPosition().clone();
	        	}
	        	Vector3f newPosition = aTransform.transformVector( mOriginalPosition, null );
	            aLight.setPosition( newPosition );
	            
	        	if ( mOriginalDirection == null ) {
	        		mOriginalDirection = aLight.getDirection().clone();
	        	}
	            aLight.setDirection( aTransform.getRotation().mult( mOriginalDirection ) );
	        }
        }
    }

    @Override
    protected void controlRender(
    	RenderManager	pRenderManager
    , 	ViewPort 		pViewPort
    ) {
        // nothing to do
    }

}
