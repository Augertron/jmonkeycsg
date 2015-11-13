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
import net.wcomohundro.jme3.csg.CSGPlaneDbl;
import net.wcomohundro.jme3.csg.CSGPlaneFlt;
import net.wcomohundro.jme3.csg.CSGPolygon;
import net.wcomohundro.jme3.csg.CSGPolygonDbl;
import net.wcomohundro.jme3.csg.CSGPolygonFlt;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.CSGVertexDbl;
import net.wcomohundro.jme3.csg.CSGVertexFlt;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;
import com.jme3.scene.plugins.blender.math.Vector3d;


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
 	in the paper above. The BSP construction code is closely followed splitPolygon() 
 	processing.  That being said, the BSP code assumes that all polygons are convex.
 	
 	An important aspect of a Partition is the ability to 'split' a given Polygon based on if the 
	polygon is in front of, behind, crosses, or lies on the plane itself. The polygon can then be
	included in an appropriate list of similar polygons.  In particular, a polygon that crosses the
	plane can be broken into two parts, the new polygon in front of the plane and the new polygon
	behind the plane.
	
	From the BSP FAQ paper: @see ftp://ftp.sgi.com/other/bspfaq/faq/bspfaq.html
-------------------------------------------------------------------------------------------------------
HOW DO YOU PARTITION A POLYGON WITH A PLANE?

Overview
	Partitioning a polygon with a plane is a matter of determining which side of the plane the polygon 
	is on. This is referred to as a front/back test, and is performed by testing each point in the 
	polygon against the plane. If all of the points lie to one side of the plane, then the entire 
	polygon is on that side and does not need to be split. If some points lie on both sides of the 
	plane, then the polygon is split into two or more pieces.

	The basic algorithm is to loop across all the edges of the polygon and find those for which one 
	vertex is on each side of the partition plane. The intersection points of these edges and 
	the plane are computed, and those points are used as new vertices for the resulting pieces.

Implementation notes
	Classifying a point with respect to a plane is done by passing the (x, y, z) values of the 
	point into the plane equation, Ax + By + Cz + D = 0. The result of this operation is the 
	distance from the plane to the point along the plane's normal vector. It will be positive 
	if the point is on the side of the plane pointed to by the normal vector, negative otherwise. 
	If the result is 0, the point is on the plane.

	For those not familiar with the plane equation, The values A, B, and C are the coordinate 
	values of the normal vector. D can be calculated by substituting a point known to be on 
	the plane for x, y, and z.

	Convex polygons are generally easier to deal with in BSP tree construction than concave 
	ones, because splitting them with a plane always results in exactly two convex pieces. 
	Furthermore, the algorithm for splitting convex polygons is straightforward and robust. 
	Splitting of concave polygons, especially self intersecting ones, is a significant problem 
	in its own right.
------------------------------------------------------------------------------------------------------	
	

 */
