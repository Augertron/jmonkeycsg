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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;

/** Constructive Solid Geometry (CSG)
	
	A CSG Vertex is a point in space, know by its position, its normal, and its texture coordinate.
	While the standard jme3 processing is all based on Vector3f and floats, I have extended the
	internal processing to optionally work from underlying Vector3d and doubles.  This allows me
	to persue some of the 'artifacts' issues with a finer level of detail.  The end resultant 
	mesh, however, always renders via floats, not doubles.
	@see CSGVertexFlt and CSGVertexDbl for float/double implementation.

	NOTE
		that a vertex is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its attributes.  
		THEREFORE, be careful with Vertex internal Vectors used in Mesh construction which could be 
		adjusted/altered. .
  */
public abstract class CSGVertex<VectorT>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexRevision="$Rev$";
	public static final String sCSGVertexDate="$Date$";
	
	/** Static empty list of vertices */
	protected static final List<CSGVertex> sEmptyVertices = new ArrayList<CSGVertex>(0);
	/** Quick access to a NULL in a list of Vertices */
	protected static final List<CSGVertex> sNullVertexList = Collections.singletonList( null );

	
	/** Factory construction of appropriate vertex */
	public static CSGVertex makeVertex(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	,	Transform		pTransform
	,	CSGEnvironment	pEnvironment
	) {
		CSGVertex aVertex;
		
		if ( pTransform != null ) {
			// Adjust the position
			pPosition = pTransform.transformVector( pPosition, pPosition );
			// Only rotation affects the surface normal
			pNormal = pTransform.getRotation().multLocal( pNormal );
			// The texture does not budge
			pTextureCoordinate = pTextureCoordinate;
		}
		if ( pEnvironment.mDoublePrecision ) {
			Vector3d aPosition = new Vector3d( pPosition.x, pPosition.y, pPosition.z );
			Vector3d aNormal = new Vector3d( pNormal.x, pNormal.y, pNormal.z );
			aVertex = new CSGVertexDbl( aPosition, aNormal, pTextureCoordinate, pEnvironment );
		} else {
			aVertex = new CSGVertexFlt( pPosition, pNormal, pTextureCoordinate, pEnvironment );
		}
		return( aVertex );
	}

	/** Where is this vertex */
	protected VectorT	mPosition;
	/** What is its normal */
	protected VectorT 	mNormal;
	/** What is the texture coordinate */
	protected Vector2f	mTextureCoordinate;
	
	
	/** Make a copy */
	public abstract CSGVertex<VectorT> clone(
		boolean		pFlipIt
	);
	
	/** Accessor to the position */
	public VectorT getPosition() { return mPosition; }
	public abstract Vector3f getPositionFlt();
	
	/** Accessor to the normal */
	public VectorT getNormal() { return mNormal; }
	public abstract Vector3f getNormalFlt();
	
	/** Accessor to the texture coordinate */
	public Vector2f getTextureCoordinate() { return mTextureCoordinate; }
	

	/** Make a Vertex savable */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule(this);
		aCapsule.write( mTextureCoordinate, "texCoord", Vector2f.ZERO );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mTextureCoordinate = (Vector2f)aCapsule.readSavable( "texCoord", Vector2f.ZERO );
	}
	
	/** Enhanced equality check for within a tolerance */
	public abstract boolean equals(
		CSGVertex		pOther
	,	CSGEnvironment	pEnvironment
	);
	
	/** For Debug */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mPosition );
	}

}
