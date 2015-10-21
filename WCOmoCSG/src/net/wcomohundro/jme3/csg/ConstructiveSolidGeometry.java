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
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.wcomohundro.jme3.csg.bsp.CSGPartition;
import net.wcomohundro.jme3.csg.shape.*;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;

/** Constructive solid geometry (CSG) (formerly called computational binary solid geometry) is a 
 	technique used in 3D solid modeling. Constructive solid geometry allows a modeler to create 
 	a complex surface or object by using Boolean operators to combine objects. Often CSG presents 
 	a model or surface that appears visually complex, but is actually little more than cleverly 
 	combined or decombined objects
 	
 	It is said that an object is constructed from primitives by means of allowable operations, which 
 	are typically Boolean operations on sets: union, intersection and difference.
 	
 	Union - the merger (addition) of two objects into one
 	Intersection - the portion common to both objects
 	Difference - what is left when you remove one object from another
 	
 -- A Few Personal Notes on the History of this Code (2015)--
 
 	After retiring from the professional life of a software engineer for over four decades, I have the
 	opportunity to indulge in a few whims and can now investigate things like 3D graphics. Having 
 	experimented with Java since its initial release in 1995, and having been developing enterprise-level,
 	commercial java applications since before 2000, jMonkey struck me as perfect place to begin
 	playing around.
 	
 	As an engineer, not a graphics designer, the idea of hand-tooling meshes and textures via various
 	UI oriented drawing tools has no appeal.  I am a build-it-up-programmatically kind of person. So
 	when I went looking for the concept of blending shapes together in jMonkey, I came across CSG and
 	a sample BSP (binary space partitioning) implementation in Java for jMonkey.
 	
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
 	
 	Since simple shapes work just fine, I originally believed the core logic is proper.  So I 
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
 
 	While testing, I noticed I could get better results with fewer 'dropouts' if I carefully 
 	adjusted the tolerance of when a point was on-plane or not.  That explains the various
 	EPSILON values defined below.  I needed to set different tolerances for different 
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

 -- A Different Approach: Inside/Outside/Boundary --
 	While struggling with the BSP approach, I stumbled across a Java implementation of a non-BSP
 	algorithm based on the paper:
 		D. H. Laidlaw, W. B. Trumbore, and J. F. Hughes.  
 		"Constructive Solid Geometry for Polyhedral Objects" 
 		SIGGRAPH Proceedings, 1986, p.161. 

	Their approach is to first analyze all the faces in two objects that are being combined.  Any
	faces that intersect are broken into smaller pieces that do not intersect.  Every resultant face
	is then categorized as being Inside, Outside, or on the Boundary of the other object. The
	boolean operation is performed by selecting the appropriately categorized face from the two
	objects.
	
	Unlike BSP, the IOB approach is not recursive, but iterative.  However, it does involve 
	matching every face in one solid with every face in a second solid, so you dealing with
	M * N performance issues.
	
	A Java implementation of this algorithm was made open source by 
		Danilo Balby Silva Castanheira (danbalby@yahoo.com)
	It is based on the Java3D library, not jMonkey.  It has not been actively worked on for 
	several years, and I was unable to get it operational.
	
	Like the fabsterpal BSP code, I have reimplemented this code to run within the jMonkey
	environment.  Artifacts are no longer a problem, but the rendering differences between
	Java3D and jMonkey means that I have not yet gotten textures to operate properly.  But the
	shapes themselves look good in simple colors.
 	
 -- A Few Coding Conventions --
  	From a software engineering, long term maintenance perspective, I have ended up following a 
  	rather simple pattern of coding conventions across many projects.  In brief:
  	
  	1)	The code itself (with good names) tells you WHAT
  		Comments tell you WHY (and may explain a bit of HOW if the code is very complex)
  		
  	2)	A simple naming standard provides huge returns:
  		A)	As per standard Java, classes are capitalized, methods are not
  		B)	All parameters in method calls are named "pXxxxx"
  		C)	All instance variables are named "mXxxxxx"
  		D)	All static variables are named "sXxxxxx"
  		
  	3)	For older eyes, whitespace increases legibility tremendously
  
 -- The structure/packaging of this code --
  	I am attempting to keep this CSG code as an independent plugin with minimal changes within the
  	core JME as possible.  All CSG code is therefore in net.wcomohundro packages.
  	
  	Where simple changes in the core eliminated a lot of hoop-jumping, I have duplicated the
  	core JME code into the appropriate com.jme3 package within the CSG SVN repository. The changes are
  	all related to making the XML importer more robust.  I have found the Savable interface with its
  	XML support to be quite handy in my testing and development. The XML is very readable and easy
  	to manually edit to produce a wide range of test cases.  The core jme code changes
  	are related to allowing the Savable.read() process to accommodate more missing parameters, which
  	makes the .xml files more compact.
  	I have also created an XMLLoader option for the AssetManager which makes it easy to load 
  	.xml based definitions. XMLLoader can be included in the core jme package whenever the powers
  	that be see fit.  There is nothing about it related to CSG and can be used for any type of
  	loaded asset.
  	
  	CSGShape is the key player in defining the elements blended by CSG boolean operators.  It 
  	is connected to any arbitrary Mesh, created by any core jme process.
  	It was my original intent to leverage all the com.jme3.scene.shape classes.  But as I worked
  	through various tests, I found I wanted a more unified approach to the primitive shapes 
  	than what is offered by Box, Cylinder, etc.  I therefore have created CSG variants of the
  	primitive shapes, based in the package net.wcomo.jme3.csg.shape.  While the class structure/
  	inheritance tree is independent of the the core jme shapes, I shamelessly stole as much code
  	as I needed.  @see net.wcomohundro.jme3.csg.shape.CSGMesh   --   Oh the joys of open source.
  	
  	So you have two options -- leverage the jme core shapes and attach them to an instance of
  	CSGShape, or feel free to use any of the CSG defined primitives.
  	
  	Both the BSP and IOB algorithms are operational, and you can employ either process on 
  	a shape-by-shape basis.  @see CSGEnvironment for selecting configuration options that
  	control the processing.
  	
  	net.wcomohundro.jme3.csg - core/common CSG constructs and support
  	net.wcomohundro.jme3.csg.bsp - the BSP algorithm support (float or double based on config option)
  	net.wcomohundro.jme3.csg.iob - the IOB algorithm support (inherently double)
  	
  	net.wcomohundro.jme3.csg.shape - Box/Sphere/Pipe/Mesh support 
*/
public interface ConstructiveSolidGeometry 
{
	/** Version tracking support */
	public static final String sConstructiveSolidGeometryRevision="$Rev$";
	public static final String sConstructiveSolidGeometryDate="$Date$";

	
	/** ASSERT style debugging flag */
	public static final boolean DEBUG = false;
	
