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
 	a sample BSP (binary space partitioning) implementation in Java for jMonkey:
 		@see net.wcomohundro.jme3.csg.bsp.CSGShapeBSP 
 	
 	After encountering problems with 'artifacts' with the BSP algorithm, I went searching again
 	and found a different approach:
 		@see net.wcomohundro.jme3.csg.iob.CSGShapeIOB
 		
 	Both processes are still available, and can be selected by an appropriate configuration
 	change to CSGEnvironment.  By default, the IOB algorithm is used.
 	
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
  	
  	net.wcomohundro.jme3.csg - core/common CSG constructs and support
  	net.wcomohundro.jme3.csg.bsp - the BSP algorithm support (float or double based on config option)
  	net.wcomohundro.jme3.csg.iob - the IOB algorithm support (inherently double)
  	
  	net.wcomohundro.jme3.csg.shape - Box/Sphere/Pipe/Mesh support 
  	
  	FYI - earlier versions of this code required Java 1.8 and its support of static methods
  		  defined within interfaces.  All CSG code has been retrofitted back to Java 1.7 
  		  to better with with the mainline development of jMonkey.
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
	public static final double EPSILON_ONPLANE_DBL = 1.0e-7;
	// Tolerance to determine if two points are close enough to be considered the same point
	public static final float EPSILON_BETWEEN_POINTS_FLT = 5.0e-7f;
	public static final double EPSILON_BETWEEN_POINTS_DBL = 1.0e-8;
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
		
	    /** Accessor to the Material (ala Geometry) 
	     		NOTE that setMaterial() is defined by Spatial
	     */
	    public Material getMaterial();
	    
	    /** Special provisional setMaterial() that does NOT override anything 
	     	already in force, but supplies a default if any element is missing 
	     	a material
	     */
	    public void setDefaultMaterial(
	    	Material	pMaterial
	    );
	    
	    /** Accessor to the LOD level (ala Geometry) 
	     		NOTE that setLodLevel() is defined by Spatial
	     */
	    public int getLodLevel();

	    
	    /** Include a shape */
	    public void addShape(
	    	CSGShape	pShape
	    );
	    public void subtractShape(
		    CSGShape	pShape
		);
	    public void intersectShape(
		    CSGShape	pShape
		);

		/** Add a shape to this geometry with a given boolean operator */
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
		
		/** How long did it take to build this shape */
		public long getShapeRegenerationNS();
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
