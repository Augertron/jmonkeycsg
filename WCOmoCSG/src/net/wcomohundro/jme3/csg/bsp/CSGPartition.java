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
package net.wcomohundro.jme3.csg.bsp;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGPlane;
import net.wcomohundro.jme3.csg.CSGPolygon;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;
import com.jme3.util.TempVars;
import com.jme3.bounding.BoundingVolume;


/** Constructive Solid Geometry (CSG)

    A CSGPartition is a hierarchical container class used to keep track of a set of Polygons, along with
 	those in 'front' of and 'behind' a given plane.
 	
 	CSGPlane knows how to identify polygons in front of, behind, and in the same plane.  CSGPartition
 	uses this mechanism to build up the appropriate set of polygons that apply in its section 
 	of the hierarchy.
 	
 	After more research, it appears this is all related to the concept of BSP trees
 	(Binary Space Partitioning) which is a generic mechanism for producing a tree representation of 
 	a hierarchical set of shapes.  In 3D, each shape is a polygon.
 		@see ftp://ftp.sgi.com/other/bspfaq/faq/bspfaq.html
 	
 	You will notice that the structure of a CSGPartition closely mimics the C++ psuedo-code provided
 	in the paper above. The BSP construction code is closely followed by CSGPlane.splitPolygon() 
 	processing.  That being said, the BSP codes that all polygons are assumed to be convex.
 	I wonder if that is true in all cases for this code????
 */
