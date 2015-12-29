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

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGPlaneDbl;
import net.wcomohundro.jme3.csg.CSGPlaneFlt;
import net.wcomohundro.jme3.csg.CSGPolygon;
import net.wcomohundro.jme3.csg.CSGPolygonDbl;
import net.wcomohundro.jme3.csg.CSGPolygonFlt;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;
import net.wcomohundro.jme3.csg.bsp.CSGPartition;
import net.wcomohundro.jme3.csg.iob.CSGEnvironmentIOB;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.Mesh.Mode;
import com.jme3.material.Material;
import com.jme3.material.MatParamTexture;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

/** Constructive Solid Geometry (CSG) and Binary Space Partitioning
 
 	As far as I can tell, Evan Wallace put together a Javascript library for CSG support within 
 	browsers that support WebGL.  @see http://evanw.github.io/csg.js  (it is really quite impressive)
 	This was converted into jMonkey compatible Java by "fabsterpal", and posted to a Github repository
 	by "andychase".  Everything seems to have been properly posted and annotated for fully open 
 	source.
 	
 -- Binary Space Partitioning --
    BSP is a generic mechanism for producing a tree representation of a hierarchical set of shapes.  
    In 3D, each shape is a polygon.
 		@see ftp://ftp.sgi.com/other/bspfaq/faq/bspfaq.html 	
 		
 	While working with the "fabsterpal" code, I tripped over some bugs and encountered many spots
 	where I wished for a deeper level of comments.  My personal learning style is to work from an
 	operational example.  From there, I can make small, incremental changes to help we understand
 	the larger picture.  To that end, I re-implemented the Java code, following my own
 	conventions and structures.  But the logic and BSP algorithms are all based directly on what I
 	found in the original Javascript/Java code.
 	
 	My initial cut worked quite nicely, and rather quickly allowed me to duplicate the 
 	functionality of the original "fabsterpal" BSP code while correcting some bugs.
 	But as I worked with more/complex test cases, I started seeing some odd triangular 'drop outs'
 	and discrepancies.  At a certain point in a complex shape, bits of the polygons went missing, 
 	or were strangely warped.  Others have referred to these elements as 'artifacts'.
 	
 	Since simple shapes work just fine, I originally believed that the core logic was proper.  So I 
 	started going after rounding/overflow types of problems.  My thought was that a triangle went 
 	crazy due to a corrupted vertex.  For this reason, you will run across various ASSERT style 
 	checks that are looking for absurd constructs.  In particular, I have tried to be very careful
 	with boolean and comparison checks on vertex values, trying to remain sensitive to any 
 	possible NaN or Infinity condition. 
 	
 	** Please note the Java language specification **
 		Floating-point operators produce no exceptions. An operation that overflows 
 		produces a signed infinity, an operation that underflows produces a denormalized value 
 		or a signed zero, and an operation that has no mathematically definite result produces 
 		NaN. All numeric operations with NaN as an operand produce NaN as a result. As has already 
 		been described, NaN is unordered, so a numeric comparison operation involving one or two 
 		NaNs returns false and any != comparison involving NaN returns true, including x!=x when 
 		x is NaN.
 		
 	FYI, I never detected an overflow/NaN condition when generating a vertex.
 
 	While testing, I noticed I could get better results with fewer 'artifacts' if I carefully 
 	adjusted the tolerance of when a point was on-plane or not.  That explains the various
 	EPSILON values defined via CSGEnvironment.  I needed to set different tolerances for different 
 	conditions.  So a larger EPSILON_ONPLANE value gave me better looking results.  But it
 	did not fix all cases.
 	
 	I have refactored the code to allow me to operate in either float or double precision. I have
 	added support for forcing points near to a plane to be recomputed onto the plane. And you
 	will find various other experiments and test conditions within the BSP code trying to 
 	address the 'artifact' problem.
 	
 	 Subsequent searching on the web discovered this paper:
 		https://www.andrew.cmu.edu/user/jackiey/resources/CSG/CSG_report.pdf
 		csegurar@andrew.cmu.edu, tstine@andrew.cmu.edu, jackiey@andrew.cmu.edu (all accounts now inactive)
 	in which the authors are discussing CSG and BSP.  In particular was this:
 	
 		the Boolean operation would yield results with minor geometry artifacts. The artifacts 
 		that appear in some of our results appear to manifest themselves when operations are 
 		performed on objects with low polygon count. We believe that this occurs due to the 
 		lower resolution of one model relative to the other, and thus when the BSP trees of 
 		both models are merged, we occasionally are left with extraneous triangles that must 
 		get pushed through the BSP tree more than once but end up getting catalogued incorrectly.
 		
 	A few other sources related to BSP (if not specifically CSG) indicate an inherent flaw
 	with the approach itself:
 	
 		An exact representation can be obtained using signed Euclidean distance from a given 
 		point to the polygonal mesh.  The main problem with this solution is that the Euclidean 
 		distance is not Cprime-continuous and has points with the derivatives discontinuity
		(vanishing gradients) in its domain, which can cause appearance of unexpected artifacts 
		in results of further operations on the object.
		
	And the following paper may be directly addressing the problem I am seeing, but the 
	math is too deep for me.
		http://www.pascucci.org/pdf-papers/progressive-bsp.pdf
		
	In particular is the discussion of:
		The case (2) above is numerically unstable. Three types of labels are used for vertex 
		classification, labeling a vertex as v= when it is contained on the h hyperplane. No problems 
		would arise in a computation with infinite precision. Unfortunately, real computers
		are not infinitely precise, so that some vertex classification can be inexact. In order 
		to recover from wrong vertex classifications and to consistently compute the split complex, 
		further information concerning topological structure must be used.
 		
 	IN CONCLUSTION --- All of these statements lead me to believe there is a fundamental limitation 
 	in the underlying BSP algorithm. Somewhere deep inside the BSP, a polygon split is required and 
 	the limited precision can mean that multiple, incorrect assignment of vertices can occur.  
 	Unfortunately, I still have no idea how to identify, detect, capture, and fix such a condition.
 
 	----- From Evan Wallace -----
 	All CSG operations are implemented in terms of two functions, clipTo() and invert(), 
 	which remove parts of a BSP tree inside another BSP tree and swap solid and empty space, 
 	respectively. 
 	
 	To find the union of a and b, we want to remove everything in a inside b and everything 
 	in b inside a, then combine polygons from a and b into one solid:

		a.clipTo(b);
		b.clipTo(a);
		a.build(b.allPolygons());

	The only tricky part is handling overlapping coplanar polygons in both trees. The code 
	above keeps both copies, but we need to keep them in one tree and remove them in the other 
	tree. To remove them from b we can clip the inverse of b against a. The code for union now 
	looks like this:

		a.clipTo(b);
		b.clipTo(a);
		b.invert();
		b.clipTo(a);
		b.invert();
		a.build(b.allPolygons());

	Subtraction and intersection naturally follow from set operations. If union is A | B, 
	subtraction is A - B = ~(~A | B) and intersection is A & B = ~(~A | ~B) where ~ is 
	the complement operator.
	
 */
