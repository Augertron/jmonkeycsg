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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.util.TempVars;

/**  Constructive Solid Geometry (CSG)

  	ACSGPolygon represents a flat geometric shape defined by a set of vertices in a given plane.
  	
 	A CSGPolygon is assumed to be immutable once created, which means that its internal
 	structure is never changed.  This means that a clone of a polygon just uses the
 	original instance.
 	
 	A bit of history -- CSGPolygon was initially created to handle any set of points so long
 	as they were within the same plane. Obviously, three vertices is the minimum required, but
 	when splitting a polygon across a plane, a simple triangle can be transformed into four
 	points.
 	
 	But while searching for the problem of dropped and misplaces shapes, I became concerned that
 	I was possibly creating a concave polygon in some fashion.  To eliminate that problem, 
 	I have decided to support the option of restricting CSGPolygon to simple triangles.
 */
public abstract class CSGPolygon<VertexT extends CSGVertex, PlaneT extends CSGPlane>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPolygonRevision="$Rev$";
	public static final String sCSGPolygonDate="$Date$";

	
	/** Supported actions applied to the CSGShapes */
	public static enum CSGPolygonPlaneMode
	{
		USE_GIVEN			// Construct polygon to reference the given plane
	,	FROM_VERTICES		// Compute plane from the given vertices
	,	FORCE_TO_PLANE		// Force all vertices to intersect with the given plane
	}

	/** Factory level service routine to construct appropriate polygons */
	public static int addPolygon(
		List<CSGPolygon>	pPolyList
	,	CSGVertex[]			pVertices
	,	int					pMaterialIndex
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
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

	/** The vertices that define this shape */
	protected List<CSGVertex>	mVertices;
	/** The plane that contains this shape */
	protected PlaneT			mPlane;
	/** The custom material that applies to this shape */
	protected int				mMaterialIndex;

	/** Ensure we are working with something valid */
	public boolean isValid() { return( mPlane != null ); }
	
	/** Make a copy */
	public abstract CSGPolygon clone(
		boolean 	pFlipIt
	);
	
	/** Accessor to the vertices */
	public List<CSGVertex> getVertices() { return( this.isValid() ? mVertices : Collections.EMPTY_LIST ); }
	
	/** Accessor to the plane */
	public PlaneT getPlane() { return mPlane; }
	
	/** Accessor to the custom material index */
	public int getMaterialIndex() { return mMaterialIndex; }
	

	/** Make it 'savable' */
	@Override
	public void write(
		JmeExporter 	pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule (this );	
		aCapsule.write( mPlane, "plane", null );
	}
	@Override
	public void read(
		JmeImporter 	pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mPlane = (PlaneT)aCapsule.readSavable( "plane", null );
	}
	
	/** For DEBUG */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mVertices.size() + "(" + mPlane + ")" );
	}

}
