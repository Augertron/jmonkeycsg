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
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.placeholder.CSGPlaceholderSpatial;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

/** Simple extension of Node that extends Savable.read() to accept a list of external references 
 	and provides for a custom environment.
 	
 	There is no simple programmatic way to access the CSGEnvironment defined by this node.
 	However, in an .xml definition, you can set the environment here and assign it an id
 		<csgEnvironment class='net.wcomohundro.jme3.csg.CSGEnvironment' id='CommonCSGEnvironment' ..../>
 	
 	and then reference it in a subsequent definition via 
 		<csgEnvironment class='net.wcomohundro.jme3.csg.CSGEnvironment' ref='CommonCSGEnvironment'/>
 		
 	Likewise, you can define/reference a common material.
 	
 	The 'library' items can also be used to define reference names
 		
 	This node also can act as a collection of independent CSGSpatials, triggering a single
 	regeneration of all CSG elements within itself.
 */
public class CSGLinkNode 
	extends CSGNode
	implements Savable, ConstructiveSolidGeometry, ConstructiveSolidGeometry.CSGElement
{
	/** Version tracking support */
	public static final String sCSGLinkNodeRevision="$Rev$";
	public static final String sCSGLinkNodeDate="$Date$";
	
	
	/** Null constructor */
	public CSGLinkNode(
	) {
	}
	
    /** If physics is active for any subelement, connect it all up now */
    @Override
    public void applyPhysics(
    	PhysicsSpace		pPhysicsSpace
    ,	Node				pRoot
    ) {
    	// A LinkNode implies no special structure/grouping in terms of Physics.
    	// Let each individual element decide how to structure its own Physics.
    	for( Spatial aSpatial : children ) {
    		if ( aSpatial instanceof CSGElement ) {
    			// Let the subshape decide how to apply the physics
    			((CSGElement)aSpatial).applyPhysics( pPhysicsSpace, pRoot );
    		} 
    	}
    }
    
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
    	AssetManager aManager = pImporter.getAssetManager();
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// Let the super do its thing
		super.read( pImporter );
		
		// Individual CSGSpatials may regenerate as part of read(), so now scan the list
		// looking for any oddness.
		mInError = null;
		List<Spatial> aChildList =  this.getChildren();
		for( int i = 0, j = aChildList.size(); i < j; i += 1 ) {
			Spatial aSpatial = aChildList.get( i );
			if ( aSpatial instanceof CSGElement ) {
				// Check on the construction of this element
				CSGElement csgSpatial = (CSGElement)aSpatial;
				mRegenNS += csgSpatial.getShapeRegenerationNS();
				if ( !csgSpatial.isValid() ) {
					mInError = CSGConstructionException.registerError( mInError, csgSpatial.getError() );
				}
			} else if ( aSpatial instanceof CSGPlaceholderSpatial ) {
				// Resolve the placeholder to its real element
				Spatial realSpatial = ((CSGPlaceholderSpatial)aSpatial).resolveItem( this, false );
				this.detachChildAt( i );
				this.attachChildAt( realSpatial, i );
				
				// And check it again
				i -= 1;
			}
		}		
	    // Rebuild the shapes just loaded, as needed
	    boolean doLater = aCapsule.readBoolean( "deferRegeneration", false );
	    if ( doLater ) {
	    	// No regeneration at this time
	    	this.mRegenNS = 0;
	    } else {
	    	// Do the regeneration, capturing any errors
	        try {
	        	regenerate( true, CSGEnvironment.resolveEnvironment( mEnvironment, null) );
	        } catch( CSGConstructionException ex ) {
	        	// This error should already be registered with this element
	        }
			if ( !this.isValid() ) {
				CSGEnvironment.sLogger.log( Level.WARNING, "Geometry invalid: " + this );
			}
	    }
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGLinkNodeRevision
													, sCSGLinkNodeDate
													, pBuffer ) );
	}

}
