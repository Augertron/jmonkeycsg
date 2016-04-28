/** Copyright (c) 2016, WCOmohundro
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
**/
package net.wcomohundro.jme3.csg.exception;

import java.util.Iterator;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;

/** This flavor of CSGExceptionI is a dynamic, runtime exception produced during object
 	generation/construction.
 */
public class CSGConstructionException 
	extends RuntimeException 
	implements CSGExceptionI
{
	/** Service routine to form a list of reported errors */
	public static CSGExceptionI registerError(
		CSGExceptionI	pPriorError
	,	CSGExceptionI	pError
	) {
		if ( pPriorError == null ) {
			// This is the first and only
			return( pError );
		} else {
			// Add this at the end
			pPriorError.addError( pError );
			return( pPriorError );
		}
	}
	/** Service routine to report on an error */
	public static StringBuilder reportError(
		CSGExceptionI	pError
	,	String			pSeparator
	,	StringBuilder	pBuffer
	) {
		if ( pBuffer == null ) {
			pBuffer = new StringBuilder( 128 );
		}
		if ( pError != null ) {
			// Add each error in the list
			int priorLength = pBuffer.length();
			for( CSGExceptionI anError : pError ) {
				if ( pBuffer.length() > priorLength ) {
					pBuffer.append( pSeparator );
				}
				anError.reportError( pBuffer );
			}
		}
		return( pBuffer );
	}
	
	/** The error code */
	protected CSGErrorCode	mErrorCode;
	/** The associated element */
	protected CSGElement	mElement;
	/** Link to the next error */
	protected CSGExceptionI	mNextError;
	
	
	/** Standard constructor based on a code and message */
	public CSGConstructionException(
		CSGErrorCode	pErrorCode
	,	String			pMessage
	) {
		this( pErrorCode, pMessage, null, (Throwable)null );
	}
	/** Standard constructor based on a code, message, and root cause exception */
	public CSGConstructionException(
		CSGErrorCode	pErrorCode
	,	String			pMessage
	,	Throwable		pCause
	) {
		this( pErrorCode, pMessage, null, pCause );
	}
	/** Standard constructor based on a code, message, and element */
	public CSGConstructionException(
		CSGErrorCode	pErrorCode
	,	String			pMessage
	,	CSGElement		pElement
	) {
		this( pErrorCode, pMessage, pElement, (Throwable)null );
	}
	/** Standard constructor based on a code, message, element, and root cause exception */
	public CSGConstructionException(
		CSGErrorCode	pErrorCode
	,	String			pMessage
	,	CSGElement		pElement
	,	Throwable		pCause
	) {
		super( pMessage, pCause );
		mErrorCode = pErrorCode;
		mElement = pElement;
	}
	/** Standard constructor based on a code, message, element, and another construction problem */
	public CSGConstructionException(
		CSGErrorCode	pErrorCode
	,	String			pMessage
	,	CSGElement		pElement
	,	CSGExceptionI	pCause
	) {
		super( pMessage );
		mErrorCode = pErrorCode;
		mElement = pElement;
		mNextError = pCause;
	}
	
	/** The associated error code */
	@Override
	public CSGErrorCode getErrorCode() { return mErrorCode; }
	
	/** The associated element */
	@Override
	public CSGElement getCSGElement() { return mElement; }
	@Override
	public void setCSGElement( CSGElement pElement ) { mElement = pElement; }
	
	/** Error list management */
	@Override
	public CSGExceptionI getNextError() { return( mNextError ); }
	@Override
	public CSGExceptionI addError( 
		CSGExceptionI pError
	) {
		if ( this == pError ) {
			// Do NOT add to yourself
			return( null );
		} else if ( mNextError == null ) {
			// This becomes the first
			mNextError = pError;
		} else {
			// Pass it down the chain (assuming we do not produce all that many linked errors)
			mNextError.addError( pError );
		}
		return( pError );
	}
	
	/** Build a report on this error */
	public void reportError( 
		StringBuilder pBuffer
	) {
		pBuffer.append( mErrorCode.toString() )
			   .append( ": " )
			   .append( this.getLocalizedMessage() );
		if ( mElement != null ) {
			pBuffer.append( "\n\telement: " )
			       .append( mElement.asSpatial().getName() );
		}
		Throwable aCause = this.getCause();
		if ( aCause != null ) {
			pBuffer.append( "\n\t" )
				   .append( aCause.getLocalizedMessage() );
		}
	}


	/////////////////////////// Iterable ///////////////////////////////
	public Iterator iterator(
	) {
		return( new CSGExceptionIterator( this ) );
	}
}
/** Helper class that iterates a list of CSGExceptions */
class CSGExceptionIterator
	implements Iterator<CSGExceptionI>
{
	/** Active iteration item */
	protected CSGExceptionI		mActiveItem;
	
	/** Standard constructor */
	CSGExceptionIterator(
		CSGExceptionI	pError
	) {
		mActiveItem = pError;
	}
	
	@Override
	public boolean hasNext() { return( mActiveItem != null ); }
	
	@Override
	public CSGExceptionI next(
	) {
		CSGExceptionI thisError = mActiveItem;
		if ( thisError != null ) {
			mActiveItem = thisError.getNextError();
		}
		return( thisError );
	}
	
	@Override
	public void remove(
	) {
	}
}
