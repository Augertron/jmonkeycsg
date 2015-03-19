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
 	commercial java applications for over fifteen years, jMonkey struck me as perfect place to begin
 	playing around.
 	
 	As an engineer, not a graphics designer, the idea of hand-tooling meshes and textures via various
 	UI oriented drawing tools has no appeal.  I am a build-it-up-programmatically kind of person. So
 	when I went looking for the concept of blending shapes together in jMonkey, I came across CSG and
 	a sample implementation in Java for jMonkey.
 	
 	As far as I can tell, Evan Wallace put together a Javascript library for CSG support within 
 	browsers that support WebGL.  @see http://evanw.github.io/csg.js  (it is really quite impressive)
 	This was converted into jMonkey compatible Java by "fabsterpal", and posted to a Github repository
 	by "andychase".  Everything seems to have been properly posted and annotated for fully open 
 	source.
 	
 	While working with the "fabsterpal" code, I tripped over some bugs and encountered many spots
 	where I wished for a deeper level of comments.  My personal learning style is to work from an
 	operational example.  From there, I can make small, incremental changes to help we understand
 	the larger picture.  To that end, I reimplemented the Java code, following my own
 	conventions and structures.  But the logic and algorithms are all based directly on what I found
 	in the original Javascript/Java code.
 	
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
  	core JME as possible.  All CSG code is therefore in net.wcomo packages.
  	
  	Where simple changes in the core eliminated a lot of hoop-jumping, I have duplicated the
  	core JME code into the appropriate com.jme3 package within the CSG SVN repository. The changes are
  	all related to making the XML importer more robust.  I have found the Savable interface with its
  	XML support to be quite handy in my testing and development. The XML is very readible and easy
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
  	as I needed.  @see net.wcomo.jme3.csg.shape.CSGMesh   --   Oh the joys of open source.
  	
  	So you have two options -- leverage the jme core shapes and attach them to an instance of
  	CSGShape, or feel free to use any of the CSG defined primitives.
  	
*/
public interface ConstructiveSolidGeometry 
{
	/** Supported actions applied to the CSGShapes */
	public static enum CSGOperator
	{
		UNION
	,	DIFFERENCE
	,	INTERSECTION
	,	SKIP
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
	
	/** Service routine to report on the global CSG version */
	public static StringBuilder getVersion(
		Class			pCSGClass
	,	StringBuilder	pBuffer
	) {
		return( getVersion( pCSGClass, sCSGVersionMajor, sCSGVersionMinor, sCSGRevision, sCSGDate, pBuffer ) );
	}
	public static StringBuilder getVersion(
		Class			pCSGClass
	,	String			pRevision
	,	String			pDate
	,	StringBuilder	pBuffer
	) {
		return( getVersion( pCSGClass, sCSGVersionMajor, sCSGVersionMinor, pRevision, pDate, pBuffer ) );
	}

	public static StringBuilder getVersion(
		Class			pCSGClass
	,	int				pMajor
	,	int				pMinor
	,	String			pRevision
	,	String			pDate
	,	StringBuilder	pBuffer
	) {
		if ( pBuffer == null ) pBuffer = new StringBuilder( 256 );
		pBuffer.append( pCSGClass.getSimpleName() ).append( ": " );
		pBuffer.append( "v" ).append( pMajor ).append( "." ).append( pMinor );
		if ( pRevision != null ) pBuffer.append( ", rev: " ).append( pRevision );
		if ( pDate != null ) pBuffer.append( ", date: " ).append( pDate );
		pBuffer.append( "\n" );
		return( pBuffer );
	}
}
