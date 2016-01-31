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

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;

/** Define the common attributes of any CSG exception.
 	Every CSG exception has an integer ErrorCode, can identify an associated CSGElement, and
 	can act as an Iterable, returning a series of more detailed errors
 
 	NOTE
 		that we start with a simple Interface, not an extension of a particular java Throwable.
 		This allows us define both Runtime and Exception style CSG exceptions that obey this
 		contract.
 		
 	ALL ERROR CODES FOR THE ENTIRE CSG SYSTEM WILL BE DEFINED HERE

 */
public interface CSGExceptionI
	extends Iterable<CSGExceptionI>
{
	public enum CSGErrorCode {
		EMPTY_SHAPE			// A shape has been created with no mesh or subshapes -- just what is this????
	,	INVALID_SHAPE		// Working with a shape that has marked itself invalid
	,	CONSTRUCTION_FAILED	// CSG regeneration failed
	,	LOAD_FAILED			// Asset load mechanism failed
	,	INVALID_VERTEX		// Attempt to build a bogus vertex
	,	INVALID_PLANE		// Attempt to build a bogus plane
	,	INVALID_MESH		// Working with a mesh we do not understand
	,	INTERRUPTED			// Processing externally interrupted
	}
	/** The associated error code */
	public CSGErrorCode getErrorCode();

	/** The associated element */
	public CSGElement getCSGElement();
	
	/** Error list management */
	public CSGExceptionI getNextError();
	public CSGExceptionI addError( CSGExceptionI pError );
}
