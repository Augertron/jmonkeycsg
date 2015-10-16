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
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.scene.Mesh;

/** Define the operational environment to apply to the CSG processing 
 	An instance of this class acts as container for various public configuration parameters.
 	
 	As I have chased after the 'artifacts' problem, I found it desirable to alter these
 	parameters from a case-to-case basis, all within the same test run.  Therefore, the
 	generation/rendering interfaces have been expanded to accept a per test case, unique
 	definition of CSGEnvironment, defaulting back to a system standard if none is supplied.
 	
 	It is possible, therefore, to configure the CSGEnvironment via the XML load mechanism.
 */
public class CSGEnvironment 
	implements ConstructiveSolidGeometry, Savable 
{
	/** Version tracking support */
	public static final String sCSGEnvironmentRevision="$Rev$";
	public static final String sCSGEnvironmentDate="$Date$";
	
	/** Standard configuration 
	 	NOTE
	 		we do NOT declare this global as 'final' so that the default can be dynamically
	 		modified at run time
	 */
	public static CSGEnvironment sStandardEnvironment = new CSGEnvironment();

	
	/** Shape this environment applies to */
	public String		mShapeName;
	/** Is structural debug on or off */
	public boolean		mStructuralDebug;
	/** Control flag to process in double precision */
	public boolean 		mDoublePrecision;
	
	/** EPSILON - near to zero */
	public double		mEpsilonNearZeroDbl;
	public float		mEpsilonNearZeroFlt;
	/** EPSILON - distance to plane */
	public double		mEpsilonOnPlaneDbl;
	public float		mEpsilonOnPlaneFlt;
	/** EPSILON - meaningful minimal distance between points */
	public double		mEpsilonBetweenPointsDbl;
	public float		mEpsilonBetweenPointsFlt;
	/** EPSILON - meaningful maximal distance between points */
	public double		mEpsilonMaxBetweenPoints;

	/** Type of 'shape processor' to operate with */
	public Class		mShapeClass;

	//////////////////////////////// BSP SPECIFIC PROCESSING ///////////////////////////////
	/** Maximum depth allowed on BSP recursion processing */
	public int			mBSPLimit;
	/** Limit a polygon to be a triangle */
	public boolean		mPolygonTriangleOnly;
	/** How to process a polygon and its plane */
	public CSGPolygon.CSGPolygonPlaneMode	mPolygonPlaneMode;
	
	/** Which polygon's plane is used to seed a partition */
	public double		mPartitionSeedPlane;
	
	
	/** Null constructor produces the 'standards' */
	public CSGEnvironment(
	) {
		this( true, "net.wcomohundro.jme3.csg.iob.CSGShapeIOB" );
	}
	public CSGEnvironment(
		boolean		pDoublePrecision
	,	String		pHandlerClassName
	) {
		mShapeName = "";
		mDoublePrecision = pDoublePrecision;
		try {
			mShapeClass = Class.forName( pHandlerClassName );
		} catch( ClassNotFoundException ex ) {
			ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "No Handler Class:" + ex, ex );
		}
		mStructuralDebug = DEBUG;
		mBSPLimit = (mDoublePrecision) ? BSP_HIERARCHY_DEEP_LIMIT : BSP_HIERARCHY_LIMIT;
		
		mEpsilonNearZeroDbl = EPSILON_NEAR_ZERO_DBL;
		mEpsilonNearZeroFlt = EPSILON_NEAR_ZERO_FLT;
		
		mEpsilonBetweenPointsDbl = EPSILON_BETWEEN_POINTS_DBL;
		mEpsilonBetweenPointsFlt = EPSILON_BETWEEN_POINTS_FLT;
		
		mEpsilonOnPlaneDbl = EPSILON_ONPLANE_DBL;
		mEpsilonOnPlaneFlt = EPSILON_ONPLANE_FLT;
		
		mEpsilonMaxBetweenPoints = EPSILON_BETWEEN_POINTS_MAX;
		
		mPolygonTriangleOnly = LIMIT_TO_TRIANGLES;
		mPolygonPlaneMode = CSGPolygonPlaneMode.USE_GIVEN;

		mPartitionSeedPlane = PARTITION_SEED_PLANE;
	}
	
	/** Constructor based on final configuration */
	public CSGEnvironment(
		String							pShapeName
	,	boolean							pStructuralDebug
	,	boolean							pDoublePrecision
	,	Class							pShapeClass
	,	int								pBSPLimit
	,	double							pEpsilonNearZero
	,	double							pEpsilonOnPlane
	,	double							pEpsilonBetweenPoints
	,	double							pEpsilonMaxBetweenPoints
	,	boolean							pPolygonTriangleOnly
	,	CSGPolygon.CSGPolygonPlaneMode	pPolygonPlaneMode
	,	double							pPartitionSeedPlane
	) {
		mShapeName = pShapeName;
		
		mDoublePrecision = pDoublePrecision;
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
		mEpsilonMaxBetweenPoints = pEpsilonMaxBetweenPoints;
		
		mPolygonTriangleOnly = pPolygonTriangleOnly;
		mPolygonPlaneMode = pPolygonPlaneMode;
		
		mPartitionSeedPlane = pPartitionSeedPlane;
	}
	
	/** Build an appropriate shape processor */
	public CSGShape.CSGShapeProcessor resolveShapeProcessor(
	) {
		CSGShape.CSGShapeProcessor aHandler = null;
		if ( mShapeClass != null ) try {
			aHandler = (CSGShape.CSGShapeProcessor)mShapeClass.newInstance();
		} catch( Exception ex ) {
			sLogger.log( Level.SEVERE, "Failed to create handler: " + ex, ex );
		}
		return( aHandler );
	}
	
	/** Support the persistence of this Environment */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Save the configuration options
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mDoublePrecision, "doublePrecision", false );
		aCapsule.write( mStructuralDebug, "structuralDebug", false );
		String shapeClassName = (mDoublePrecision)
								?	"net.wcomohundro.jme3.csg.iob.CSGShapeIOB"
								:	"net.wcomohundro.jme3.csg.bsp.CSGShapeBSP";
		aCapsule.write( mShapeClass.getName(), "shapeClass", shapeClassName );
		

		if ( mDoublePrecision ) {
			aCapsule.write( mEpsilonNearZeroDbl, "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			aCapsule.write( mEpsilonBetweenPointsDbl, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			aCapsule.write( mEpsilonOnPlaneDbl, "epsilonOnPlane", EPSILON_ONPLANE_DBL );
			
			aCapsule.write( mBSPLimit, "bspLimit", BSP_HIERARCHY_DEEP_LIMIT );
		} else {
			aCapsule.write( mEpsilonNearZeroFlt, "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			aCapsule.write( mEpsilonBetweenPointsFlt, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			aCapsule.write( mEpsilonOnPlaneFlt, "epsilonOnPlane", EPSILON_ONPLANE_FLT );

			aCapsule.write( mBSPLimit, "bspLimit", BSP_HIERARCHY_LIMIT );
		}
		aCapsule.write( mEpsilonMaxBetweenPoints, "epsilonMaxBetweenPoints", EPSILON_BETWEEN_POINTS_MAX );
		
		aCapsule.write( mPolygonTriangleOnly, "polygonTriangleOnly", LIMIT_TO_TRIANGLES );
		aCapsule.write( mPolygonPlaneMode, "polygonPlaneMode", CSGPolygonPlaneMode.USE_GIVEN );
		
		aCapsule.write( mPartitionSeedPlane, "partitionSeedPlane", PARTITION_SEED_PLANE );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mDoublePrecision = aCapsule.readBoolean( "doublePrecision", true );
		mStructuralDebug = aCapsule.readBoolean( "structuralDebug", DEBUG );
		
		String shapeClassName = (mDoublePrecision)
								?	"net.wcomohundro.jme3.csg.iob.CSGShapeIOB"
								:	"net.wcomohundro.jme3.csg.bsp.CSGShapeBSP";
		shapeClassName = aCapsule.readString( "shapeClass", shapeClassName );
		try {
			mShapeClass = Class.forName( shapeClassName );
		} catch( ClassNotFoundException ex ) {
			sLogger.log( Level.SEVERE, "Invalid ShapeHandler: " + ex, ex );
		}
		
		if ( mDoublePrecision ) {
			mEpsilonNearZeroDbl = aCapsule.readDouble( "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			mEpsilonBetweenPointsDbl = aCapsule.readDouble( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			mEpsilonOnPlaneDbl = aCapsule.readDouble( "epsilonOnPlane", EPSILON_ONPLANE_DBL );
			
			mBSPLimit = aCapsule.readInt( "bspLimit", BSP_HIERARCHY_DEEP_LIMIT );
		} else {
			mEpsilonNearZeroFlt = aCapsule.readFloat( "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			mEpsilonBetweenPointsFlt = aCapsule.readFloat( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			mEpsilonOnPlaneFlt = aCapsule.readFloat( "epsilonOnPlane", EPSILON_ONPLANE_FLT );
			
			mBSPLimit = aCapsule.readInt( "bspLimit", BSP_HIERARCHY_LIMIT );
		}
		mEpsilonMaxBetweenPoints = aCapsule.readDouble( "epsilonMaxBetweenPoints", EPSILON_BETWEEN_POINTS_MAX );
		
		mPolygonTriangleOnly = aCapsule.readBoolean( "polygonTriangleOnly", LIMIT_TO_TRIANGLES );
		mPolygonPlaneMode = aCapsule.readEnum( "polygonPlaneMode", CSGPolygonPlaneMode.class, CSGPolygonPlaneMode.USE_GIVEN );

		mPartitionSeedPlane = aCapsule.readDouble( "partitionSeedPlane", PARTITION_SEED_PLANE );	
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
		aBuffer.append( "\tDouble Precision: " ).append( this.mDoublePrecision ).append( "\n" );
		aBuffer.append( "\tStructural Debug: " ).append( this.mStructuralDebug ).append( "\n" );
		aBuffer.append( "\tBSPLimit: " ).append( this.mBSPLimit ).append( "\n" );
		if ( this.mDoublePrecision ) {
			aBuffer.append( "\tEpsilon Near Zero: " ).append( this.mEpsilonNearZeroDbl ).append( "\n" );
			aBuffer.append( "\tEpsilon On Plane: " ).append( this.mEpsilonOnPlaneDbl ).append( "\n" );
			aBuffer.append( "\tEpsilon Between Points: " ).append( this.mEpsilonBetweenPointsDbl ).append( "\n" );
		} else {
			aBuffer.append( "\tEpsilon Near Zero: " ).append( this.mEpsilonNearZeroFlt ).append( "\n" );
			aBuffer.append( "\tEpsilon On Plane: " ).append( this.mEpsilonOnPlaneFlt ).append( "\n" );
			aBuffer.append( "\tEpsilon Between Points: " ).append( this.mEpsilonBetweenPointsFlt ).append( "\n" );
		}
		aBuffer.append( "\tPolygon Plane Mode: " ).append( this.mPolygonPlaneMode ).append( "\n" );
		return( aBuffer );
	}

}
