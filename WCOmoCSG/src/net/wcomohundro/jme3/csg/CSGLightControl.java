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

import java.util.ArrayList;
import java.util.List;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;

import com.jme3.light.AmbientLight;
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
	/** Service routine that understands adjusting a Light based on a transform and a starting position */
	public static Light applyLightTransform(
		Light			pLight
	,	Light			pPosition
	,	Transform		pTransform
	) {
        if ( pLight instanceof PointLight ) {
        	// A PointLight has a position that we drag along with the change
        	PointLight aLight = (PointLight)pLight;
        	PointLight posLight = (PointLight)pPosition;
        	Vector3f newPosition = pTransform.transformVector( posLight.getPosition(), null );
            aLight.setPosition( newPosition );
            
        } else if ( pLight instanceof DirectionalLight ) {
        	// A DirectionalLight has a direction that must follow any rotation
        	DirectionalLight aLight = (DirectionalLight)pLight;
        	DirectionalLight posLight = (DirectionalLight)pPosition;
            aLight.setDirection( pTransform.getRotation().mult( posLight.getDirection() ) );
            
        } else if ( pLight instanceof SpotLight ) {
        	// A SpotLight has a position and direction that must be kept up to date
        	SpotLight aLight = (SpotLight)pLight;
        	SpotLight posLight = (SpotLight)pPosition;
        	Vector3f newPosition = pTransform.transformVector( posLight.getPosition(), null );
            aLight.setPosition( newPosition );
            aLight.setDirection( pTransform.getRotation().mult( posLight.getDirection() ) );
        }
        return( pLight );
	}
	
	/** Factory level service routine that understands applying a light control to a 
	 	set of lights.
	 	
	 	The lights are tracked via a list of 'controls' that apply to each light.
	 	NOTE
	 		that even though AmbientLights are not affected by this control, we still
	 		include a control in the list.  This allows the caller to manage a list of
	 		active lights, knowing the AmbientLight will clean up after itself in the
	 		update cycle.
	 */
	public static List<Control> configureLightControls(
		List<Control>	pControlList
	,	Control			pControlTemplate
	,	LightList		pLights
	,	boolean			pCloneLights
	,	Transform		pLocalTransform
	) {
		// Ensure we have a place to stash the controls
		if ( pControlList == null ) {
			pControlList = new ArrayList( pLights.size() );
		}
		// Produce a control for every light in the list
		for( Light aLight : pLights ) {
			AbstractControl aControl = null;
			
			if ( pCloneLights ) {
				// Clone the light, which covers us for shared shapes
				aLight = aLight.clone();
			}
			if ( pControlTemplate == null ) {
				// Use a standard 
				aControl = new CSGLightControl( aLight, true );
				((CSGLightControl)aControl).setLocalTransform( pLocalTransform );
				
			} else if ( pControlTemplate instanceof CSGLightControl ) {
				// Use the CSGLightControl supplied
				aControl = ((CSGLightControl)pControlTemplate).clone( aLight );
				((CSGLightControl)aControl).setLocalTransform( pLocalTransform );
				
			} else if ( pControlTemplate instanceof LightControl ) {
				// Use the LightControll supplied
				aControl = (LightControl)pControlTemplate.cloneForSpatial( null );
				((LightControl)aControl).setLight( aLight );
			}
			if ( aControl != null ) {
				pControlList.add( aControl );
			}
		}
		return( (pControlList.isEmpty() ) ? null : pControlList );
	}
	
	/** Resolve the light behind a given light control */
	public static Light resolveLight(
		Control		pLightControl
	) {
		Light aLight;
		
		if ( pLightControl instanceof CSGLightControl ) {
			aLight = ((CSGLightControl)pLightControl).getLight();
			
		} else if ( pLightControl instanceof LightControl ) {
			aLight = ((LightControl)pLightControl).getLight();
			
		} else {
			aLight = null;
		}
		return( aLight );
	}
	
	/** The Light that is local to the Spatial */
    protected Light		mLight;
    /** Local transform to apply */
    protected Transform	mLocalTransform;
    /** The original position of the light */
    protected Light		mOriginalPosition;
    /** What was the last transform we applied */
    protected Transform	mLastWorldTransform;
    /** Control flag to adjust the light only on the initial positioning */
    protected boolean	mInitialPositionOnly;
    

    /** Null constructor where the light must be defined later */
    public CSGLightControl(
    ) {
    	this( null, true );
    }
    /** Standard constructor that knows its light */
    public CSGLightControl(
    	Light	pLight
    ,	boolean	pInitialPositionOnly
    ) {
        this.mLight = pLight;
        this.mInitialPositionOnly = pInitialPositionOnly;
    }
    
    /** Clone this control for a given light */
    public CSGLightControl clone(
    	Light		pLight
    ) {
    	CSGLightControl aCopy = (CSGLightControl)super.cloneForSpatial( null );
    	aCopy.mLight = pLight;
    	return( aCopy );
    }
    @Override
    public Control cloneForSpatial(
    	Spatial		pSpatial
    ) {
    	CSGLightControl aCopy = (CSGLightControl)super.cloneForSpatial( pSpatial );
    	return( aCopy );
    }
    
    /** Accessor to the light */
    public Light getLight() { return mLight; }
    public void setLight( Light pLight ) { mLight = pLight; }
    
    
    /** Accessor to the InitialPositionOnly control flag */
    public boolean isInitialPostitionOnly() { return mInitialPositionOnly; }
    public void setInitialPositionOnly( boolean pFlag ) { mInitialPositionOnly = pFlag; }
    
    
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
			if ( this.mLight instanceof AmbientLight ) {
				// NOTE that Ambient lights are not affected by the control
				this.setEnabled( false );
				this.spatial.removeControl( this );
				
			} else {
	        	// A Transform affects every kind of light, which we must define in World coordinates
	        	Transform aTransform = this.spatial.getWorldTransform();
	        	
	        	if ( mInitialPositionOnly ) {
	        		// Avoid the cloning overhead where possible
		        	if ( mLocalTransform != null ) {
		        		// The local transform must be blended with the world
		        		// (and the localTransform may be shared, do not corrupt it)
		        		aTransform = mLocalTransform.clone().combineWithParent( aTransform );
		        	}
		        	// Move the light from its original position
		         	applyLightTransform( mLight, mLight, aTransform );
		         	
		         	// We will not be coming back
		        	this.setEnabled( false );
		        	this.spatial.removeControl( this );
	       		
	        	} else {
	        		// Remember where we started/are to account for another reposition     	
		        	if ( (mLastWorldTransform != null) && mLastWorldTransform.equals( aTransform ) ) {
		        		// Nothing moved
		        		return;
		        	} else {
		        		// Remember where we are now
		        		mLastWorldTransform = aTransform.clone();
		        	}
		        	if ( mLocalTransform != null ) {
		        		// The initial local transform must be blended with the world
		        		aTransform = mLocalTransform.clone().combineWithParent( aTransform );
		        	}
		        	if ( mOriginalPosition == null ) {
		        		// Remember where we started
		        		mOriginalPosition = mLight.clone();
		        	}
		        	// Move the light based on its original position
		         	applyLightTransform( mLight, mOriginalPosition, aTransform );
		        }
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