	/** Force polygons to simple triangles only */
	public static final boolean LIMIT_TO_TRIANGLES = false;
	
	/** Deepest split when processing BSP hierarchy */
	public static final int BSP_HIERARCHY_LIMIT = 1024;
	public static final int BSP_HIERARCHY_DEEP_LIMIT = 4096;
	
	/** When selecting the plane used to define a partition, use the Nth polygon */
	public static final double PARTITION_SEED_PLANE = 0.5;
	
	/** Define a 'tolerance' for when two items are so close, they are effectively the same */
	// Tolerance to decide if a given point in 'on' a plane
	public static final float EPSILON_ONPLANE_FLT = 1.0e-5f;
	public static final double EPSILON_ONPLANE_DBL = 1.0e-8;
	// Tolerance to determine if two points are close enough to be considered the same point
	public static final float EPSILON_BETWEEN_POINTS_FLT = 5.0e-7f;
	public static final double EPSILON_BETWEEN_POINTS_DBL = 1.0e-10;
	// Tolerance if a given value is near enough to zero to be treated as zero
	public static final float EPSILON_NEAR_ZERO_FLT = 5.0e-7f;
	public static final double EPSILON_NEAR_ZERO_DBL = 1.0e-10;
	
	// NOTE that '5E-15' may cause points on a plane to report problems.  In other words,
	//		when near_zero gets this small, the precision errors cause points on a plane to
	//		NOT look like they are on the plane, even when those points were used to create the
	//		plane.....
	
	
	/** Define a 'tolerance' for when two points are so far apart, it is ridiculous to consider it */
	public static final double EPSILON_BETWEEN_POINTS_MAX = 1e+3;
	
	/** Supported actions applied to the CSGShapes */
	public static enum CSGOperator
	{
		UNION
	,	DIFFERENCE
	,	INTERSECTION
	,	SKIP
	}
	
	/** Basic Spatial/Geometry interface:
	 		Define the services common across CSGGeometry and CSGGeonode
	 */
	public interface CSGSpatial {
		
	    /** Accessor to the Material (ala Geometry) */
	    public Material getMaterial();
	    public void setMaterial(
	    	Material 	pMaterial
	    );
	    
	    /** Accessor to the LOD level (ala Geometry) */
	    public int getLodLevel();
	    public void setLodLevel(
	    	int		pLODLevel
	    );

		/** Add a shape to this geometry */
		public void addShape(
			CSGShape	pShape
		,	CSGOperator	pOperator
		);
		
		/** Remove a shape from this geometry */
		public void removeShape(
			CSGShape	pShape
		);
		
		/** Action to generate the mesh based on the given shapes */
		public boolean regenerate();
		public boolean regenerate(
			CSGEnvironment		pEnvironment
		);

		/** Is this a valid geometry */
		public boolean isValid();
	}

	/** Version tracking support 
	 	(I plan to roll these numbers manually when something interesting happens) */
	public static final int sCSGVersionMajor = 0;
	public static final int sCSGVersionMinor = 9;
	// NOTE that $Rev$  and $Date$ are auto-filled by SVN processing based on SVN keywords 
	public static final String sCSGRevision="$Rev$";
	public static final String sCSGDate="$Date$";
	
	/** Have all CSG components report on their current version */
	public StringBuilder getVersion( StringBuilder pBuffer );
	
}