public class CSGPartition 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPartitionRevision="$Rev$";
	public static final String sCSGPartitionDate="$Date$";

	
	/** The parent to this partition, either a CSGShape or another CSGPartition */
	protected Object			mParent;
	/** The level in the BSP hierarchy */
	protected int				mLevel;
	/** Marker if this level is corrupt */
	protected boolean			mCorrupted;
	/** Count of how many vertices were 'lost' while processing this partition */
	protected int				mLostVertices;
	/** The list of active polygons within this partition */
	protected List<CSGPolygon>	mPolygons;
	/** The plane that defines this partition  */
	protected CSGPlane			mPlane;
	/** Those partitions in front of this one */
	protected CSGPartition		mFrontPartition;
	/** Those partitions behind this one */
	protected CSGPartition		mBackPartition;
	/** The custom Material index that applies to this Partition */
	protected int				mMaterialIndex;
	
	/** Simple null constructor */
	public CSGPartition(
	) {
		this( null, 0, 1, CSGEnvironment.sStandardEnvironment );
	}
	/** Internal constructor that builds a hierarchy of nodes based on a set of polygons given later */
	protected CSGPartition(
		CSGPartition		pParentPartition
	,	int					pMaterialIndex
	,	int					pLevel
	,	CSGEnvironment		pEnvironment
	) {
		mParent = pParentPartition;
		mLevel = pLevel;
		mPolygons = new ArrayList<CSGPolygon>();
		mMaterialIndex = pMaterialIndex;
	}
	
	/** Public constructor to partition a given shape */
	public CSGPartition(
		CSGShapeBSP			pShape
	,	List<CSGPolygon>	pPolygons
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		mParent = pShape;
		mLevel = 1;
		mPolygons = new ArrayList<CSGPolygon>();
		
		if ( pPolygons != null ) {
			// Use the material assigned to the first polygon
			mMaterialIndex = pPolygons.get(0).getMaterialIndex();
			
	        // Avoid some object churn by using the 'temp' variables
	        buildHierarchy( pPolygons, pTempVars, pEnvironment );
		}
	}
	
	/** Accessor to the hierarchy level */
	public int getLevel() { return mLevel; }
	public boolean isValid() { return( !mCorrupted ); }
	public int whereCorrupt(
	) {
		int corruptLevel = 0;
		if ( mCorrupted ) {
			corruptLevel = mLevel;
			if ( mFrontPartition != null ) {
				int frontLevel = mFrontPartition.whereCorrupt();
				if ( frontLevel > corruptLevel ) {
					corruptLevel = frontLevel;
				}
			}
			if ( mBackPartition != null ) {
				int backLevel = mBackPartition.whereCorrupt();
				if ( backLevel > corruptLevel ) {
					corruptLevel = backLevel;
				}
			}
		}
		return( corruptLevel );
	}
	
	/** Accessor to the lost vertex tracker */
	public int getLostVertexCount(
		boolean		pTotalInHierarchy
	) {
		int aCount = mLostVertices;
		if ( pTotalInHierarchy ) {
			if ( mFrontPartition != null ) aCount += mFrontPartition.getLostVertexCount( true );
			if ( mBackPartition != null ) aCount += mBackPartition.getLostVertexCount( true );
		}
		return( aCount );
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
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( mCorrupted || (mPlane == null) ) {
			// If corrupted or if we have no effective plane, then everything is retained
			// with no deeper processing
			return new ArrayList<CSGPolygon>( pPolygons );
		}
		// Accumulate the appropriate lists of where the given polygons fall
		List<CSGPolygon> frontPolys = new ArrayList<CSGPolygon>();
		List<CSGPolygon> backPolys = new ArrayList<CSGPolygon>();
		for ( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that coplannar polygons are retained in front/back, based on which
			//		way they are facing
			mLostVertices += mPlane.splitPolygon( aPolygon
												, pEnvironment.mEpsilonOnPlane
												, mLevel
												, frontPolys, backPolys, frontPolys, backPolys
												, pTempVars
												, pEnvironment );
		}
		if ( mFrontPartition != null ) {
			// Include appropriate clipping from the front partition as well
			frontPolys = mFrontPartition.clipPolygons( frontPolys, pTempVars, pEnvironment );
		}
		if ( mBackPartition != null ) {
			// Include appropriate clipping from the back partition as well
			backPolys = mBackPartition.clipPolygons( backPolys, pTempVars, pEnvironment );
			
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
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		// Reset the list of polygons that apply based on clipping from the other partition
		mPolygons = pOther.clipPolygons( mPolygons, pTempVars, pEnvironment );
		if ( mFrontPartition != null ) mFrontPartition.clipTo( pOther, pTempVars, pEnvironment );
		if ( mBackPartition != null) mBackPartition.clipTo( pOther, pTempVars, pEnvironment );
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
	
	/** Build a hierarchy of nodes from a given set of polygons
	 	@return - true if any construction problems occur
	 */
	public boolean buildHierarchy(
		List<CSGPolygon>	pPolygons
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		boolean aCorruptHierarchy = mCorrupted;
		
		if ( mCorrupted || pPolygons.isEmpty() ) {
			return( aCorruptHierarchy );
		}
		if ( mLevel > pEnvironment.mBSPLimit ) {
			// This is probably an error in the algorithm, but I have not yet found the true cause.
			ConstructiveSolidGeometry.sLogger.log( Level.WARNING
			,   pEnvironment.mShapeName + "CSGPartition.buildHierarchy - too deep: " + mLevel );
			//mPolygons = CSGPolygon.compressPolygons( pPolygons );
			return( mCorrupted = true );
		}
		// If no plane has been set for this partition, select a plane to use
		if ( mPlane == null ) {
			int anIndex = (int)((pPolygons.size() -1) * pEnvironment.mPartitionSeedPlane);
			mPlane = pPolygons.get( anIndex ).getPlane();
		}
		// As we go deeper in the hierarchy, do NOT insist on the same level of tolerance
		// Otherwise, you will be looking for such detail that the polygons are so small that
		// you get very very odd results
		double aTolerance = pEnvironment.mEpsilonOnPlane; // * mLevel;
		
		// Split up the polygons according to front/back of the given plane
		List<CSGPolygon> front = new ArrayList<CSGPolygon>();
		List<CSGPolygon> back = new ArrayList<CSGPolygon>();
		for( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that for coplannar, we do not care which direction the polygon faces
			mLostVertices += mPlane.splitPolygon( aPolygon
												, aTolerance
												, mLevel
												, mPolygons, mPolygons, front, back
												, pTempVars
												, pEnvironment );
		}
		if ( !aCorruptHierarchy && !front.isEmpty() ) {
			if (this.mFrontPartition == null) {
				this.mFrontPartition 
					= new CSGPartition( this, this.mMaterialIndex, this.mLevel + 1, pEnvironment );
			}
			if ( this.mPolygons.isEmpty() && back.isEmpty() ) {
				// Everything is in the front, it does not need to be processed deeper
				this.mFrontPartition.mPolygons.addAll( front );
			} else {
				// Assign whatever is in the front
				aCorruptHierarchy 
					|= this.mFrontPartition.buildHierarchy( front, pTempVars, pEnvironment );
			}
		}
		if ( !aCorruptHierarchy && !back.isEmpty() ) {
			if ( mBackPartition == null ) {
				mBackPartition 
					= new CSGPartition( this, this.mMaterialIndex, this.mLevel + 1, pEnvironment );
			}
			if ( mPolygons.isEmpty() && front.isEmpty() ) {
				// Everything is in the back, it does not need to be processed deeper
				mBackPartition.mPolygons.addAll( back );
			} else {
				// Assign whatever is in the back
				aCorruptHierarchy 
					|= mBackPartition.buildHierarchy( back, pTempVars, pEnvironment );
			}
		}
		return( aCorruptHierarchy );
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
