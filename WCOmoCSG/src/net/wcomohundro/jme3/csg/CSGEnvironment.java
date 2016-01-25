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
import java.util.logging.Logger;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;
import net.wcomohundro.jme3.csg.CSGShape.CSGShapeProcessor;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import com.jme3.scene.plugins.blender.math.Vector3d;

/** Define the operational environment to apply to the CSG processing 
 	An instance of this class acts as container for various public configuration parameters.
 	
 	As I have chased after the 'artifacts' problem, I found it desirable to alter these
 	parameters from a case-to-case basis, all within the same test run.  Therefore, the
 	generation/rendering interfaces have been expanded to accept a per test case, unique
 	definition of CSGEnvironment, defaulting back to a system standard if none is supplied.
 	
 	It is possible, therefore, to configure the CSGEnvironment via the XML load mechanism.
 */
public class CSGEnvironment<ShapeProcessorT> 
	implements ConstructiveSolidGeometry, Savable, Cloneable
{
	/** Version tracking support */
	public static final String sCSGEnvironmentRevision="$Rev$";
	public static final String sCSGEnvironmentDate="$Date$";
	
	/** Logger available to any CSG services that desire it */
    public static final Logger sLogger = Logger.getLogger( ConstructiveSolidGeometry.class.getName() );

	/** Standard configuration 
	 	NOTE
	 		we do NOT declare this global as 'final' so that the default can be dynamically
	 		modified at run time
	 */
	public static CSGEnvironment sStandardEnvironment = null;
	public static CSGEnvironment resolveEnvironment(
		CSGEnvironment	pEnvironment
	,	Spatial			pForSpatial
	) {
		if ( pEnvironment != null ) {
			// Use environment provided
			if ( sStandardEnvironment == null ) {
				// First time initialization of the standard
				sStandardEnvironment = pEnvironment.clone( null );
			}
		} else if ( sStandardEnvironment == null ) {
			// First time initialization of the standard
			pEnvironment = new net.wcomohundro.jme3.csg.iob.CSGEnvironmentIOB();
			sStandardEnvironment = pEnvironment;
		} else {
			// Use the standard
			pEnvironment = sStandardEnvironment;
		}
		if ( pForSpatial != null ) {
			if ( !pForSpatial.equals( pEnvironment.mShape ) ) {
				// Clone a copy with the given spatial
				pEnvironment = pEnvironment.clone( pForSpatial );
			}
		}
		return( pEnvironment );
	}
	
	/** Clone a copy with a given spatial */
	public CSGEnvironment clone(
		Spatial		pShape
	) {
		CSGEnvironment aCopy = null;
		try {
			aCopy = (CSGEnvironment)this.clone();
			aCopy.mShape = pShape;
			
		} catch( CloneNotSupportedException willNotHappen ) {
		}
		return( aCopy );
	}

	
	/** Java 1.8 equivalent to check if a double is finite */
    public static boolean isFinite(
    	double		pValue
    ) {
        return( !Double.isNaN( pValue ) && !Double.isInfinite( pValue ) );
    }
	/** Java 1.8 equivalent to check if a double is finite */
    public static boolean isFinite(
    	float		pValue
    ) {
        return( !Float.isNaN( pValue ) && !Float.isInfinite( pValue ) );
    }
    
    /** Service routine to check for a value so close to zero that it might as well be zero */
    public static double isZero(
    	double		pValue
    ,	double		pTolerance
    ) {
    	if ( (pValue < -pTolerance) || (pValue > pTolerance) ) {
    		return( pValue );
    	} else {
    		return( 0.0 );
    	}
    }
    public static float isZero(
    	float		pValue
    ,	float		pTolerance
    ) {
    	if ( (pValue < -pTolerance) || (pValue > pTolerance) ) {
    		return( pValue );
    	} else {
    		return( 0.0f );
    	}
    }

    /** Service routine to check two vectors and see if they are the same, within a given 
	 	tolerance. (Typically EPSILON_BETWEEN_POINTS, but not required)
	 */
	public static boolean equalVector2f(
		Vector2f 	pVector1
	,	Vector2f	pVector2
	,	double		pTolerance
	) {
		if ( pVector1 != pVector2 ) {
	    	float deltaX = pVector1.x - pVector2.x;
	    	if ( (deltaX < -pTolerance) || (deltaX > pTolerance) ) {
	    		return false;
	    	}
	    	float deltaY = pVector1.y - pVector2.y;
	    	if ( (deltaY < -pTolerance) || (deltaY > pTolerance) ) {
	    		return false;
	    	}
		}
		return( true );
	}
	public static boolean equalVector3f(
		Vector3f 	pVector1
	,	Vector3f	pVector2
	,	float		pTolerance
	) {
		if ( pVector1 != pVector2 ) {
	    	float deltaX = pVector1.x - pVector2.x;
	    	if ( (deltaX < -pTolerance) || (deltaX > pTolerance) ) {
	    		return false;
	    	}
	    	float deltaY = pVector1.y - pVector2.y;
	    	if ( (deltaY < -pTolerance) || (deltaY > pTolerance) ) {
	    		return false;
	    	}
	    	float deltaZ = pVector1.z - pVector2.z;
	    	if ( (deltaZ < -pTolerance) || (deltaZ > pTolerance) ) {
	    		return false;
	    	}
		}
		return( true );
	}
	/** Service routine to check two vectors and see if they are the same, within a given 
	 	tolerance. (Typically EPSILON_BETWEEN_POINTS, but not required)
	 */
	public static boolean equalVector3d(
		Vector3d 	pVector1
	,	Vector3d	pVector2
	,	double		pTolerance
	) {
		if ( pVector1 != pVector2 ) {
	    	double deltaX = pVector1.x - pVector2.x;
	    	if ( (deltaX < -pTolerance) || (deltaX > pTolerance) ) {
	    		return false;
	    	}
	    	double deltaY = pVector1.y - pVector2.y;
	    	if ( (deltaY < -pTolerance) || (deltaY > pTolerance) ) {
	    		return false;
	    	}
	    	double deltaZ = pVector1.z - pVector2.z;
	    	if ( (deltaZ < -pTolerance) || (deltaZ > pTolerance) ) {
	    		return false;
	    	}
		}
		return( true );
	}
	
	/** Service routine to 'rationalize' a vector to keep all its components within the same
	 	scale.  In other words, to have x,y,z values like 0.5, 0.4, 0.000000000001 with
	 	wildly varying orders of magnitude is rather silly.
	 */
	public static Vector3d rationalizeVector(
		Vector3d	pVector
	,	int			pMagnitudeRange
	) {
		// NOTE that by definition, the getExponent() of zero is MIN_EXPONENT-1, which
		//		actually works out quite fine for comparisons
		int magX = Math.getExponent( pVector.x );
		int magY = Math.getExponent( pVector.y );
		int magZ = Math.getExponent( pVector.z );
		if ( Math.abs( magX - magY ) > pMagnitudeRange ) {
			if ( magX > magY ) pVector.y = 0; else pVector.x = 0;
		}
		if ( Math.abs( magY - magZ ) > pMagnitudeRange ) {
			if ( magY > magZ ) pVector.z = 0; else pVector.y = 0;
		}
		if ( Math.abs( magZ - magX ) > pMagnitudeRange ) {
			if ( magZ > magX ) pVector.x = 0; else pVector.z = 0;
		}
		return( pVector );
	}
	
	/** Service routine to interpret a Savable.read() float value that can take the form
			xxxPI/yyy
		that supports fractional values of PI
	*/
	public static float readPiValue(
		InputCapsule	pCapsule
	,	String			pValueName
	,	float			pDefaultValue
	) throws IOException {
		float aFloatValue = pDefaultValue;
		
		// Read the value as a string so we can test for "PI"
		String piText = pCapsule.readString( pValueName, null );
		if ( piText != null ) {
			// Something explicitly given, figure out what
			piText = piText.toUpperCase();
			int index = piText.indexOf( "PI" );
			if ( index >= 0 ) {
				// Decipher things like 3PI/4
				float numerator = 1.0f;
				float denominator = 1.0f;
				if ( index > 0 ) {
					// Some integer multiplier of PI
					String numeratorTxt = piText.substring( 0, index );
					if ( numeratorTxt.indexOf( '.' ) < 0 ) {
						numerator = Integer.parseInt( numeratorTxt );
					} else {
						numerator = Float.parseFloat( numeratorTxt );
					}
				}
				index += 2;
				if ( (index < piText.length() -1) 
				&& (piText.charAt( index++ ) == '/') ) {
					// Some integer divisor of PI
					String denominatorTxt = piText.substring( index );
					if ( denominatorTxt.indexOf( '.' ) < 0 ) {
						denominator = Integer.parseInt( denominatorTxt );
					} else {
						denominator = Float.parseFloat( denominatorTxt );
					}
				}
				aFloatValue = (numerator * FastMath.PI) / denominator;
			} else {
				// If not PI, then its just a float
				aFloatValue = Float.parseFloat( piText );
			}
		}
		return( aFloatValue );
	}

	/** Debug support for confirming a vertex */
	public static boolean confirmVertex(
		CSGVertexFlt	pVertex
	) {
		// This is more a spot customize a check for something going wrong in a given test case
		float maxBetweenPoints = 10000f;
		float maxTexCoord = 1000f;
		
		// NOTE use of negative boolean logic to accommodate NaN and Infinity always producing
		//		false comparisons
		if ( !(
			   (Math.abs( pVertex.mPosition.x ) < maxBetweenPoints ) 
			&& (Math.abs( pVertex.mPosition.y ) < maxBetweenPoints ) 
			&& (Math.abs( pVertex.mPosition.z ) < maxBetweenPoints )
			) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Vertex: " + pVertex.mPosition );
			return( false );
		}
		// Upon further research, I am not seeing a requirement for the normal to be a unit vector
		double normalLength = pVertex.mNormal.length();
		if ( !(normalLength != 0.0f) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Normal: " + pVertex.mNormal + ", " + normalLength );
			return( false );
		}
		if ( !(
			   (Math.abs( pVertex.mTextureCoordinate.x ) <= maxTexCoord ) 
			&& (Math.abs( pVertex.mTextureCoordinate.y ) <= maxTexCoord )
			) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Tex: " + pVertex.mTextureCoordinate );
			return( false );
		}
		return( true );
	}
	public static boolean confirmVertex(
		CSGVertexDbl	pVertex
	) {
		// This is more a spot customize a check for something going wrong in a given test case
		double maxBetweenPoints = 10000;
		double maxTexCoord = 1000;
		
		// NOTE use of negative boolean logic to accommodate NaN and Infinity always producing
		//		false comparisons
		if ( !(
			   (Math.abs( pVertex.mPosition.x ) < maxBetweenPoints ) 
			&& (Math.abs( pVertex.mPosition.y ) < maxBetweenPoints ) 
			&& (Math.abs( pVertex.mPosition.z ) < maxBetweenPoints )
			) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Vertex: " + pVertex.mPosition );
			return( false );
		}
		// Upon further research, I am not seeing a requirement for the normal to be a unit vector
		double normalLength = pVertex.mNormal.length();
		if ( !(normalLength != 0.0f) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Normal: " + pVertex.mNormal + ", " + normalLength );
			return( false );
		}
		if ( !(
			   (Math.abs( pVertex.mTextureCoordinate.x ) <= maxTexCoord ) 
			&& (Math.abs( pVertex.mTextureCoordinate.y ) <= maxTexCoord )
			) ) {
			CSGEnvironment.sLogger.log( Level.SEVERE, "Bogus Tex: " + pVertex.mTextureCoordinate );
			return( false );
		}
		return( true );
	}
	
	
	/** Shape this environment applies to */
	public Spatial		mShape;
	/** Is value (Vector/Vertex) rationalization on or off */
	public boolean		mRationalizeValues;
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
	/** EPSILON - meaningful range of magnitudes between two floating point numbers */
	public int			mEpsilonMagnitudeRange;

	/** Type of 'shape processor' to operate with */
	public Class		mShapeClass;
	
	
	/** Constructor based on the 'standards' */
	protected CSGEnvironment(
		boolean		pDoublePrecision
	,	Class		pHandlerClass
	,	double		pEpsilonNearZeroDbl
	,	double		pEpsilonBetweenPointsDbl
	,	double		pEpsilonOnPlaneDbl
	,	float		pEpsilonNearZeroFlt
	,	float		pEpsilonBetweenPointsFlt
	,	float		pEpsilonOnPlaneFlt
	,	int			pEpsilonMagnitudeRange
	) {
		mShape = null;
		mDoublePrecision = pDoublePrecision;
		mShapeClass = pHandlerClass;
		mStructuralDebug = DEBUG;
		mRationalizeValues = true;
		
		mEpsilonNearZeroDbl = pEpsilonNearZeroDbl;
		mEpsilonOnPlaneDbl = pEpsilonOnPlaneDbl;		
		mEpsilonBetweenPointsDbl = pEpsilonBetweenPointsDbl;

		mEpsilonNearZeroFlt = pEpsilonNearZeroFlt;
		mEpsilonOnPlaneFlt = pEpsilonOnPlaneFlt;		
		mEpsilonBetweenPointsFlt = pEpsilonBetweenPointsFlt;
		
		mEpsilonMagnitudeRange = pEpsilonMagnitudeRange;
	}
	
	/** Log a message */
	public void log(
		Level		pLogLevel
	,	String		pMessage
	) {
		if ( this.mShape != null ) {
			pMessage = this.mShape.getName() + ": " + pMessage;
		}
		sLogger.log( pLogLevel, pMessage );
	}
	
	/** Build a standard exception */
	public IllegalArgumentException exception( 
		String		pMessage
	) {
		if ( this.mShape != null ) {
			pMessage = this.mShape.getName() + ": " + pMessage;
		}
		return( new IllegalArgumentException( pMessage ) );
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
		aCapsule.write( mRationalizeValues, "rationalizeValues", true );
		aCapsule.write( mStructuralDebug, "structuralDebug", false );
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mDoublePrecision = aCapsule.readBoolean( "doublePrecision", true );
		mRationalizeValues = aCapsule.readBoolean( "rationalizeValues", true );
		mStructuralDebug = aCapsule.readBoolean( "structuralDebug", DEBUG );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder pBuffer
	) {
		StringBuilder aBuffer = CSGVersion.getVersion( this.getClass()
														, sCSGEnvironmentRevision
														, sCSGEnvironmentDate
														, pBuffer );
		aBuffer.append( "\tDouble Precision: " ).append( this.mDoublePrecision ).append( "\n" );
		aBuffer.append( "\tStructural Debug: " ).append( this.mStructuralDebug ).append( "\n" );
		if ( this.mDoublePrecision ) {
			aBuffer.append( "\tEpsilon Near Zero: " ).append( this.mEpsilonNearZeroDbl ).append( "\n" );
			aBuffer.append( "\tEpsilon On Plane: " ).append( this.mEpsilonOnPlaneDbl ).append( "\n" );
			aBuffer.append( "\tEpsilon Between Points: " ).append( this.mEpsilonBetweenPointsDbl ).append( "\n" );
		} else {
			aBuffer.append( "\tEpsilon Near Zero: " ).append( this.mEpsilonNearZeroFlt ).append( "\n" );
			aBuffer.append( "\tEpsilon On Plane: " ).append( this.mEpsilonOnPlaneFlt ).append( "\n" );
			aBuffer.append( "\tEpsilon Between Points: " ).append( this.mEpsilonBetweenPointsFlt ).append( "\n" );
		}
		return( aBuffer );
	}

}
