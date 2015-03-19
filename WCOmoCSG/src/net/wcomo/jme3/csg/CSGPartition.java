/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014 jMonkeyEngine
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
package net.wcomo.jme3.csg;

import java.util.ArrayList;
import java.util.List;

import com.jme3.export.Savable;


/** Constructive Solid Geometry (CSG)

    A CSGPartition is a hierarchical container class used to keep track of a set of Polygons, along with
 	those in 'front' of and 'behind' a given plane.
 	
 	CSGPlane knows how to identify polygons in front of, behind, and in the same plane.  CSGPartition
 	uses this mechanism to build up the appropriate set of polygons that apply in its section 
 	of the hierarchy.
 */
public class CSGPartition 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPartitionRevision="$Rev$";
	public static final String sCSGPartitionDate="$Date$";

	/** The list of active polygons within this node */
	protected List<CSGPolygon>	mPolygons;
	/** The plane that defines this node */
	protected CSGPlane			mPlane;
	/** Those nodes in front of this one */
	protected CSGPartition		mFrontPartition;
	/** Those nodes behind this one */
	protected CSGPartition		mBackPartition;
	/** The custom Material index that applies to this Partition */
	protected int				mMaterialIndex;
	
	/** Simple null constructor */
	public CSGPartition(
	) {
		this( null, 0 );
	}
	/** Standard constructor that builds a hierarchy of nodes based on a given set of polygons */
	public CSGPartition(
		List<CSGPolygon>	pPolygons
	,	Number				pMaterialIndex
	) {
		this( pPolygons, (pMaterialIndex == null) ? 0 : pMaterialIndex.intValue() );
	}
	public CSGPartition(
		List<CSGPolygon>	pPolygons
	,	int					pMaterialIndex
	) {
		mPolygons = new ArrayList<CSGPolygon>();
		mMaterialIndex = pMaterialIndex;
		
		if ( pPolygons != null ) {
			buildHierarchy( pPolygons, 0 );
		}
	}
	public CSGPartition(
		List<CSGPolygon>	pPolygons
	) {
		mPolygons = new ArrayList<CSGPolygon>();
		
		if ( pPolygons != null ) {
			mMaterialIndex = pPolygons.get(0).getMaterialIndex();
			buildHierarchy( pPolygons, 0 );
		}
	}
	
	/** Accessor to the MaterialIndex */
	public int getMaterialIndex() { return mMaterialIndex; }
	

	/** Access to all polygons defined within this hierarchy */
	public List<CSGPolygon> allPolygons(
		List<CSGPolygon>	pPolyList
	) {
		if ( pPolyList == null ) {
			pPolyList = new ArrayList<CSGPolygon>( mPolygons.size() );
		}
		pPolyList.addAll( mPolygons );
		if ( mFrontPartition != null ) {
			mFrontPartition.allPolygons( pPolyList );
		}
		if ( mBackPartition != null ) {
			mBackPartition.allPolygons( pPolyList );
		}
		return( pPolyList );
	}
	
	/** 'CLIP' all the given polygons that are in front of this node */
	public List<CSGPolygon> clipPolygons(
		List<CSGPolygon>	pPolygons
	) {
		if ( mPlane == null ) {
			// If we have no effective plane, then everything is retained
			return new ArrayList<CSGPolygon>( pPolygons );
		}
		// Accumulate the appropriate lists of where the given polygons fall
		List<CSGPolygon> frontPolys = new ArrayList<CSGPolygon>();
		List<CSGPolygon> backPolys = new ArrayList<CSGPolygon>();
		for ( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that coplannar polygons are retained in front/back, based on which
			//		way they are facing
			mPlane.splitPolygon( aPolygon, frontPolys, backPolys, frontPolys, backPolys );
		}
		if ( mFrontPartition != null ) {
			// Include appropriate clipping from the front partition as well
			frontPolys = mFrontPartition.clipPolygons( frontPolys );
		}
		if ( mBackPartition != null ) {
			// Include appropriate clipping from the back partition as well
			backPolys = mBackPartition.clipPolygons( backPolys );
			
			// Keep it blended into the total list
			frontPolys.addAll( backPolys );
		}
		return( frontPolys );
	}
	
	/** Adjust the hierarchy (this node and all child nodes) by clipping against the
	 	other supplied node.
	 */
	public void clipTo(
		CSGPartition		pOther
	) {
		// Reset the list of polygons that apply based on clipping from the other partition
		mPolygons = pOther.clipPolygons( mPolygons );
		if ( mFrontPartition != null ) mFrontPartition.clipTo( pOther );
		if ( mBackPartition != null) mBackPartition.clipTo( pOther );
	}
		
	/** Invert this node */
	public void invert(
	) {
		List<CSGPolygon> flippedPolys = new ArrayList<CSGPolygon>( mPolygons.size() );
		for( CSGPolygon aPolygon : mPolygons ) {
			flippedPolys.add( aPolygon.clone( true ) );
		}
		mPolygons = flippedPolys;
		if ( mPlane != null ) {
			mPlane = mPlane.clone( true );
		}
		if ( mFrontPartition != null ) mFrontPartition.invert();
		if ( mBackPartition != null ) mBackPartition.invert();
		CSGPartition temp = mFrontPartition;
		mFrontPartition = mBackPartition;
		mBackPartition = temp;
	}
	
	/** Build a hierarchy of nodes from a given set of polygons */
	protected void buildHierarchy(
		List<CSGPolygon>	pPolygons
	,	int					pStackDepth
	) {
		if ( pPolygons.isEmpty() ) {
			return;
		}
		if ( pStackDepth > 10000 ) {
			// This is probably an error in the algorithm, but I have not yet
			// found the true cause.
			System.out.println( "Node.build - too deep" );
			return;
		}
		// If no plane has been set for this node, use the plane of the first polygon
		if ( mPlane == null ) {
			mPlane = pPolygons.get(0).getPlane();
		}
		// Split up the polygons according to front/back of the given plane
		List<CSGPolygon> front = new ArrayList<CSGPolygon>();
		List<CSGPolygon> back = new ArrayList<CSGPolygon>();
		for( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that for coplannar, we do not care which direction the polygon faces
			mPlane.splitPolygon( aPolygon, mPolygons, mPolygons, front, back );
		}
		if ( !front.isEmpty() ) {
			if (this.mFrontPartition == null) {
				this.mFrontPartition = new CSGPartition( null, this.mMaterialIndex );
			}
			if ( this.mPolygons.isEmpty() && back.isEmpty() ) {
				// Everything is in the front, it does not need to be processed deeper
				this.mFrontPartition.mPolygons.addAll( front );
			} else {
				// Assign whatever is in the front
				this.mFrontPartition.buildHierarchy( front, pStackDepth + 1 );
			}
		}
		if ( !back.isEmpty() ) {
			if ( mBackPartition == null ) {
				mBackPartition = new CSGPartition( null, this.mMaterialIndex );
			}
			if ( mPolygons.isEmpty() && front.isEmpty() ) {
				// Everything is in the back, it does not need to be processed deeper
				mBackPartition.mPolygons.addAll( front );
			} else {
				// Assign whatever is in the back
				mBackPartition.buildHierarchy( back, pStackDepth + 1 );
			}
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGPartitionRevision
													, sCSGPartitionDate
													, pBuffer ) );
	}

}
