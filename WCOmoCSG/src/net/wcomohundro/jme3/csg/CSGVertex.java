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

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/** Constructive Solid Geometry (CSG)
	
	A CSG Vertex is a point in space, know by its position, its normal, and its texture coordinate

	NOTE
		that a vertex is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its attributes.  
		HOWEVER, the Vertex internal Vectors are used in Mesh construction and could therefore be 
		adjusted/altered. So even though the vertex itself is expected to be immutable, we must 
		anticipate its parts being consumed.  Therefore every clone must be a true copy.
  */
public class CSGVertex 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexRevision="$Rev$";
	public static final String sCSGVertexDate="$Date$";

	/** Where is this vertex */
	protected Vector3f	mPosition;
	/** What is its normal */
	protected Vector3f 	mNormal;
	/** What is the texture coordinate */
	protected Vector2f	mTextureCoordinate;
	
	/** Standard null constructor */
	public CSGVertex(
	) {
		this( Vector3f.ZERO, Vector3f.ZERO, Vector2f.ZERO );
	}
	
	/** Constructor based on the given components */
	public CSGVertex(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	) {
		this( pPosition, pNormal, pTextureCoordinate, null );
	}
	public CSGVertex(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	,	Transform		pTransform
	) {
		if ( pTransform != null ) {
			mPosition = pTransform.transformVector( pPosition, null );
			mNormal = pTransform.transformVector( pNormal, null );
			mTextureCoordinate = pTextureCoordinate;
		} else {
			mPosition = pPosition;
			mNormal = pNormal;
			mTextureCoordinate = pTextureCoordinate;
		}
	}
	
	/** Make a copy */
	public CSGVertex clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			return( new CSGVertex( mPosition.clone(), mNormal.negateLocal(), mTextureCoordinate.clone() ));
		} else {
			// Standard copy
			return( new CSGVertex( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone() ));
		}
	}
	
	/** Accessor to the position */
	public Vector3f getPosition() { return mPosition; }
	
	/** Accessor to the normal */
	public Vector3f getNormal() { return mNormal; }
	
	/** Accessor to the texture coordinate */
	public Vector2f getTextureCoordinate() { return mTextureCoordinate; }
	
	/** Interpolate between this vertex and another */
	public CSGVertex interpolate(
		CSGVertex 	pOther
	, 	float		pPercentage
	) {
		Vector3f newPosition 
			= this.mPosition.add( 
					pOther.getPosition().subtract( this.mPosition ).mult( pPercentage ) );
		Vector3f newNormal 
			= this.mNormal.add( 
					pOther.getNormal().subtract( this.mNormal ).mult( pPercentage ) );
		Vector2f newTextureCoordinate 
			= this.mTextureCoordinate.add( 
					pOther.getTextureCoordinate().subtract( this.mTextureCoordinate ).mult( pPercentage ) );
		return new CSGVertex( newPosition, newNormal, newTextureCoordinate );
	}

	/** Make a Vertex savable */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule(this);
		aCapsule.write( mPosition, "Position", Vector3f.ZERO );
		aCapsule.write( mNormal, "Normal", Vector3f.ZERO );
		aCapsule.write( mTextureCoordinate, "TexCoord", Vector2f.ZERO );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mPosition = (Vector3f)aCapsule.readSavable( "Position", Vector3f.ZERO );
		mNormal = (Vector3f)aCapsule.readSavable( "Normal", Vector3f.ZERO );
		mTextureCoordinate = (Vector2f)aCapsule.readSavable( "TexCoord", Vector2f.ZERO );
	}
	
	/** For Debug */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mPosition );
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGVertexRevision
													, sCSGVertexDate
													, pBuffer ) );
	}

}
