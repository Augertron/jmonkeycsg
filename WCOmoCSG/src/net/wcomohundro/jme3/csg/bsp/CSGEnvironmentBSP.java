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

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGPolygon;
import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

public class CSGEnvironmentBSP 
	extends CSGEnvironment<CSGShapeBSP>
{
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
	
	// Limits on points
	public static final float EPSILON_MAX_POINT_POSITION = 10000f;
	public static final float EPSILON_MAX_POINT_TEXTURE = 10000f;

	// Tolerance of difference between magnitudes between two doubles
	public static final int EPSILON_MAGNITUDE_RANGE = 22;

	
	// NOTE that '5E-15' may cause points on a plane to report problems.  In other words,
	//		when near_zero gets this small, the precision errors cause points on a plane to
	//		NOT look like they are on the plane, even when those points were used to create the
	//		plane.....

	

	//////////////////////////////// BSP SPECIFIC PROCESSING ///////////////////////////////
	/** Maximum depth allowed on BSP recursion processing */
	public int			mBSPLimit;
	/** Limit a polygon to be a triangle */
	public boolean		mPolygonTriangleOnly;
	/** How to process a polygon and its plane */
	public CSGPolygon.CSGPolygonPlaneMode	mPolygonPlaneMode;
	
	/** Which polygon's plane is used to seed a partition */
	public double		mPartitionSeedPlane;

	
	/** Null constructor produces the 'standard' */
	public CSGEnvironmentBSP(
	) {
		this( false );
	}
	public CSGEnvironmentBSP(
		boolean		pDoublePrecision
	) {
		super( pDoublePrecision
		,	CSGShapeBSP.class
		
		,	EPSILON_NEAR_ZERO_DBL
		,	EPSILON_BETWEEN_POINTS_DBL
		,	EPSILON_ONPLANE_DBL
		
		,	EPSILON_NEAR_ZERO_FLT
		,	EPSILON_BETWEEN_POINTS_FLT
		,	EPSILON_ONPLANE_FLT
		
		,	EPSILON_MAX_POINT_POSITION
		,	EPSILON_MAX_POINT_TEXTURE
		
		,	false
		,	EPSILON_MAGNITUDE_RANGE );

		mBSPLimit = (mDoublePrecision) ? BSP_HIERARCHY_DEEP_LIMIT : BSP_HIERARCHY_LIMIT;
				
		mPolygonTriangleOnly = LIMIT_TO_TRIANGLES;
		mPolygonPlaneMode = CSGPolygonPlaneMode.USE_GIVEN;

		mPartitionSeedPlane = PARTITION_SEED_PLANE;
	}
	
	/** Constructor based on final configuration */
	public CSGEnvironmentBSP(
		String							pShapeName
	,	boolean							pStructuralDebug
	,	boolean							pDoublePrecision
	,	Class							pShapeClass
	,	int								pBSPLimit
	,	double							pEpsilonNearZero
	,	double							pEpsilonOnPlane
	,	double							pEpsilonBetweenPoints
	,	double							pEpsilonMaxBetweenPoints
	,	int								pEpsilonMagnitudeRange
	,	boolean							pPolygonTriangleOnly
	,	CSGPolygon.CSGPolygonPlaneMode	pPolygonPlaneMode
	,	double							pPartitionSeedPlane
	) {
		this( pDoublePrecision );
		
		mShape = null;
		mRationalizeValues = true;
		mStructuralDebug = pStructuralDebug;
		mShapeClass = pShapeClass;
		
		mBSPLimit = pBSPLimit;
		
		if ( mDoublePrecision ) {
			mEpsilonNearZeroDbl = pEpsilonNearZero;
			mEpsilonOnPlaneDbl = pEpsilonOnPlane;		
			mEpsilonBetweenPointsDbl = pEpsilonBetweenPoints;
		} else {
			mEpsilonNearZeroFlt = (float)pEpsilonNearZero;
			mEpsilonOnPlaneFlt = (float)pEpsilonOnPlane;		
			mEpsilonBetweenPointsFlt = (float)pEpsilonBetweenPoints;
		}
		mEpsilonMagnitudeRange = pEpsilonMagnitudeRange;
		
		mPolygonTriangleOnly = pPolygonTriangleOnly;
		mPolygonPlaneMode = pPolygonPlaneMode;
		
		mPartitionSeedPlane = pPartitionSeedPlane;
	}
	
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		super.write( pExporter );
		
		// Save the configuration options
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		
		if ( mDoublePrecision ) {
			aCapsule.write( mEpsilonNearZeroDbl, "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			aCapsule.write( mEpsilonBetweenPointsDbl, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			aCapsule.write( mEpsilonOnPlaneDbl, "epsilonOnPlane", EPSILON_ONPLANE_DBL );
		} else {
			aCapsule.write( mEpsilonNearZeroFlt, "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			aCapsule.write( mEpsilonBetweenPointsFlt, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			aCapsule.write( mEpsilonOnPlaneFlt, "epsilonOnPlane", EPSILON_ONPLANE_FLT );
		}
		aCapsule.write( mEpsilonMaxPointPositionFlt, "epsilonMaxPointPosition", EPSILON_MAX_POINT_POSITION );
		aCapsule.write( mEpsilonMaxPointTextureFlt, "epsilonMaxPointTexture", EPSILON_MAX_POINT_TEXTURE );

		aCapsule.write( mEpsilonMagnitudeRange, "epsilonMagnitudeRange", EPSILON_MAGNITUDE_RANGE );

		if ( mDoublePrecision ) {
			aCapsule.write( mBSPLimit, "bspLimit", BSP_HIERARCHY_DEEP_LIMIT );
		} else {
			aCapsule.write( mBSPLimit, "bspLimit", BSP_HIERARCHY_LIMIT );
		}
		aCapsule.write( mPolygonTriangleOnly, "polygonTriangleOnly", LIMIT_TO_TRIANGLES );
		aCapsule.write( mPolygonPlaneMode, "polygonPlaneMode", CSGPolygonPlaneMode.USE_GIVEN );
		
		aCapsule.write( mPartitionSeedPlane, "partitionSeedPlane", PARTITION_SEED_PLANE );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		super.read( pImporter );
		
		InputCapsule aCapsule = pImporter.getCapsule( this );
		if ( mDoublePrecision ) {
			mEpsilonNearZeroDbl = aCapsule.readDouble( "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			mEpsilonBetweenPointsDbl = aCapsule.readDouble( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			mEpsilonOnPlaneDbl = aCapsule.readDouble( "epsilonOnPlane", EPSILON_ONPLANE_DBL );
		} else {
			mEpsilonNearZeroFlt = aCapsule.readFloat( "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			mEpsilonBetweenPointsFlt = aCapsule.readFloat( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			mEpsilonOnPlaneFlt = aCapsule.readFloat( "epsilonOnPlane", EPSILON_ONPLANE_FLT );
		}
		mEpsilonMaxPointPositionFlt = aCapsule.readFloat( "epsilonMaxPointPosition", EPSILON_MAX_POINT_POSITION );
		mEpsilonMaxPointTextureFlt = aCapsule.readFloat( "epsilonMaxPointTexture", EPSILON_MAX_POINT_TEXTURE );
		
		mEpsilonMagnitudeRange = aCapsule.readInt( "epsilonMagnitudeRange", EPSILON_MAGNITUDE_RANGE );

		if ( mDoublePrecision ) {
			mBSPLimit = aCapsule.readInt( "bspLimit", BSP_HIERARCHY_DEEP_LIMIT );
		} else {
			mBSPLimit = aCapsule.readInt( "bspLimit", BSP_HIERARCHY_LIMIT );
		}
		mPolygonTriangleOnly = aCapsule.readBoolean( "polygonTriangleOnly", LIMIT_TO_TRIANGLES );
		mPolygonPlaneMode = aCapsule.readEnum( "polygonPlaneMode", CSGPolygonPlaneMode.class, CSGPolygonPlaneMode.USE_GIVEN );

		mPartitionSeedPlane = aCapsule.readDouble( "partitionSeedPlane", PARTITION_SEED_PLANE );	
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder pBuffer
	) {
		StringBuilder aBuffer = super.getVersion( pBuffer );
		aBuffer.append( "\tBSPLimit: " ).append( this.mBSPLimit ).append( "\n" );
		aBuffer.append( "\tPolygon Plane Mode: " ).append( this.mPolygonPlaneMode ).append( "\n" );
		return( aBuffer );
	}

}
