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
	
	/** Which element is being reconstructed */
	protected CSGElement	mActiveElement;
	
	
	/** Null constructor */
	public CSGLinkNode(
	) {
	}
	
	/** Get status about just what regenerate is doing */
	@Override
	public synchronized StringBuilder reportStatus( 
		StringBuilder 	pBuffer
	, 	boolean 		pBriefly	
	) {
		if ( pBuffer == null ) pBuffer = new StringBuilder( 256 );
		
		// Where are we in the process?
		if ( mRegenNS < 0 ) {
			// Work in progress
			if ( mActiveElement != null ) {
				mActiveElement.reportStatus( pBuffer, pBriefly );
			}
		} else {
			// Report on what happened
			if ( pBriefly ) {
				// ????
			} else {
				pBuffer.append( ": " )
					   .append( mRegenNS / 1000000 )
					   .append( "ms" );
			}
		}
		return( pBuffer );
	}
	@Override
	public synchronized int getProgress(
	) {
		return( (mActiveElement == null) ? 0 : mActiveElement.getProgress() );
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
    
    /** Apply regeneration to all subelements */
	@Override
	public CSGShape regenerate(
		boolean				pOnlyIfNeeded
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		mRegenNS = -1;
		long totalNS = 0;
		pEnvironment = CSGEnvironment.resolveEnvironment( (pEnvironment == null) ? mEnvironment : pEnvironment, this );

		// Operate on all CSG elements defined within this node, returning the last
		// NOTE that we only detect first level children -- but you can always nest by
		//		using deeper levels of this class.
		CSGShape lastValidShape = null;
		mInError = null;
		
		List<Spatial> aChildList =  this.getChildren();
		for( int i = 0, j = aChildList.size(); i < j; i += 1 ) {
			Spatial aSpatial = aChildList.get( i );
			aSpatial = resolveSpatial( aSpatial, i, true );
			
			if ( aSpatial instanceof CSGElement ) try {
				// Trigger the regeneration
				mActiveElement = (CSGElement)aSpatial;
				CSGShape aShape = mActiveElement.regenerate( pOnlyIfNeeded, pEnvironment );
				
				totalNS += mActiveElement.getShapeRegenerationNS();
				if ( mActiveElement.isValid() ) {
					// Remember the last valid shape
					lastValidShape = aShape;
				} else {
					// If any child shape is invalid, then this shape is invalid
					setError( mActiveElement.getError() );
				}
			} catch( CSGConstructionException ex ) {
				// Record the error and continue on???
				setError( ex );
			}
		}
		mRegenNS = totalNS;
		return( lastValidShape );
	}
	
	/** Service routine to resolve any placeholder in the child list */
	protected Spatial resolveSpatial(
		Spatial		pSpatial
	,	int			pChildIndex
	,	boolean		pResolveFromTop
	) {
		if ( pSpatial instanceof CSGPlaceholderSpatial ) {
			// Resolve the placeholder to its real element (if possible)
			Spatial realSpatial = ((CSGPlaceholderSpatial)pSpatial).resolveItem( this, pResolveFromTop );
			if ( realSpatial != null ) {
				this.detachChildAt( pChildIndex );
				this.attachChildAt( realSpatial, pChildIndex );
				return( realSpatial );
			}	
		}
		return( pSpatial );
	}

    ///////////////////////////////////////// Savable /////////////////////////////////////////////
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
			aSpatial = resolveSpatial( aSpatial, i, false );
			
			if ( aSpatial instanceof CSGElement ) {
				// Check on the construction of this element
				CSGElement csgSpatial = (CSGElement)aSpatial;
				mRegenNS += csgSpatial.getShapeRegenerationNS();
				if ( !csgSpatial.isValid() ) {
					mInError = CSGConstructionException.registerError( mInError, csgSpatial.getError() );
				}
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