public class CSGShapeBSP
	implements CSGShape.CSGShapeProcessor<CSGEnvironmentBSP>, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeBSPRevision="$Rev$";
	public static final String sCSGShapeBSPDate="$Date$";

	/** Default configuration that applies to BSP processing */
	public static CSGEnvironmentBSP sDefaultEnvironment = new CSGEnvironmentBSP();

	/** Canned, immutable empty list of polygons */
	protected static final List<CSGPolygon> sEmptyPolygons = new ArrayList<CSGPolygon>(0);
	
	/** Factory level service routine to construct appropriate polygons */
	public static int addPolygon(
		List<CSGPolygon>	pPolyList
	,	CSGVertex[]			pVertices
	,	int					pMaterialIndex
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		// NOTE that aPlane comes back null if we lack vertices
		if ( pEnvironment.mDoublePrecision ) {
			// Work with doubles
			CSGPlaneDbl aPlane = CSGPlaneDbl.fromVertices( pVertices, pTempVars, pEnvironment );
			if ( (aPlane != null) && aPlane.isValid() ) {
				// Polygon is based on computed plane, regardless of active mode
				CSGPolygonDbl aPolygon = new CSGPolygonDbl( pVertices, aPlane, pMaterialIndex );
				pPolyList.add( aPolygon );
				return( 1 );
			}
		} else {
			// Work with floats
			CSGPlaneFlt aPlane = CSGPlaneFlt.fromVertices( pVertices, pTempVars, pEnvironment );
			if ( (aPlane != null) && aPlane.isValid() ) {
				// Polygon is based on computed plane, regardless of active mode
				CSGPolygonFlt aPolygon = new CSGPolygonFlt( pVertices, aPlane, pMaterialIndex );
				pPolyList.add( aPolygon );
				return( 1 );
			}
		}
		// Nothing of interest
		return( 0 );
	}


    /** The underlying shape we operate on */
    protected CSGShape					mShape;
	/** BSP hierarchy level where the partitioning became corrupt */
    protected int						mCorruptLevel;
    /** Count of vertices 'lost' while constructing this shape */
    protected int						mLostVertices;
	/** The list of polygons that make up this shape */
	protected List<CSGPolygon>			mPolygons;

	
	/** Basic null constructor */
	public CSGShapeBSP(
	) {
		this( null, sEmptyPolygons );
	}

	/** Constructor based on an explicit list of polygons, as determined by a blend of shapes */
	protected CSGShapeBSP(
		CSGShape			pForShape
	,	List<CSGPolygon>	pPolygons
	) {
		mShape = pForShape;
		mPolygons = pPolygons;
	}
	
	/** Ready a list of shapes for processing */
	@Override
	public List<CSGShape> prepareShapeList(
		List<CSGShape>		pShapeList
	,	CSGEnvironmentBSP	pEnvironment
	) {
		List<CSGShape> sortedShapes = new ArrayList<>( pShapeList );
		Collections.sort( sortedShapes, new Comparator<CSGShape>() {
			@Override
			public int compare( CSGShape pA, CSGShape pB ) {
				int thisOrder = pA.getOrder(), otherOrder = pB.getOrder();
				
				if ( thisOrder == otherOrder ) {
					// CSG should be applied inner-level as follows: Union -> Intersection -> Difference
					switch( pA.getOperator() ) {
					case UNION:
						switch( pB.getOperator() ) {
						case UNION: return( 0 );		// Same as other UNION
						default: return( -1 );			// Before all others
						}
						
					case INTERSECTION:
						switch( pB.getOperator() ) {
						case UNION: return( 1 );		// After UNION
						case INTERSECTION: return( 0 );	// Same as other INTERSECTION
						default: return( -1 );			// Before all others
						}
						
					case DIFFERENCE:
						switch( pB.getOperator() ) {
						case UNION:
						case INTERSECTION: 
							return( 1 );				// After UNION/INTERSECTION
						case DIFFERENCE: return( 0 );	// Same as other DIFFERENCE
						default: return( -1 );			// Before all others
						}
						
					case SKIP:
						switch( pB.getOperator() ) {
						case SKIP: return( 0 );			// Same as other SKIP
						default: return( 1 );			// After all others
						}
						
					case MERGE:
						switch( pB.getOperator() ) {
						case MERGE: return( 0 );		// Same as other MERGE
						default: return( -1 );			// Before all others
						}
					}
					// If we fall out the above, then we come before
					return( -1 );
				}
				return( thisOrder - otherOrder );
			}
		});
		return( sortedShapes );
	}
		
	/** The shape is 'valid' so long as its BSP hierarchy is not corrupt */
	public boolean isValid() { return( mCorruptLevel == 0 ); }
	public int whereCorrupt() { return( mCorruptLevel ); }
	public void setCorrupt(
		CSGPartition	pPartitionA
	,	CSGPartition	pPartitionB
	) {
		int whereCorrupt;
		whereCorrupt = pPartitionA.whereCorrupt();
		if ( whereCorrupt > mCorruptLevel ) mCorruptLevel = whereCorrupt;
		whereCorrupt = pPartitionB.whereCorrupt();
		if ( whereCorrupt > mCorruptLevel ) mCorruptLevel = whereCorrupt;
		
		mLostVertices = pPartitionA.getLostVertexCount( true ) + pPartitionB.getLostVertexCount( true );
	}
	/** Accessor to how many vertices where 'lost' during construction */
	public int getLostVertexCount() { return mLostVertices; }
	
	/** Connect to a shape */
	@Override
	public CSGShapeProcessor setShape(
		CSGShape		pForShape
	) {
		mShape = pForShape;
		return( this );
	}
	
	/** Refresh this handler and ensure it is ready for blending */
	@Override
	public CSGShapeProcessor refresh(
	) {
		// Ensure we start with an empty list of polygons
		mPolygons.clear();
		return( this );
	}

	/** Make a copy of this shape */
	@Override
	public CSGShape.CSGShapeProcessor clone(
		CSGShape	pForShape
	) {
		return( new CSGShapeBSP( pForShape, sEmptyPolygons ) );
	}

	
	/** Accessor to the list of polygons */
	protected List<CSGPolygon> getPolygons(
		CSGMeshManager		pMaterialManager
	,	int					pLevelOfDetail
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) { 
		if ( mPolygons.isEmpty() && (mShape.getMesh() != null) ) {
			// Generate the polygons
			mPolygons = fromMesh( mShape.getMesh()
									, mShape.getCSGTransform()
									, pMaterialManager
									, pLevelOfDetail
									, pTempVars
									, pEnvironment );
		}
		return mPolygons; 
	}
	
	/** Add a shape into this one */
	@Override
	public CSGShape union(
		CSGShape			pOther
	,	CSGMeshManager		pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		CSGPartition a = new CSGPartition( this
											, this.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment );
		CSGPartition b = new CSGPartition( otherBSP
											, otherBSP.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		
		a.clipTo( b, pTempVars, pEnvironment );
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		a.buildHierarchy( b.allPolygons( null ), pTempVars, pEnvironment );

		CSGShapeBSP aHandler = new CSGShapeBSP( null, a.allPolygons( null ) );
		aHandler.setCorrupt( a, b );
		CSGShape aShape = new CSGShape( aHandler, mShape.getName(), mShape.getOrder(), aHandler.isValid() );
		return( aShape );
	}
	
	/** Subtract a shape from this one */
	@Override
	public CSGShape difference(
		CSGShape			pOther
	,	CSGMeshManager		pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		CSGPartition a = new CSGPartition( this
											, this.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		CSGPartition b = new CSGPartition( otherBSP
											, otherBSP.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment )
											, pTempVars
											, pEnvironment  );
		
		a.invert();
		a.clipTo( b, pTempVars, pEnvironment );
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		b.clipTo( a, pTempVars, pEnvironment );
		b.invert();
		a.buildHierarchy( b.allPolygons( null ), pTempVars, pEnvironment );
		a.invert();

		CSGShapeBSP aHandler = new CSGShapeBSP( null, a.allPolygons( null ) );
		aHandler.setCorrupt( a, b );
		CSGShape aShape = new CSGShape( aHandler, mShape.getName(), mShape.getOrder(), aHandler.isValid() );
        return( aShape );
	}

	/** Find the intersection with another shape */
	@Override
	public CSGShape intersection(
		CSGShape			pOther
	,	CSGMeshManager		pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		List<CSGPolygon> thisList = this.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment );
		
	    List<CSGPolygon> otherList = otherBSP.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment );
		this.mPolygons.addAll( otherList );
	    return( this.mShape );
	}
	
	/** Find the merge with another shape */
	@Override
	public CSGShape merge(
		CSGShape			pOther
	,	CSGMeshManager		pMaterialManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		CSGShapeBSP otherBSP = (CSGShapeBSP)pOther.getHandler( pEnvironment );
		List<CSGPolygon> thisList = this.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment );
		
	    List<CSGPolygon> otherList = otherBSP.getPolygons( pMaterialManager, 0, pTempVars, pEnvironment );
		this.mPolygons.addAll( otherList );
	    return( this.mShape );
	}

	/** Produce the set of polygons that correspond to a given mesh */
	protected List<CSGPolygon> fromMesh(
		Mesh				pMesh
	,	Transform			pTransform
	,	CSGMeshManager		pMeshManager
	,	int					pLevelOfDetail
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {
		// Convert the mesh in to appropriate polygons
	    VertexBuffer indexBuffer = pMesh.getBuffer( VertexBuffer.Type.Index );
		IndexBuffer idxBuffer = null;
		if ( (pLevelOfDetail > 0) && (pLevelOfDetail < pMesh.getNumLodLevels()) ) {
			// Look for the given level of detail
			VertexBuffer lodBuffer = pMesh.getLodLevel( pLevelOfDetail );
			if ( lodBuffer != null ) {
				idxBuffer = IndexBuffer.wrapIndexBuffer( lodBuffer.getData() );
			}
		}
		if ( idxBuffer == null ) {
			// Use the 'standard'
			idxBuffer = pMesh.getIndexBuffer();
		}
		Mode meshMode = pMesh.getMode();
		FloatBuffer posBuffer = pMesh.getFloatBuffer( Type.Position );
		FloatBuffer normBuffer = pMesh.getFloatBuffer( Type.Normal );
		FloatBuffer texCoordBuffer = pMesh.getFloatBuffer( Type.TexCoord );
		if ( pEnvironment.mStructuralDebug ) {
			switch( meshMode ) {
			case Triangles:
				// This is the only one we can deal with at this time
				break;
			default:
				throw new IllegalArgumentException( "Only Mode.Triangles type mesh is currently supported" );
			}
			if ( posBuffer == null ) {
				throw new IllegalArgumentException( "Mesh lacking Type.Position buffer" );
			}
			if ( normBuffer == null ) {
				throw new IllegalArgumentException( "Mesh lacking Type.Normal buffer" );
			}
		}
		// Work from 3 points which define a triangle
		List<CSGPolygon> polygons = new ArrayList<CSGPolygon>( idxBuffer.size() / 3 );
		for( int i = 0, j = 0; i < idxBuffer.size(); i += 3, j += 1 ) {
			int idx1 = idxBuffer.get(i);
			int idx2 = idxBuffer.get(i + 1);
			int idx3 = idxBuffer.get(i + 2);
			
			int idx1x3 = idx1 * 3;
			int idx2x3 = idx2 * 3;
			int idx3x3 = idx3 * 3;
			
			int idx1x2 = idx1 * 2;
			int idx2x2 = idx2 * 2;
			int idx3x2 = idx3 * 2;
			
			// Extract the positions
			Vector3f pos1 = new Vector3f( posBuffer.get( idx1x3 )
										, posBuffer.get( idx1x3 + 1)
										, posBuffer.get( idx1x3 + 2) );
			Vector3f pos2 = new Vector3f( posBuffer.get( idx2x3 )
										, posBuffer.get( idx2x3 + 1)
										, posBuffer.get( idx2x3 + 2) );
			Vector3f pos3 = new Vector3f( posBuffer.get( idx3x3 )
										, posBuffer.get( idx3x3 + 1)
										, posBuffer.get( idx3x3 + 2) );

			// Extract the normals
			Vector3f norm1 = new Vector3f( normBuffer.get( idx1x3 )
										, normBuffer.get( idx1x3 + 1)
										, normBuffer.get( idx1x3 + 2) );
			Vector3f norm2 = new Vector3f( normBuffer.get( idx2x3 )
										, normBuffer.get( idx2x3 + 1)
										, normBuffer.get( idx2x3 + 2) );
			Vector3f norm3 = new Vector3f( normBuffer.get( idx3x3)
										, normBuffer.get( idx3x3 + 1)
										, normBuffer.get( idx3x3 + 2) );

			// Extract the Texture Coordinates
			// Based on an interaction via the SourceForge Ticket system, another user has informed
			// me that UV texture coordinates are optional.... so be it
			Vector2f texCoord1, texCoord2, texCoord3;
			if ( texCoordBuffer != null ) {
				texCoord1 = new Vector2f( texCoordBuffer.get( idx1x2)
											, texCoordBuffer.get( idx1x2 + 1) );
				texCoord2 = new Vector2f( texCoordBuffer.get( idx2x2)
											, texCoordBuffer.get( idx2x2 + 1) );
				texCoord3 = new Vector2f( texCoordBuffer.get( idx3x2)
											, texCoordBuffer.get( idx3x2 + 1) );
			} else {
				texCoord1 = texCoord2 = texCoord3 = Vector2f.ZERO;
			}
			// Construct the vertices that define the points of the triangle
			CSGVertex[] aVertexList = new CSGVertex[ 3 ];
			aVertexList[0] = CSGVertex.makeVertex( pos1, norm1, texCoord1, pTransform, pEnvironment );
			aVertexList[1] = CSGVertex.makeVertex( pos2, norm2, texCoord2, pTransform, pEnvironment );
			aVertexList[2] = CSGVertex.makeVertex( pos3, norm3, texCoord3, pTransform, pEnvironment );
			
			// And build the appropriate polygon (assuming the vertices are far enough apart to be significant)
			int polyCount = addPolygon( polygons
										, aVertexList
										, mShape.getMeshIndex( pMeshManager, j )
										, pTempVars
										, pEnvironment );
		}
		return( polygons );
	}
	
	/** Produce the mesh(es) that corresponds to this shape
	 	The zeroth mesh in the list is the total, composite mesh.
	 	Every other mesh (if present) applies solely to a specific Material.
	  */
	@Override
	public void toMesh(
		CSGMeshManager		pMeshManager
	,	boolean				pProduceSubelements
	,	CSGTempVars			pTempVars
	,	CSGEnvironmentBSP	pEnvironment
	) {	
		List<CSGPolygon> aPolyList = getPolygons( pMeshManager, 0, pTempVars, pEnvironment );
		int anEstimateVertexCount = aPolyList.size() * 3;
		
		List<Vector3f> aPositionList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector3f> aNormalList = new ArrayList<Vector3f>( anEstimateVertexCount );
		List<Vector2f> aTexCoordList = new ArrayList<Vector2f>( anEstimateVertexCount  );
		List<Number> anIndexList = new ArrayList<Number>( anEstimateVertexCount );
		
		// Include the master list of all elements
		Mesh aMesh = toMesh( -1, aPolyList, aPositionList, aNormalList, aTexCoordList, anIndexList );
		pMeshManager.registerMasterMesh( aMesh );
		
		if ( pProduceSubelements ) {
			// Produce the meshes for all the sub elements
			for( int index = 0; index <= pMeshManager.getMeshCount(); index += 1 ) {
				// The zeroth index is the generic Material, all others are custom Materials
				aPositionList.clear(); aNormalList.clear(); aTexCoordList.clear(); anIndexList.clear();
				aMesh = toMesh( index, aPolyList, aPositionList, aNormalList, aTexCoordList, anIndexList );
				pMeshManager.registerMesh( aMesh, new Integer( index ) );
			}
		}
	}
		
	protected Mesh toMesh(
		int					pMeshIndex
	,	List<CSGPolygon>	pPolyList
	,	List<Vector3f> 		pPositionList
	,	List<Vector3f> 		pNormalList
	,	List<Vector2f> 		pTexCoordList
	,	List<Number> 		pIndexList
	) {
		Mesh aMesh = new Mesh();
		
		// Walk the list of all polygons, collecting all appropriate vertices
		int indexPtr = 0;
		for( CSGPolygon aPolygon : pPolyList ) {
			// Does this polygon have a custom material?
			int meshIndex = aPolygon.getMeshIndex();
			if ( (pMeshIndex >= 0) && (meshIndex != pMeshIndex) ) {
				// Only material-specific polygons are interesting
				continue;
			}
			List<CSGVertex> aVertexList = aPolygon.getVertices();
			int aVertexCount = aVertexList.size();
			
			// Include every vertex in this polygon
			List<Number> vertexPointers = new ArrayList<Number>( aVertexCount );
			for( CSGVertex aVertex : aVertexList ) {
				pPositionList.add( aVertex.getPositionFlt() );
				pNormalList.add( aVertex.getNormalFlt() );
				pTexCoordList.add( aVertex.getTextureCoordinate() );
				
				vertexPointers.add( indexPtr++ );
			}
			// Produce as many triangles (all starting from vertex 0) as needed to
			// include all the vertices  (3 yields 1 triangle, 4 yields 2 triangles, etc)
			for( int ptr = 2; ptr < aVertexCount; ptr += 1 ) {
				pIndexList.add( vertexPointers.get(0) );
				pIndexList.add( vertexPointers.get(ptr-1) );
				pIndexList.add( vertexPointers.get(ptr) );
			}
		}
		// Use our own buffer setters to optimize access and cut down on object churn
		aMesh.setBuffer( Type.Position, 3, CSGShape.createVector3Buffer( pPositionList ) );
		aMesh.setBuffer( Type.Normal, 3, CSGShape.createVector3Buffer( pNormalList ) );
		aMesh.setBuffer( Type.TexCoord, 2, CSGShape.createVector2Buffer( pTexCoordList ) );
		CSGShape.createIndexBuffer( pIndexList, aMesh );

		aMesh.updateBound();
		aMesh.updateCounts();
		
		return( aMesh );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGShapeBSPRevision
													, sCSGShapeBSPDate
													, pBuffer ) );
	}

}