public class CSGPartition 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPartitionRevision="$Rev$";
	public static final String sCSGPartitionDate="$Date$";

	/** Factory level service routine to create appropriate polygon(s), compressing vertices
	 	and deciding if the polygon is 'worth' constructing.
	 	
	 	NOTE
	 		that the given list of vertices is 'compressed' as a side effect
	 	
	 	@return - a count of polygons added to the given list
	 	
	 	TempVars Usage:
	 		-- CSGPlane.fromVertices --
	 				vect5
	 				vect6
	 */
	public static int addPolygonDbl(
		List<CSGPolygon>	pPolyList
	,	List<CSGVertex>		pVertices
	,	int					pMaterialIndex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		// Compress out any spurious vertices before we resolve the plane
		double eccentricity = CSGVertexDbl.compressVertices( pVertices, null, pEnvironment );
		
		if ( pVertices.size() >= 3 ) {
			// Polygon is based on computed plane, regardless of active mode
			CSGPlaneDbl aPlane = CSGPlaneDbl.fromVertices( pVertices, pTempVars, pEnvironment );
			return( addPolygons( pPolyList, pVertices, aPlane, pMaterialIndex, pEnvironment ) );
		} else {
			// Nothing of interest
			return( 0 );
		}
	}
	public static int addPolygonDbl(
		List<CSGPolygon>	pPolyList
	,	CSGPolygon			pPolygon
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGPlaneDbl aPlane = (CSGPlaneDbl)pPolygon.getPlane();
		List<CSGVertex> vertexList = pPolygon.getVertices();
		
		if ( pEnvironment.mPolygonPlaneMode == CSGPolygonPlaneMode.FROM_VERTICES ) {
			// Force the use of the plane from the underlying vertices
			aPlane = CSGPlaneDbl.fromVertices( vertexList, pTempVars, pEnvironment );
			return( addPolygons( pPolyList, vertexList, aPlane, pPolygon.getMeshIndex(), pEnvironment ) );
		} else {
			// Use the polygon as given
			pPolyList.add( pPolygon );
			return( 1 );
		}
	}
	protected static int addPolygons(
		List<CSGPolygon>	pPolyList
	,	List<CSGVertex>		pVertices
	,	CSGPlaneDbl			pPlane
	,	int					pMaterialIndex
	,	CSGEnvironment		pEnvironment
	) {
		int polyCount = 0;
		
		// A NULL plane could indicate all vertices in a straight line
		if ( (pPlane != null) && pPlane.isValid() ) {
			if ( pEnvironment.mPolygonTriangleOnly ) {
				// Restrict our polygons to triangles
				for( int j = 1, last = pVertices.size() -2; j <= last; j += 1 ) {
					// Remember that CSGPolygon will hold onto the array, so a new instance is needed
					// for every triangle
					CSGVertexDbl[] vertices = new CSGVertexDbl[3];
					vertices[0] = (CSGVertexDbl)pVertices.get( 0 );
					vertices[1] = (CSGVertexDbl)pVertices.get( j );
					vertices[2] = (CSGVertexDbl)pVertices.get( j + 1 );
					
					CSGPolygonDbl aPolygon = new CSGPolygonDbl( vertices, pPlane, pMaterialIndex );
					pPolyList.add( aPolygon );
					polyCount += 1;
				}
			} else {
				// Multipoint polygon support
				CSGPolygonDbl aPolygon = new CSGPolygonDbl( pVertices, pPlane, pMaterialIndex );
				pPolyList.add( aPolygon );
				polyCount += 1;
			}
		}
		return( polyCount );
	}
	
	/** Factory level service routine to create appropriate polygon(s), compressing vertices
	 	and deciding if the polygon is 'worth' constructing.
	 	
	 	NOTE
	 		that the given list of vertices is 'compressed' as a side effect
	 	
	 	@return - a count of polygons added to the given list
	 	
	 	TempVars Usage:
	 		-- CSGPlane.fromVertices --
	 				vect4
	 				vect5
	 */
	public static int addPolygonFlt(
		List<CSGPolygon>	pPolyList
	,	List<CSGVertex>		pVertices
	,	int					pMaterialIndex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		// Compress out any spurious vertices before we resolve the plane
		float eccentricity = CSGVertexFlt.compressVertices( pVertices, null, pEnvironment );
		
		if ( pVertices.size() >= 3 ) {
			// Polygon is based on computed plane, regardless of active mode
			CSGPlaneFlt aPlane = CSGPlaneFlt.fromVertices( pVertices, pTempVars, pEnvironment );
			return( addPolygons( pPolyList, pVertices, aPlane, pMaterialIndex, pEnvironment ) );
		} else {
			// Nothing of interest
			return( 0 );
		}
	}
	public static int addPolygonFlt(
		List<CSGPolygon>	pPolyList
	,	List<CSGVertex>		pVertices
	,	CSGPlaneFlt			pPlane
	,	int					pMaterialIndex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( (pPlane != null) && pPlane.isValid() ) {
			// NOTE that compressVertices operates directly on the given list
			float eccentricity = CSGVertexFlt.compressVertices( pVertices, pPlane, pEnvironment );
			if ( pVertices.size() >= 3 ) {
				// We have enough vertices for a shape
				// NOTE when debugging, it can be useful to look for odd eccentricty values here....
				if ( pEnvironment.mPolygonPlaneMode == CSGPolygonPlaneMode.FROM_VERTICES ) {
					// Use the plane from the underlying vertices
					pPlane = CSGPlaneFlt.fromVertices( pVertices, pTempVars, pEnvironment );
				}
				return( addPolygons( pPolyList, pVertices, pPlane, pMaterialIndex, pEnvironment ) );
			}
		} else {
			throw new IllegalArgumentException( pEnvironment.mShapeName + "Incomplete Polygon" );
		}
		// We did NOT build anything of value
		return( 0 );
	}
	protected static int addPolygons(
		List<CSGPolygon>	pPolyList
	,	List<CSGVertex>		pVertices
	,	CSGPlaneFlt			pPlane
	,	int					pMaterialIndex
	,	CSGEnvironment		pEnvironment
	) {
		int polyCount = 0;
		
		// A NULL plane could indicate all vertices in a straight line
		if ( (pPlane != null) && pPlane.isValid() ) {
			if ( pEnvironment.mPolygonTriangleOnly ) {
				// Restrict our polygons to triangles
				for( int j = 1, last = pVertices.size() -2; j <= last; j += 1 ) {
					// Remember that CSGPolygon will hold onto the array, so a new instance is needed
					// for every triangle
					CSGVertexFlt[] vertices = new CSGVertexFlt[3];
					vertices[0] = (CSGVertexFlt)pVertices.get( 0 );
					vertices[1] = (CSGVertexFlt)pVertices.get( j );
					vertices[2] = (CSGVertexFlt)pVertices.get( j + 1 );
					
					CSGPolygonFlt aPolygon = new CSGPolygonFlt( vertices, pPlane, pMaterialIndex );
					pPolyList.add( aPolygon );
					polyCount += 1;
				}
			} else {
				// Multipoint polygon support
				CSGPolygonFlt aPolygon = new CSGPolygonFlt( pVertices, pPlane, pMaterialIndex );
				pPolyList.add( aPolygon );
				polyCount += 1;
			}
		}
		return( polyCount );
	}

	/** Provide a service that knows how to assign a given polygon to an appropriate 
	 	positional list based on its relationship to this plane.
	 	
	 	The options are:
	 		COPLANAR - the polygon is in the same plane as this one (within a given tolerance)
	 		FRONT - the polygon is in front of this plane
	 		BACK - the polygon is in back of this plane
	 		SPANNING - the polygon crosses the plane
	 		
	 	The resultant position is:
	 		COPLANER - 	same plane, but front/back based on which way it is facing
	 		FRONT -		front list
	 		BACK -		back list
	 		SPANNING - 	the polygon is split into two new polygons, with piece in front going
	 					into the front list, and the piece in back going into the back list
	 */
	protected static final int SAMEPLANE = -1;
	protected static final int COPLANAR = 0;
	
	protected static final int FRONT = 1;
	protected static final int FRONTISH = 2;
	
	protected static final int BACKISH = 4;
	protected static final int BACK = 8;
	
	protected static final int SPANNING = 9;
	
	
	
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
	/** The custom Mesh index that applies to this Partition */
	protected int				mMeshIndex;
	
	/** Simple null constructor */
	public CSGPartition(
	) {
		this( null, 0, 1, CSGShapeBSP.sDefaultEnvironment );
	}
	/** Internal constructor that builds a hierarchy of nodes based on a set of polygons given later */
	protected CSGPartition(
		CSGPartition		pParentPartition
	,	int					pMeshIndex
	,	int					pLevel
	,	CSGEnvironment		pEnvironment
	) {
		mParent = pParentPartition;
		mLevel = pLevel;
		mPolygons = new ArrayList<CSGPolygon>();
		mMeshIndex = pMeshIndex;
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
			mMeshIndex = pPolygons.get(0).getMeshIndex();
			
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
	
	/** Accessor to the MeshIndex */
	public int getMeshIndex() { return mMeshIndex; }
	

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
		double aTolerance;
		if ( pEnvironment.mDoublePrecision ) {
			aTolerance = pEnvironment.mEpsilonOnPlaneDbl;
		} else {
			aTolerance = pEnvironment.mEpsilonOnPlaneFlt;
		}
		// Accumulate the appropriate lists of where the given polygons fall
		List<CSGPolygon> frontPolys = new ArrayList<CSGPolygon>();
		List<CSGPolygon> backPolys = new ArrayList<CSGPolygon>();
		for ( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that coplannar polygons are retained in front/back, based on which
			//		way they are facing
			mLostVertices += splitPolygon( aPolygon
											, aTolerance
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
			CSGEnvironment.sLogger.log( Level.WARNING
			,   pEnvironment.mShapeName + "CSGPartition.buildHierarchy - too deep: " + mLevel );
			mPolygons.addAll( pPolygons );
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
		double aTolerance;
		if ( pEnvironment.mDoublePrecision ) {
			aTolerance = pEnvironment.mEpsilonOnPlaneDbl * mLevel;
		} else {
			aTolerance = pEnvironment.mEpsilonOnPlaneFlt * mLevel;
		}
		// Split up the polygons according to front/back of the given plane
		List<CSGPolygon> front = new ArrayList<CSGPolygon>();
		List<CSGPolygon> back = new ArrayList<CSGPolygon>();
		for( CSGPolygon aPolygon : pPolygons ) {
			// NOTE that for coplannar, we do not care which direction the polygon faces
			mLostVertices += splitPolygon( aPolygon
												, aTolerance
												, mPolygons, mPolygons, front, back
												, pTempVars
												, pEnvironment );
		}
		if ( !aCorruptHierarchy && !front.isEmpty() ) {
			if (this.mFrontPartition == null) {
				this.mFrontPartition 
					= new CSGPartition( this, this.mMeshIndex, this.mLevel + 1, pEnvironment );
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
					= new CSGPartition( this, this.mMeshIndex, this.mLevel + 1, pEnvironment );
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
	
	/** Provide a service that knows how to assign a given polygon to an appropriate 
	 	positional list based on its relationship to a plane.
	 	
	 	The options are:
	 		COPLANAR - the polygon is in the same plane as this partition (within a given tolerance)
	 		FRONT - the polygon is in front of this partition
	 		BACK - the polygon is in back of this partition
	 		SPANNING - the polygon crosses the partition
	 		
	 	The resultant position is:
	 		COPLANER - 	same plane, but front/back based on which way it is facing
	 		FRONT -		front list
	 		BACK -		back list
	 		SPANNING - 	the polygon is split into two new polygons, with piece in front going
	 					into the front list, and the piece in back going into the back list
	 					
	 	TempVars usage:
	 		-- Span Processing --
		 		vect1
		 		vect2
		 		vect2d
		 	-- CSGPolygon.createPolygon -- 
		 		vect5
	 			vect6
	
	 */
	protected int splitPolygon(
		CSGPolygon 			pPolygon
	,	double				pTolerance
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		int lostVertexCount;
		if ( pEnvironment.mDoublePrecision ) {
			lostVertexCount = splitPolygonDbl( (CSGPlaneDbl)mPlane
												, (CSGPolygonDbl)pPolygon
												, pTolerance
												, mLevel
												, pCoplanarFront, pCoplanarBack, pFront, pBack
												, pTempVars
												, pEnvironment );
		} else {
			lostVertexCount = splitPolygonFlt( (CSGPlaneFlt)mPlane
												, (CSGPolygonFlt)pPolygon
												, (float)pTolerance
												, mLevel
												, pCoplanarFront, pCoplanarBack, pFront, pBack
												, pTempVars
												, pEnvironment );
		}
		return( lostVertexCount );
	}
	protected int splitPolygonDbl(
		CSGPlaneDbl			pPlane
	,	CSGPolygonDbl		pPolygon
	,	double				pTolerance
	,	int					pHierarchyLevel
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		List<CSGVertex> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		
		Vector3d planeNormal = pPlane.getNormal();
		CSGPlaneDbl polygonPlane = pPolygon.getPlane();
		Vector3d polygonNormal = polygonPlane.getNormal();
		
		if ( !pPolygon.isValid() ) {
			// This polygon is not playing the game properly, ignore it
			return( vertexCount );
		}
		// A note from the web about what "dot" means in 3D graphics
		// 		When deciding if a polygon is facing the camera, you need only calculate the dot product 
		// 		of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
		// 		vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
		// 		value is greater than zero, it is facing away from the camera. 
		int polygonType = COPLANAR;
		int[] polygonTypes = null;
		double[] vertexDot = null;
		
		// Which way is the polygon facing?
		List<CSGPolygon> coplaneList 
			= (planeNormal.dot( polygonNormal ) >= 0) ? pCoplanarFront : pCoplanarBack;
		
		// NOTE that CSGPlane.equals() checks for near-misses
		//		I am going to try suppressing the check on the plane to account for those
		//		polygons that may be 'using' a given plane without being exactly on it....
		if ( false ) { //polygonPlane == this ) {
			polygonType = SAMEPLANE;
		} else if ( polygonPlane.equals( this, pTolerance ) ) { //pEnvironment.mEpsilonOnPlane ) ) {
			// By definition, we are close enough to be in the same plane
			polygonType = COPLANAR;
		} else { 
			// Check every vertex against the plane
			polygonTypes = new int[ vertexCount ];
			vertexDot = new double[ vertexCount ];
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Where is this vertex in relation to the plane?
				// Compare the vertex dot to the inherent plane dot
				Vector3d aVertexPosition = ((CSGVertexDbl)polygonVertices.get( i )).getPosition();
				double aVertexDot = vertexDot[i] = (planeNormal.dot( aVertexPosition ) - pPlane.getDot() );
				if ( CSGEnvironment.isFinite( aVertexDot ) ) {
					// If within a given tolerance, it is the same plane
					// See the discussion from the BSP FAQ paper about the distance of a point to the plane
					//int type = (aVertexDot < -pTolerance) ? BACK : (aVertexDot > pTolerance) ? FRONT : COPLANAR;
					int type;
					if ( (aVertexDot < 0.0) && (aVertexDot < -pEnvironment.mEpsilonNearZeroDbl) ) {
						// Somewhere in the back
						type = (aVertexDot < -pTolerance) ? BACK : BACKISH;
					} else if ( (aVertexDot > 0.0) && (aVertexDot > pEnvironment.mEpsilonNearZeroDbl) ) {
						// Somewhere in the front
						type = (aVertexDot > pTolerance) ? FRONT : FRONTISH;
					} else {
						type = COPLANAR;
					}
					polygonType |= type;
					polygonTypes[i] = type;
				} else {
					CSGEnvironment.sLogger.log( Level.SEVERE
					, pEnvironment.mShapeName + "Bogus Vertex: " + aVertexPosition );					
				}
			}
			if ( pEnvironment.mStructuralDebug && (polygonPlane == pPlane) && (polygonType != COPLANAR) ) {
				CSGEnvironment.sLogger.log( Level.SEVERE
				, pEnvironment.mShapeName + "Bogus polygon plane[" + pHierarchyLevel + "] " + polygonType );				
			}
		}
		switch( polygonType ) {
		default:
			CSGEnvironment.sLogger.log( Level.SEVERE
			, pEnvironment.mShapeName + "Bogus polygon split[" + pHierarchyLevel + "] " + polygonType );
			return( vertexCount );

		case SAMEPLANE:
			// The given polygon lies in this exact same plane
			coplaneList.add( pPolygon );
			break;

		case COPLANAR:
		case FRONTISH:
			// If coplanar, then we are for-sure on the plane.  But if strictly frontish, then we
			// are very close, so treat it like being on the plane as well.  This should prevent us
			// for subsequent split processing on the front.
			int coplaneCount
				= addPolygonDbl( coplaneList, pPolygon, pTempVars, pEnvironment );
			if ( coplaneCount < 1 ) {
				CSGEnvironment.sLogger.log( Level.WARNING
				, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
				return( vertexCount );
			}
			break;
			
		case FRONT:
		case FRONT | FRONTISH:
			// The given polygon is in front of this plane
			pFront.add( pPolygon );
			break;
			
		case BACK:
		case BACKISH:
		case BACK | BACKISH:
			// The given polygon is behind this plane
			// (and if frontish, not far enough in front to make a difference)
			pBack.add( pPolygon );
			break;
			
		case FRONT | BACK:
		case FRONT | BACK | FRONTISH:
		case FRONT | BACK | BACKISH:
		case FRONT | BACK | FRONTISH | BACKISH:
			
		case FRONT | BACKISH:
		case FRONT | FRONTISH | BACKISH:
			
		case BACK | FRONTISH:
		case BACK | BACKISH | FRONTISH:
			
		case FRONTISH | BACKISH:

			// The given polygon crosses this plane
			List<CSGVertex> beforeVertices = new ArrayList<CSGVertex>( vertexCount );
			List<CSGVertex> behindVertices = new ArrayList<CSGVertex>( vertexCount );		
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Compare to 'next' vertex (wrapping around at the end)
				int j = (i + 1) % vertexCount;
				
				int iType = polygonTypes[i];
				double iDot = vertexDot[i];
				CSGVertexDbl iVertex = (CSGVertexDbl)polygonVertices.get(i);
				
				int jType = polygonTypes[j];
				double jDot = vertexDot[j];
				CSGVertexDbl jVertex = (CSGVertexDbl)polygonVertices.get(j);
				
				switch( iType ) {
				case FRONT:
					// This vertex strictly in front
					beforeVertices.add( iVertex );
					break;
				case BACK:
					// This vertex strictly behind
					behindVertices.add( iVertex );
					break;
				case COPLANAR:
					// This vertex will be included on both sides
					beforeVertices.add( iVertex );
					behindVertices.add( iVertex.clone( false ) );
					break;
				case FRONTISH:
					// Sortof in front, but very close to the plane
					switch( jType ) {
					case FRONT:
					case FRONTISH:
						// Really in front
						beforeVertices.add( iVertex );
						break;
					case COPLANAR:
						// Treat as coplanar
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					case BACK:
						// I am only sortof in front, but the next is really in back
						behindVertices.add( iVertex );
						break;
					case BACKISH:
						// I am sortof in front, the next is sortof in back, but we are
						// both really close to the plane ????
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					}
					break;
				case BACKISH:
					// Sortof in back
					switch( jType ) {
					case BACK:
					case BACKISH:
						// Really in back
						behindVertices.add( iVertex );
						break;
					case COPLANAR:
						// Treat as coplanar
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					case FRONT:
						// I am only sortof in back, the the next is really in front
						beforeVertices.add( iVertex );
						break;
					case FRONTISH:
						// I am sortof in back, the next is sortof in front, but we are
						// both really close to the plane  ????
						beforeVertices.add( iVertex );
						behindVertices.add( iVertex.clone( false ) );
						break;
					}
					break;
				}
				if ((iType | jType) == (FRONT | BACK) ) {
					// If we cross the plane between these two vertices, then interpolate 
					// a new vertex on this plane itself, which is both before and behind.
					Vector3d pointA = iVertex.getPosition();
					Vector3d pointB = jVertex.getPosition();
					Vector3d intersectPoint 
						= pPlane.intersectLine( pointA, pointB, pTempVars, pEnvironment );
					double percentage = intersectPoint.distance( pointA ) / pointB.distance( pointA );
					
					CSGVertexDbl onPlane 
						= iVertex.interpolate( jVertex
						, percentage
						, intersectPoint
						, pTempVars
						, pEnvironment );
					if ( onPlane != null ) {
						beforeVertices.add( onPlane );
						behindVertices.add( onPlane.clone( false ) );
					}
				}
			}
			// What comes in front of the plane?
			int beforeCount
				= addPolygonDbl( pFront, beforeVertices, pPolygon.getMeshIndex()
											, pTempVars, pEnvironment );

			// What comes behind the given plane?
			int behindCount 
				= addPolygonDbl( pBack, behindVertices, pPolygon.getMeshIndex()
											, pTempVars, pEnvironment );

			if ( beforeCount == 0 ) {
				if ( behindCount == 0 ) {
					// We did not split the polygon at all, treat it as COPLANAR
					coplaneCount
						= addPolygonDbl( coplaneList, pPolygon, pTempVars, pEnvironment );
					if ( coplaneCount < 1 ) {
						CSGEnvironment.sLogger.log( Level.WARNING
						, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
						return( vertexCount );
					}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					//pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					CSGEnvironment.sLogger.log( Level.INFO
					, pEnvironment.mShapeName + "Discarding front vertices: " + beforeVertices.size() + "/" + vertexCount );
					return( vertexCount - beforeVertices.size() );
				}
			} else if ( (behindCount == 0) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				CSGEnvironment.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices: "+ behindVertices.size() + "/" + vertexCount );
				return( vertexCount - behindVertices.size() );
			}
			break;
		}
		return( 0 );
		
	}
	protected int splitPolygonFlt(
		CSGPlaneFlt			pPlane
	,	CSGPolygonFlt		pPolygon
	,	float				pTolerance
	,	int					pHierarchyLevel
	, 	List<CSGPolygon> 	pCoplanarFront
	, 	List<CSGPolygon> 	pCoplanarBack
	, 	List<CSGPolygon> 	pFront
	, 	List<CSGPolygon> 	pBack
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		List<CSGVertex> polygonVertices = pPolygon.getVertices();
		int vertexCount = polygonVertices.size();
		
		Vector3f planeNormal = pPlane.getNormal();
		CSGPlaneFlt polygonPlane = pPolygon.getPlane();
		Vector3f polygonNormal = polygonPlane.getNormal();

    	if ( !pPolygon.isValid() ) {
			// This polygon is not playing the game properly, ignore it
			return( vertexCount );
		}
		// A note from the web about what "dot" means in 3D graphics
		// 		When deciding if a polygon is facing the camera, you need only calculate the dot product 
		// 		of the normal vector of that polygon, with a vector from the camera to one of the polygon's 
		// 		vertices. If the dot product is less than zero, the polygon is facing the camera. If the 
		// 		value is greater than zero, it is facing away from the camera. 
		int polygonType = COPLANAR;
		int[] polygonTypes = null;
		float[] vertexDot = null;
		
		// Which way is the polygon facing?
		List<CSGPolygon> coplaneList 
			= (planeNormal.dot( polygonNormal ) > 0) ? pCoplanarFront : pCoplanarBack;
		
		// NOTE that CSGPlane.equals() checks for near-misses
		if ( polygonPlane == pPlane ) {
			polygonType = SAMEPLANE;
		} else if ( polygonPlane.equals( this, pTolerance ) ) { 
			// By definition, we are close enough to be in the same plane
			polygonType = COPLANAR;
		} else {
			// Check every vertex against the plane
			polygonTypes = new int[ vertexCount ];
			vertexDot = new float[ vertexCount ];
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Where is this vertex in relation to the plane?
				// Compare the vertex dot to the inherent plane dot
				Vector3f aVertexPosition = ((CSGVertexFlt)polygonVertices.get( i )).getPosition();
				float aVertexDot = vertexDot[i] = planeNormal.dot( aVertexPosition );
				aVertexDot -= pPlane.getDot();
				if ( CSGEnvironment.isFinite( aVertexDot ) ) {
					// If within a given tolerance, it is the same plane
					// See the discussion from the BSP FAQ paper about the distance of a point to the plane
					int type = (aVertexDot < -pTolerance) ? BACK : (aVertexDot > pTolerance) ? FRONT : COPLANAR;
					polygonType |= type;
					polygonTypes[i] = type;
				} else {
					CSGEnvironment.sLogger.log( Level.SEVERE
					, pEnvironment.mShapeName + "Bogus Vertex: " + aVertexPosition );					
				}
			}
		}
		switch( polygonType ) {
		case SAMEPLANE:
			// The given polygon lies in this exact same plane
			pCoplanarFront.add( pPolygon );
			break;

		case COPLANAR:
			// Force the polygon onto the plane as needed (working from a mutable copy of the poly list)
			List<CSGVertex> polygonCopy = new ArrayList<CSGVertex>( polygonVertices );
			int coplaneCount
				= addPolygonFlt( coplaneList
											, polygonCopy
											, (CSGPlaneFlt)mPlane
											, pPolygon.getMeshIndex()
											, pTempVars
											, pEnvironment );
			if ( coplaneCount < 1 ) {
				CSGEnvironment.sLogger.log( Level.WARNING
				, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
				return( vertexCount );
			}
			break;
			
		case FRONT:
			// The given polygon is in front of this plane
			pFront.add( pPolygon );
			break;
			
		case BACK:
			// The given polygon is behind this plane
			pBack.add( pPolygon );
			break;
			
		case SPANNING:
			// The given polygon crosses this plane
			List<CSGVertex> beforeVertices = new ArrayList<CSGVertex>( vertexCount );
			List<CSGVertex> behindVertices = new ArrayList<CSGVertex>( vertexCount );
			for( int i = 0; i < vertexCount; i += 1 ) {
				// Compare to 'next' vertex (wrapping around at the end)
				int j = (i + 1) % vertexCount;
				
				int iType = polygonTypes[i];
				int jType = polygonTypes[j];
				CSGVertexFlt iVertex = (CSGVertexFlt)polygonVertices.get(i);
				CSGVertexFlt jVertex = (CSGVertexFlt)polygonVertices.get(j);
				
				if ( iType != BACK ) {
					// If not in back, then must be in front (or COPLANAR)
					beforeVertices.add( iVertex );
				}
				if ( iType != FRONT ) {
					// If not in front, then must be behind (or COPLANAR)
					// NOTE that we need a new clone if it was already added to 'before'
					behindVertices.add( (iType != BACK) ? iVertex.clone( false ) : iVertex );
				}
				if ((iType | jType) == SPANNING ) {
					// If we cross the plane between these two vertices, then interpolate 
					// a new vertex on this plane itself, which is both before and behind.
					Vector3f pointA = iVertex.getPosition();
					Vector3f pointB = jVertex.getPosition();
					Vector3f intersectPoint 
						= pPlane.intersectLine( pointA, pointB, pTempVars.vect2, pEnvironment );
					float percentage = intersectPoint.distance( pointA ) / pointB.distance( pointA );
					
					CSGVertexFlt onPlane = iVertex.interpolate( jVertex
																, percentage
																, intersectPoint
																, pTempVars
																, pEnvironment );
					if ( onPlane != null ) {
						beforeVertices.add( onPlane );
						behindVertices.add( onPlane.clone( false ) );
					}
				}
			}
/***
			// What comes in front of the given plane?
			CSGPolygon beforePolygon
				= CSGPolygon.createPolygon( beforeVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );

			// What comes behind the given plane?
			CSGPolygon behindPolygon
				= CSGPolygon.createPolygon( behindVertices, pPolygon.getMaterialIndex(), pTempVars, pEnvironment );
			
/** Retry the split with more forgiving tolerance
			if ( (beforePolygon == null) && !beforeVertices.isEmpty() ) {
				// Not enough distinct vertices
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
					, "Discarding front vertices[" + pHierarchyLevel + "] " + beforeVertices.size() + "/" + vertexCount );
				behindPolygon = null;
			}
			if ( (behindPolygon == null) && !behindVertices.isEmpty() ) {
				// Not enough distinct vertices
				ConstructiveSolidGeometry.sLogger.log( Level.WARNING
					, "Discarding back vertices[" + pHierarchyLevel + "] "+ behindVertices.size() + "/" + vertexCount );
				beforePolygon = null;
			}
			if ( beforePolygon != null ) {
				pFront.add( beforePolygon  );
			}
			if ( behindPolygon != null ) {
				pBack.add( behindPolygon );
			} else if ( beforePolygon == null ) {
				// The polygon did not split well, try again with more tolerance
				splitPolygon(	pPolygon
								,	pTolerance * 2.0f
								,   pHierarchyLevel + 1
								, 	pCoplanarFront
								, 	pCoplanarBack
								, 	pFront
								, 	pBack
								,	pTemp3f
								,	pTemp2f
								,	pEnvironment
								);
				return( false );
			}
**/
/***** when operating on non-triangular polygons
			if ( beforePolygon != null ) {
				pFront.add( beforePolygon  );
			}
			if ( behindPolygon != null ) {
				pBack.add( behindPolygon );
			}
			if ( beforePolygon == null ) {
				if ( behindPolygon == null ) {
					// We did not split the polygon at all, treat it as COPLANAR
					polygonCopy = new ArrayList<CSGVertexFlt>( polygonVertices );
					pPolygon = CSGPolygon.createPolygon( polygonCopy, this, pPolygon.getMaterialIndex()
														, pTempVars, pEnvironment );
					if ( pPolygon != null ) {
						pCoplanarFront.add( pPolygon );
						ConstructiveSolidGeometry.sLogger.log( Level.INFO
						, pEnvironment.mShapeName + "Coopting planar vertices: " + vertexCount );
						return( false );
					}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					//pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					ConstructiveSolidGeometry.sLogger.log( Level.INFO
					, pEnvironment.mShapeName + "Discarding front vertices: " + beforeVertices.size() + "/" + vertexCount );
					return( false );
				}
			} else if ( (behindPolygon == null) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				ConstructiveSolidGeometry.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices: "+ behindVertices.size() + "/" + vertexCount );
				return( false );
			}
****/
/*** when operating on possibly triangular polygons ***/
			int beforeCount
				= addPolygonFlt( pFront, beforeVertices, pPolygon.getMeshIndex(), pTempVars, pEnvironment );

			// What comes behind the given plane?
			int behindCount 
				= addPolygonFlt( pBack, behindVertices, pPolygon.getMeshIndex(), pTempVars, pEnvironment );

			if ( beforeCount == 0 ) {
				if ( behindCount == 0 ) {
					// We did not split the polygon at all, treat it as COPLANAR
					polygonCopy = new ArrayList<CSGVertex>( polygonVertices );
					coplaneCount = addPolygonFlt( pCoplanarFront
															, polygonCopy
															, (CSGPlaneFlt)mPlane
															, pPolygon.getMeshIndex()
														    , pTempVars, pEnvironment );
					if ( coplaneCount > 0 ) {
						CSGEnvironment.sLogger.log( Level.INFO
						, pEnvironment.mShapeName + "Coopting planar vertices[" + pHierarchyLevel + "] " + vertexCount );
					} else {
						CSGEnvironment.sLogger.log( Level.WARNING
						, pEnvironment.mShapeName + "Bogus COPLANAR polygon[" + pHierarchyLevel + "] " + pPolygon );
						return( vertexCount );
					}
				} else if ( !beforeVertices.isEmpty() ) {
					// There is some fragment before but not enough for an independent shape
					// @todo - investigate further if we need to retain this polygon in front or just drop it
					//pFront.add( pPolygon );		// Just adding the full poly to the front does NOT work
					//coplaneList.add( pPolygon );		// Just adding the full poly to the plane does not work
					CSGEnvironment.sLogger.log( Level.INFO
					, pEnvironment.mShapeName + "Discarding front vertices[" + pHierarchyLevel + "] " + beforeVertices.size() + "/" + vertexCount );
					return( vertexCount - beforeVertices.size() );
				}
			} else if ( (behindCount == 0) && !behindVertices.isEmpty() ) {
				// There is some fragment behind, but not enough for an independent shape
				// @todo - investigate further if we need to retain this polygon in back, or if we just drop it
				//pBack.add( pPolygon );			// Just adding the full poly to the back does NOT work
				//coplaneList.add( pPolygon );			// Just adding the full poly to the plane does not work
				CSGEnvironment.sLogger.log( Level.INFO
				, pEnvironment.mShapeName + "Discarding back vertices[" + pHierarchyLevel + "] " + behindVertices.size() + "/" + vertexCount );
				return( vertexCount - behindVertices.size() );
			}
			break;
		}
		return( 0 );
		
	}


	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGPartitionRevision
													, sCSGPartitionDate
													, pBuffer ) );
	}

}
