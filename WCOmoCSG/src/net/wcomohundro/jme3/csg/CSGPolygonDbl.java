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

  	CSGPolygon variant based on DOUBLES
 */
public class CSGPolygonDbl 
	extends CSGPolygon<CSGVertexDbl,CSGPlaneDbl>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPolygonDblRevision="$Rev$";
	public static final String sCSGPolygonDblDate="$Date$";

	/** Standard null constructor */
	public CSGPolygonDbl(
	) {
		mVertices = CSGVertex.sEmptyVertices;
		mPlane = null;
		mMaterialIndex = 0;
	}
	
	/** Constructor based on given vertices and plane */
	public CSGPolygonDbl(
		List<CSGVertex>		pVertices
	,	CSGPlaneDbl			pPlane
	,	int					pMaterialIndex
	) {
		mVertices = pVertices;
		
		if ( (pPlane != null) && pPlane.isValid() ) {
			mPlane = pPlane;
		}
		mMaterialIndex = pMaterialIndex;
	}
	public CSGPolygonDbl(
		CSGVertex[]			pVertices
	,	CSGPlaneDbl			pPlane
	,	int					pMaterialIndex
	) {
		mVertices = Arrays.asList( pVertices );
		mPlane = pPlane;
		mMaterialIndex = pMaterialIndex;
	}
	
	/** Make a copy */
	@Override
	public CSGPolygonDbl clone(
		boolean 	pFlipIt
	) {
		if ( pFlipIt ) {
			// Flip all the components
			List<CSGVertex> newVertices = new ArrayList<CSGVertex>( mVertices.size() );
			for( CSGVertex aVertex : mVertices ) {
				CSGVertexDbl bVertex = ((CSGVertexDbl)aVertex).clone( pFlipIt );
				newVertices.add( bVertex );
			}
			// Flip the order of the vertices as well
			// NOTE that we are assuming that .reverse() is more efficient than repeatedly inserting
			//		new items at the start of the list (which forces things to copy and slide)
			Collections.reverse( newVertices );
			return( new CSGPolygonDbl( newVertices, mPlane.clone( pFlipIt ), mMaterialIndex ) );
		} else {
			// The polygon is immutable, so its clone is just itself
			return( this );
		}
	}
	
	/** Make it 'savable' */
	@Override
	public void write(
		JmeExporter 	pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule (this );	
		
		// NOTE a deficiency in the OutputCapsule API which should operate on a List,
		//		but instead requires an ArrayList
		aCapsule.writeSavableArrayList( (ArrayList)mVertices
										, "vertices"
										, (ArrayList)CSGVertex.sEmptyVertices );
		super.write( pExporter );
	}
	@Override
	public void read(
		JmeImporter 	pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mVertices = (List<CSGVertex>)aCapsule.readSavableArrayList( "vertices"
														, (ArrayList)CSGVertex.sEmptyVertices );
		super.read( pImporter );
	}
	

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGPolygonDblRevision
													, sCSGPolygonDblDate
													, pBuffer ) );
	}

}
