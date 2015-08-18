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
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.scene.Mesh;

/** Define the operational environment to apply to the CSG processing 
 	An instance of this class acts as container for various public configuration parameters.
 */
public class CSGEnvironment 
	implements ConstructiveSolidGeometry, Savable 
{
	/** Version tracking support */
	public static final String sCSGEnvironmentRevision="$Rev: 66 $";
	public static final String sCSGEnvironmentDate="$Date: 2015-08-12 20:18:21 -0500 (Wed, 12 Aug 2015) $";
	
	/** Standard configuration */
	public static final CSGEnvironment sStandardEnvironment = new CSGEnvironment();

	/** Shape this environment applies to */
	public String		mShapeName;
	/** Is structural debug on or off */
	public boolean		mStructuralDebug;
	/** Maximum depth allowed on BSP recursion processing */
	public int			mBSPLimit;
	
	/** EPSILON - near to zero */
	public float		mEpsilonNearZero;
	/** EPSILON - distance to plane */
	public float		mEpsilonOnPlane;
	/** EPSILON - meaningful minimal distance between points */
	public float		mEpsilonBetweenPoints;
	/** EPSILON - meaningful maximal distance between points */
	public float		mEpsilonMaxBetweenPoints;
	
	/** How to process a polygon and its plane */
	public CSGPolygon.CSGPolygonPlaneMode	mPolygonPlaneMode;
	
	
	/** Null constructor produces the 'standards' */
	public CSGEnvironment(
	) {
		mShapeName = "";
		
		mStructuralDebug = DEBUG;
		mBSPLimit = BSP_HIERARCHY_LIMIT;
		
		mEpsilonNearZero = EPSILON_NEAR_ZERO;
		mEpsilonOnPlane = EPSILON_ONPLANE;
		mEpsilonBetweenPoints = EPSILON_BETWEEN_POINTS;
		mEpsilonMaxBetweenPoints = EPSILON_BETWEEN_POINTS_MAX;
		
		mPolygonPlaneMode = CSGPolygonPlaneMode.USE_GIVEN;
	}
	/** Constructor based on final configuration */
	public CSGEnvironment(
		String							pShapeName
	,	boolean							pStructuralDebug
	,	int								pBSPLimit
	,	float							pEpsilonNearZero
	,	float							pEpsilonOnPlane
	,	float							pEpsilonBetweenPoints
	,	float							pEpsilonMaxBetweenPoints
	,	CSGPolygon.CSGPolygonPlaneMode	pPolygonPlaneMode
	) {
		mShapeName = pShapeName;
		
		mStructuralDebug = pStructuralDebug;
		mBSPLimit = pBSPLimit;
		
		mEpsilonNearZero = pEpsilonNearZero;
		mEpsilonOnPlane = pEpsilonOnPlane;		
		mEpsilonBetweenPoints = pEpsilonBetweenPoints;
		mEpsilonMaxBetweenPoints = pEpsilonMaxBetweenPoints;
		
		mPolygonPlaneMode = pPolygonPlaneMode;
	}
	
	/** Support the persistence of this Environment */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Save the configuration options
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mStructuralDebug, "structuralDebug", false );
		aCapsule.write( mBSPLimit, "bspLimit", BSP_HIERARCHY_LIMIT );
		
		aCapsule.write( mEpsilonNearZero, "epsilonNearZero", EPSILON_NEAR_ZERO );
		aCapsule.write( mEpsilonOnPlane, "epsilonOnPlane", EPSILON_ONPLANE );
		aCapsule.write( mEpsilonBetweenPoints, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS );
		aCapsule.write( mEpsilonMaxBetweenPoints, "epsilonMaxBetweenPoints", EPSILON_BETWEEN_POINTS_MAX );
		
		aCapsule.write( mPolygonPlaneMode, "polygonPlaneMode", CSGPolygonPlaneMode.USE_GIVEN );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mStructuralDebug = aCapsule.readBoolean( "structuralDebug", false );
		mBSPLimit = aCapsule.readInt( "bspLimit", BSP_HIERARCHY_LIMIT );
		
		mEpsilonNearZero = aCapsule.readFloat( "epsilonNearZero", EPSILON_NEAR_ZERO );
		mEpsilonOnPlane = aCapsule.readFloat( "epsilonOnPlane", EPSILON_ONPLANE );
		mEpsilonBetweenPoints = aCapsule.readFloat( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS );
		mEpsilonMaxBetweenPoints = aCapsule.readFloat( "epsilonMaxBetweenPoints", EPSILON_BETWEEN_POINTS_MAX );
		
		mPolygonPlaneMode = aCapsule.readEnum( "polygonPlaneMode", CSGPolygonPlaneMode.class, CSGPolygonPlaneMode.USE_GIVEN );

	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder pBuffer
	) {
		StringBuilder aBuffer = ConstructiveSolidGeometry.getVersion( this.getClass()
																	, sCSGEnvironmentRevision
																	, sCSGEnvironmentDate
																	, pBuffer );
		aBuffer.append( "\tStructural Debug: " ).append( this.mStructuralDebug ).append( "\n" );
		aBuffer.append( "\tBSPLimit: " ).append( this.mBSPLimit ).append( "\n" );
		aBuffer.append( "\tEpsilon Near Zero: " ).append( this.mEpsilonNearZero ).append( "\n" );
		aBuffer.append( "\tEpsilon On Plane: " ).append( this.mEpsilonOnPlane ).append( "\n" );
		aBuffer.append( "\tEpsilon Between Points: " ).append( this.mEpsilonBetweenPoints ).append( "\n" );
		aBuffer.append( "\tPolygon Plane Mode: " ).append( this.mPolygonPlaneMode ).append( "\n" );
		return( aBuffer );
	}

}
