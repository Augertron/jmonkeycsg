/** Copyright (c) 2018, WCOmohundro
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
package com.jme3.export.xml;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jme3.asset.AssetKey;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLExporter;
import com.jme3.math.FastMath;
import com.jme3.util.BufferUtils;
import com.jme3.util.IntMap;

/** This is a basic rip-off of the DOMInputCapsule, leveraging all the standard readBlah() support,
 	but providing extra XML processing services as well.  For example, floating point input
 	values can be expressed as a rational fraction (literally '1/3') or as a fraction of PI
 	('1/2PI') to help with radian values.
 	
 	The basic premise is that every element in the DOM represents some Savable (or item within a
 	Savable)  We know which 'current' element we are working on, and the parent .read() processing
 	directs us from item to item, each item becoming the 'current' as it is worked on.
 	
 	XMLInputCapsule is designed to support manually coded XML, not just the result of a prior
 	XML output that is machine generated.  To that end, we anticipate likely human error 
 	(basic typos) so this class will monitor the attributes/elements touched during the
 	Savable read process. If any attributes/elements exist within the XML that were not
 	touched, an error is registered.
 	
 	Those Savables that are 'XMLInputCapsule' aware can use the call backs to process DOM
 	elements on their own.  This allows any given Savable to customize its XML support beyond
 	the standard jMonkey definitions of XML data types.
 	
 	String substitution will be applied if given a set of ResourceBundles.  Any
 	strings of the form ${bundle.name} will locate the bundle by name, and will then
 	resolve the given name within that bundle.  The resultant text replaces the ${...} string.
 	
 	A special 'internal' bundle is supported where substitution values can be defined by
 	the parsing process itself. Of course, no localization occurs, you just get what you get.
 	The internal bundle is referenced by ${somename}.  It is the responsibility of the
 	user of XMLInputCapsule to decide how to load the internal bundle.
 	
 	The id/ref mechanism provides a way of defining Savables within a library, which can
 	be referenced within the parsing to provide a common definition.  The library can be
 	pre-seeded with values before the parsing starts.
 	
 	@see XMLContextKey for loading XML based asset content from within another XML input
 		 process, passing the active XML input context from the outer to the inner process.
 		 This allows you to utilize the id/ref, resource bundle, and internal bundle
 		 across different parsing invocations.
 */
public class XMLInputCapsule 
	implements InputCapsule
{
    /** DOM service routine:  scan for the first child Element within a parent */
    public static Element findFirstChildElement(
    	Element 	parent
    ) {
        Node ret = parent.getFirstChild();
        while (ret != null && (!(ret instanceof Element))) {
            ret = ret.getNextSibling();
        }
        return (Element) ret;
    }

    /** DOM service routine:  scan for the first child Element of a given name within a parent */
    public static Element findChildElement(
    	Element 	parent
    , 	String 		name
    ) {
        if ( parent == null ) {
            return null;
        } else if ( name == null ) {
        	return parent;
        }
        Node ret = parent.getFirstChild();
        while (ret != null && (!(ret instanceof Element) || !ret.getNodeName().equals(name))) {
            ret = ret.getNextSibling();
        }
        return (Element) ret;
    }
    
    /** DOM service routine:  scan for the next sibling Element after the given one */
    public static Element findNextSiblingElement(
    	Element 	pCurrent
    ) {
        return( findNextSiblingElement( pCurrent, null ) );
    }
    /** DOM service routine:  scan for the next sibling Element after the given one */
    public static Element findNextSiblingElement(
    	Element 	pCurrent
    ,	String		pSiblingName
    ) {
        Node aNode = pCurrent.getNextSibling();
        while( aNode != null ) {
            if ( aNode instanceof Element ) {
            	if ( (pSiblingName == null) || pSiblingName.equals( aNode.getNodeName() ) ) {
            		return (Element)aNode;
            	}
            }
            aNode = aNode.getNextSibling();
        }
        return null;
    }
    
    /** Floating point parsing routine that understands rational fractions and PI 
     		xx.xPI/yy.y    or   xx.x/yy.yPI    or  xxx/yyy
     */
    public static float parseFloat(
    	String		pPiText
    ,	float		pDefault
    ) {
    	if ( pPiText == null ) {
    		return pDefault;
    	}
    	float aFloatValue = 0.0f;
		float numerator = 1.0f;
		float denominator = 1.0f;

		pPiText = pPiText.toUpperCase();
		int piIndex = pPiText.indexOf( "PI" );
		int slashIndex = pPiText.indexOf( "/" );
		if ( piIndex >= 0 ) {
			// Decipher things like 3PI/4 and 3/4PI
			int numeratorEnd, denominatorStart;
			if ( slashIndex < 0 ) {
				// Like 2PI
				numerator = FastMath.PI;
				numeratorEnd = piIndex;
				denominatorStart = piIndex + 2;
			} else if ( piIndex < slashIndex ) {
				// Like 3PI/4
				numerator = FastMath.PI;
				numeratorEnd = piIndex;
				denominatorStart = slashIndex + 1;
			} else {
				// Like 3/4PI
				denominator = FastMath.PI;
				pPiText = pPiText.substring( 0, piIndex );
				numeratorEnd = slashIndex;
				denominatorStart = slashIndex + 1;
			}
			if ( numeratorEnd > 0 ) {
				// Some integer multiplier of PI
				String numeratorTxt = pPiText.substring( 0, numeratorEnd );
				if ( numeratorTxt.indexOf( '.' ) < 0 ) {
					numerator *= Integer.parseInt( numeratorTxt );
				} else {
					numerator *= Float.parseFloat( numeratorTxt );
				}
			}
			if ( denominatorStart < pPiText.length() ) {
				// Some integer divisor of PI
				String denominatorTxt = pPiText.substring( denominatorStart );
				if ( denominatorTxt.indexOf( '.' ) < 0 ) {
					denominator *= Integer.parseInt( denominatorTxt );
				} else {
					denominator *= Float.parseFloat( denominatorTxt );
				}
			}
			aFloatValue = numerator / denominator;
			
		} else if ( slashIndex > 0 ) {
			// Rational fraction like 3/4, but no PI
			String numeratorTxt = pPiText.substring( 0, slashIndex );
			if ( numeratorTxt.indexOf( '.' ) < 0 ) {
				numerator = Integer.parseInt( numeratorTxt );
			} else {
				numerator = Float.parseFloat( numeratorTxt );
			}
			String denominatorTxt = pPiText.substring( slashIndex + 1 );
			if ( denominatorTxt.indexOf( '.' ) < 0 ) {
				denominator = Integer.parseInt( denominatorTxt );
			} else {
				denominator = Float.parseFloat( denominatorTxt );
			}
			aFloatValue = numerator / denominator;
			
		} else {
			// If not PI or a rational fraction, then its just a float
			aFloatValue = Float.parseFloat( pPiText );
		}
    	return aFloatValue;
    }
    
    /** The importer that is driving this process */
    protected JmeImporter					mImporter;
    /** The DOM Document that is being processed */
    protected Document 						mDocument;
    /** The Element in the DOM that is actively being processed */
    protected Element 						mCurrentElement;
    /** Format version of the overall XML, as defined by format_version='123' in the XML itself */
    protected int							mFormatVersion;
    /** Version info registered with the Savables, filled in dynamically as elements are processed */
    protected int[] 						mClassHierarchyVersions;
    /** Set of special extended mappings of abbreviations to classes to instantiate */
    protected Map<String,Class>				mClassAbbreviations;
    /** Library of registered values, as set by id='xxx', referenced by ref='xxx' */
    protected Map<String, Savable>			mReferencedSavables;
    /** Library of ResourceBundles used for string translation */
    protected Map<String,ResourceBundle>	mResourceBundles;
    protected ResourceBundle				mInternalBundle;
    /** The Savable that is actively being filled, and the attributes/elements it consumes */
    protected Savable 						mSavable;
    protected Set<String>					mSavableAttrs;

    /** The basic constructor that must be provided with the DOM */
    public XMLInputCapsule(
    	Document 		pDocument
    , 	JmeImporter		pImporter
    ) {
        mDocument = pDocument;
        mImporter = pImporter;
        
        mReferencedSavables = new HashMap<String, Savable>();
        
        mCurrentElement = mDocument.getDocumentElement();
        
        String aVersion = mCurrentElement.getAttribute( "format_version" );
        mFormatVersion = aVersion.isEmpty() ? 0 : Integer.parseInt( aVersion );
    }
    
    /** Allow extended abbreviations for class names */
    public Map<String,Class> getClassAbbreviations() { return mClassAbbreviations; }
    public void setClassAbbreviations(
    	Map<String,Class>	pAbbreviations
    ) {
    	mClassAbbreviations = pAbbreviations;
    }
    public void registerClassAbbreviation(
    	String		pAbbreviation
    ,	Class		pClass
    ) {
    	if ( mClassAbbreviations == null ) {
    		mClassAbbreviations = new HashMap( 17 );
    	}
    	mClassAbbreviations.put( pAbbreviation, pClass );
    }
    
    /** Allow predefined 'seed' values to be provided into the processing */
    public Map<String, Savable>	getReferencedSavables() { return mReferencedSavables; }
    public Savable getReferencedSavable( String pRefID ) { return mReferencedSavables.get( pRefID ); }
    public void setReferencedSavable( 
    	String 		pRefID
    , 	Savable 	pValue 
    ) { 
    	mReferencedSavables.put( pRefID, pValue );
    }
    public void seedReferencedSavables(
    	Map<String,Savable>	pSeedValues
    ,	boolean				pBlendIntoSeedValues
    ) {
    	if ( pSeedValues != null ) {
		    if ( pBlendIntoSeedValues ) {
		    	// Use the given values as the source and blend new definitions into it
		    	mReferencedSavables = pSeedValues;
		    } else {
		    	// Start with the given seed values, but do not affect the given map
		    	mReferencedSavables.putAll( pSeedValues );
		    }
    	}
    }
    
    /** Allow translation bundles to be defined */
    public void setResourceBundles( 
    	Map<String,ResourceBundle>	pBundles
    ,	ResourceBundle				pInternalDefinitions
    ) {
    	mResourceBundles = pBundles;
    	if ( pInternalDefinitions != null ) {
    		mInternalBundle = pInternalDefinitions;
    	}
    }
    
    /** Define substitution values for the internal bundle */
    public void addInternalSubstitution(
    	String		pKey
    ,	Object		pValue
    ,	boolean		pOverwritePrior
    ) {
    	// This only works if we are using our own variant of a bundle
    	// Otherwise there is no standard way to set a dynamic value into a bundle.
    	if ( mInternalBundle == null ) {
    		mInternalBundle = new XMLInputCapsuleBundle();
    	}
    	if ( pOverwritePrior || !mInternalBundle.containsKey( pKey ) ) {
    		((XMLInputCapsuleBundle)mInternalBundle).put( pKey, pValue );
    	}
    }
    
    /** Allow direct access to the current element */
    public Element getCurrentElement() { return mCurrentElement; }
    
    /** Allow importer .read() to suppress attribute checking on the current Savable
     	being processed
     */
    public void suppressAttributeChecking() { mSavableAttrs = null; }
    
    /** Callback processing to read a savable from the given element, instantiating 
        from a given class name 
     */
    public Savable readSavableFromElem(
    	Element		pElement
    ,	Savable		pDefaultValue
    ,	boolean		pMarkElementProcessed
    ) throws IOException {
    	return readSavableFromElem( pElement, pDefaultValue, null, pMarkElementProcessed );
    }
    /** Read a set of savables from beneath an element, either explicitly named or not */
    public List<Savable> readSavablesFromElement(
    	Element		pElement
    ,	String		pChildName
    ,	boolean		pMarkElementProcessed
    ) throws IOException {
    	if ( pElement == null ) return null;
    	
    	List<Savable> childList = new ArrayList( 5 );
		Element childElement;
		if ( pChildName == null ) {
			// Accept a child of any name
			childElement = XMLInputCapsule.findFirstChildElement( pElement );
		} else {
			// Accept only a specifically named child
			childElement = findChildElement( pElement, pChildName );
		}
		while( childElement != null ) {
			Savable aChild = readSavableFromElem( childElement, null, null, pMarkElementProcessed );
			childList.add( aChild );
			
			childElement = findNextSiblingElement( childElement, pChildName );
		}
		return childList;
    }
    
    /** Read and instantiate a savable, possibly operating from a clone */
    public Savable readSavableFromElem(
    	Element		pElement
    ,	Savable		pDefaultValue
    ,	Savable		pCloneSource
    ,	boolean		pMarkElementProcessed
    ) throws IOException {
    	if ( pElement ==  null ) return pDefaultValue;
    	
    	// Prep what we find
        Savable aResult = pDefaultValue;
        Savable xmlResult = null;

    	Element savedElement = mCurrentElement;
    	Savable savedContext = mSavable;
    	Set<String> savedContextAttrs = mSavableAttrs;
    	int[] savedVersions = mClassHierarchyVersions;
    	try {
            // Use the given element as the current context
	    	mCurrentElement = pElement;
	    	
	        // A reference to a previously constructed item overrides all other processing
	        String refID = mCurrentElement.getAttribute( "ref" );
	        String cloneID = mCurrentElement.getAttribute( "clone" );
	        if ( refID.length() > 0 ) {
	        	// Look up the reference and us it as-is
	        	if ( mReferencedSavables.containsKey( refID ) ) {
	        		aResult = mReferencedSavables.get( refID );
	        	} else {
	        		// This is not the same as an explicit null value, 
            		throw new IOException( "Reference: " + refID + " not defined for " + mCurrentElement );
	        	}
	        } else if ( cloneID.length() > 0 ) {
	        	// Look up the reference and clone it as the base instance
	        	if ( cloneID.equals( "*" ) ) {
	        		if ( pCloneSource instanceof XMLCloneable ) {
		        		// Use the explicit source provided
		        		xmlResult = pCloneSource;
	        		} else if ( pDefaultValue instanceof XMLCloneable ) {
	        			// Clone the default
	        			xmlResult = pDefaultValue;
	        		} else {
	        			// We have nothing else to substitute for "*"
	        		}
	        	} else {
	        		// Lookup the reference
	        		xmlResult = mReferencedSavables.get( cloneID );
	        	}
	        	if ( xmlResult instanceof XMLCloneable ) {
	        		// Make a copy of the template
	        		xmlResult = aResult = (Savable)((XMLCloneable)xmlResult).clone();
	        	} else {
	        		// If we cannot clone it and obviously the user is expecting something
	        		// to be here
	        		throw new IOException( "Clone: " + cloneID + " not valid for " + mCurrentElement );
	        	}
	        } else {
	        	// Process the current node, where the class='...' attribute tells us
	        	// the class to instantiate, or else the node name is expected to be
	        	// the class name.
	            Class aClass = null;
	            String className = null;
	            String classValue = null;
	            if ( mCurrentElement.hasAttribute( "value" ) ) {
	            	// An explicit 'value' is just a string
	            	classValue = decodeString( mCurrentElement.getAttribute( "value" ) );
	            }
	            if ( mCurrentElement.hasAttribute( "class" ) ) {
	            	// An explicit class attribute is the override
	            	className = mCurrentElement.getAttribute( "class" );
	                aClass = XMLClassUtil.classFromName( className, mClassAbbreviations );
	                
	            } else if ( pDefaultValue != null ) {
	            	// The given default value will direct the class to use
	            	aClass = pDefaultValue.getClass();
	            	
	            } else {
	            	// The node name itself is expected to direct the class
	            	className = mCurrentElement.getNodeName();
	            	aClass = XMLClassUtil.classFromName( className, mClassAbbreviations );
	            }
	            if ( classValue != null ) {
	            	if ( aClass == null ) {
	            		// Just a string
	            		aResult = new SavableString( classValue );
	            	} else {
	            		// Expect a static field of this name
	            		aResult = XMLClassUtil.fromClassField( aClass, classValue );
	            	}
	            } else if ( aClass != null ) {
		            // Instantiate the Savable instance
		            // NOTE that an explicit class reference to java.lang.Void will return a null
		            xmlResult = aResult = XMLClassUtil.fromClass( aClass );
	            } else {
	            	// I do not understand this one, so let the default value stand
	            	aResult = pDefaultValue;
	            }
	            // Check for version constraints
	            String versionsStr = mCurrentElement.getAttribute( "savable_versions" );
	            if ( !versionsStr.isEmpty() ) {
	                String[] versions = versionsStr.split(",");
	                mClassHierarchyVersions = new int[ versions.length ];
	                for (int i = 0; i < mClassHierarchyVersions.length; i++){
	                    mClassHierarchyVersions[i] = Integer.parseInt( versions[i].trim());
	                }
	            } else {
	                mClassHierarchyVersions = null;
	            }
	        }
            if ( xmlResult != null ) {
	            // We fill the empty/cloned instance constructed above by letting
	            // it read itself.  We save the current element 'context'
	            // within mSavable, and get ready to monitor which attr/elements
            	// are processed.
	            mSavable = xmlResult;
	            
	            // If we have reference_ID='blah' or id='blah', then save this
	            // instance for subsequent reference
	            refID = mCurrentElement.getAttribute( "reference_ID" );
	            if ( refID.isEmpty() ) refID = mCurrentElement.getAttribute( "id" );
	            if ( refID.length() > 0 ) {
	            	// Allow subsequent parsing to reference back to this item
	            	boolean doReference = true;
	            	if ( mReferencedSavables.containsKey( refID ) ) {
	            		// By default, do NOT override something previously defined
	            		if ( mCurrentElement.hasAttribute( "overrideRef" ) ) {
	            			doReference = Boolean.parseBoolean( mCurrentElement.getAttribute( "overrideRef" ) );
	            		} else {
	            			doReference = false;
	            		}
	            	}
	            	if ( doReference ) {
	            		// Make this item referencable
	            		mReferencedSavables.put( refID, aResult );
	            	}
	            }
	            // Preseed with the standards that are part of the XML process,
	            // not the actual instance
	            mSavableAttrs = new HashSet();
	            mSavableAttrs.add( "class" );
	            mSavableAttrs.add( "clone" );
	            mSavableAttrs.add( "id" );
	            mSavableAttrs.add( "reference_ID" );
	            mSavableAttrs.add( "overrideRef" );
	            
	            aResult.read( mImporter );
	            
	            // Confirm that all attributes and child elements have been touched
	            // NOTE that a callback during the .read() above can suppress this check
	            if ( mSavableAttrs != null ) {
		            NamedNodeMap allAttrs = mCurrentElement.getAttributes();
		            for( int i = 0, j = allAttrs.getLength(); i < j; i += 1 ) {
		            	Node anAttr = allAttrs.item( i );
		            	String attrName = anAttr.getNodeName();
		            	if ( !mSavableAttrs.contains( attrName ) ) {
		            		throw new IOException( "Attribute: " + attrName + " not supported by " + mSavable );
		            	}
		            }
		            Node childNode = mCurrentElement.getFirstChild();
		            while( childNode != null ) {
		            	if ( childNode.getNodeType() == Node.ELEMENT_NODE ) {
			            	String childName = childNode.getNodeName();
			            	if ( !mSavableAttrs.contains( childName ) ) {
			            		throw new IOException( "Element: " + childName + " not supported by " + mSavable );
			            	}
		            	}
		            	childNode = childNode.getNextSibling();
		            }
	            }
            }
    	} catch( IOException ex ) {
    		// These are just thrown as is
    		throw ex;
    	} catch( Exception ex ) {
    		// Throw as an IOException
            IOException io = new IOException( ex.toString() );
            io.initCause( ex );
            throw io;    		
    	} finally {
    		// Restore all the saved values
            mCurrentElement = savedElement;
            mSavable = savedContext;
            mSavableAttrs = savedContextAttrs;
            mClassHierarchyVersions = savedVersions;
            
            if ( pMarkElementProcessed ) {
            	// This child is part of the parent
            	hasProcessed( pElement.getNodeName() );
            }
    	}
        // Return whatever we have found so far
        return aResult;
    }
    
    /** Look for a string, based on an 'attribute' or on a child with a value= attribute */
    public String resolveAttribute(
    	Element		pElement
    ,	String		pName
    ) {
    	// Look first for an attribute
    	String aValue = null;
    	if ( pElement.hasAttribute( pName ) ) {
    		// Use whatever was given (even an explicit empty string)
    		aValue = pElement.getAttribute( pName );
    	} else {
    		Element aChild = findChildElement( pElement, pName );
    		if ( aChild != null ) {
    			// Look for its 'value'
    			if ( aChild.hasAttribute( "value" ) ) {
    				aValue = aChild.getAttribute( "value" );
    			}
    		}
    		if ( aValue == null ) {
    			// No such attribute
    			return( null );
    		}
    	}
    	// Decode it
    	String bValue = decodeString( aValue );
    	return bValue;
    }

    /** Callback processing to clean up and interpret a string 
     	Use this hook to tidy up the XML character conventions AND to look for 
     	ResourceBundle substitutions (like with Nifty)
     */
    public String decodeString(
    	String	pText
    ) {
        if ( pText == null ) {
            return null;
        }
        pText = pText.replaceAll("\\&quot;", "\"").replaceAll("\\&lt;", "<").replaceAll("\\&amp;", "&");
        
        if ( (mInternalBundle != null) || (mResourceBundles != null) ) {
        	// Look for dynamic substitutions of the form ${bundle.key.key.key}
        	int start, prior = 0;
        	StringBuilder aBuffer = null;
        	
        	while( (start = pText.indexOf( "${", prior )) >= prior ) {
        		start += 2;
        		int end = pText.indexOf( "}", start );
        		int dot = pText.indexOf( ".", start );
        		ResourceBundle aBundle = null;
        		
        		if ( dot < 0 ) {
        			// If not a dot notation, then it is an internal reference
        			aBundle = mInternalBundle;
        			dot = 1;
        		} else if ( (end > start) && ((end-start) >= 3) && (dot > start) && (dot < end-1) ) {
            		// We need a minimum of ${a.b} where the a. portion picks the bundle
        			String libraryRef = pText.substring( start, dot );
        			aBundle = mResourceBundles.get( libraryRef );
        		}
    			if ( aBundle != null ) {
    				String aKey = pText.substring( dot+1, end );
    				if ( aBundle.containsKey( aKey ) ) {
    					// We have a substitution
    					String aValue = aBundle.getString( aKey );
    					
    					if ( aBuffer == null ) {
    						aBuffer = new StringBuilder( pText.length() + 10 );
    					}
    					if ( prior < start-2 ) {
    						// Copy up to the ${
    						aBuffer.append( pText, prior, start-2 );
    					}
    					// Copy the substitution
    					aBuffer.append( aValue );
    				}
    			}
        		if ( end > start ) {
        			// Look for another after skipping past the }
        			prior = end + 1;
        		} else {
        			// Off the end
        			break;
        		}
        	}
        	if ( aBuffer != null ) {
        		// Copy anything after the last }
        		if ( prior < pText.length() ) {
        			aBuffer.append( pText, prior, pText.length() );
        		}
        		// Return the replacement text
        		pText = aBuffer.toString();
        	}
        }
        return pText;
    }

    /** Service routine to break a string into multiple tokens */
    protected static final String[] zeroStrings = new String[0];
    protected String[] parseTokens(
    	String inString
    ) {
    	inString = decodeString( inString );
    	
        String[] outStrings = inString.split("\\s+");
        return (outStrings.length == 1 && outStrings[0].length() == 0)
               ? zeroStrings
               : outStrings;
    }

    /** Service routine to read a savable, instantiating from a given class name */
    protected Savable readSavableFromCurrentElem(
    	Savable		pDefaultValue
    ) throws IOException {
        if ( (mCurrentElement == null) || mCurrentElement.getNodeName().equals("null") ) {
            return null;
        }
        return( readSavableFromElem( mCurrentElement, pDefaultValue, false ) );
    }
    
    /** Service routine to mark an attribute within a Savable as being processed */
    protected void hasProcessed( 
    	String	pName
    ) {
    	if ( (mSavable != null) && (mSavableAttrs != null) ) {
    		mSavableAttrs.add( pName );
    	}
    }

    //////////////////////////////////// Input Capsule ///////////////////////////////////////////
    /** Manage the versioning */
    @Override
    public int getSavableVersion(
    	Class<? extends Savable> 	pDesiredClass
    ) {
    	// If we have encountered version markers on savables, they will be regisered
    	// within mClassHierarchyVersion, with the active savable in mSavable
        if ( mClassHierarchyVersions != null ){
            return XMLClassUtil.getSavedSavableVersion(
            		mSavable, pDesiredClass, mClassHierarchyVersions, mFormatVersion );
        } else {
        	// Nothing in particular is known
            return 0;
        }
    }
        
    @Override
    public byte readByte(String name, byte defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Byte.parseByte(tmpString);
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public byte[] readByteArray(String name, byte[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens( tmpEl.getAttribute("data") );
            byte[] tmp = new byte[strings.length];
            for (int i = 0; i < strings.length; i++) {
                tmp[i] = Byte.parseByte(strings[i]);
            }
            return tmp;

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public byte[][] readByteArray2D(String name, byte[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<byte[]> byteArrays = new ArrayList<byte[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    byteArrays.add(readByteArray(n.getNodeName(), null));
                                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return byteArrays.toArray(new byte[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public int readInt(String name, int defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Integer.parseInt(tmpString);
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public int[] readIntArray(String name, int[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);


            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            int[] tmp = new int[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Integer.parseInt(strings[i]);
            }
            return tmp;

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public int[][] readIntArray2D(String name, int[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<int[]> intArrays = new ArrayList<int[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                	// Very unsafe assumption
                    intArrays.add( readIntArray( n.getNodeName(), null) );
                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return intArrays.toArray(new int[0][]);
            
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public float readFloat(String name, float defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return parseFloat( tmpString, defVal );
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public float[] readFloatArray(String name, float[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            float[] tmp = new float[strings.length];
            int defCount = (defVal == null) ? 0 : defVal.length;
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = parseFloat( strings[i], (i < defCount) ? defVal[i] : Float.NaN );
            }
            return tmp;

        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public float[][] readFloatArray2D(String name, float[][] defVal) throws IOException {
        /* Why does this one method ignore the 'size attr.? */
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            int size_outer = Integer.parseInt(tmpEl.getAttribute("size_outer"));
            int size_inner = Integer.parseInt(tmpEl.getAttribute("size_outer"));

            float[][] tmp = new float[size_outer][size_inner];

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            for (int i = 0; i < size_outer; i++) {
                tmp[i] = new float[size_inner];
                for (int k = 0; k < size_inner; k++) {
                    tmp[i][k] = parseFloat( strings[i], defVal[i][k] );
                }
            }
            return tmp;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public double readDouble(String name, double defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Double.parseDouble(tmpString);
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public double[] readDoubleArray(String name, double[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            double[] tmp = new double[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Double.parseDouble(strings[i]);
            }
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public double[][] readDoubleArray2D(String name, double[][] defVal) throws IOException {
        try {
            Element tmpEl = mCurrentElement;
            
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<double[]> doubleArrays = new ArrayList<double[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                	// Very unsafe assumption
                	doubleArrays.add(readDoubleArray(n.getNodeName(), null));
                }
            }
            mCurrentElement = (Element)mCurrentElement.getParentNode();
            return doubleArrays.toArray(new double[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public long readLong(String name, long defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Long.parseLong(tmpString);
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public long[] readLongArray(String name, long[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            long[] tmp = new long[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Long.parseLong(strings[i]);
            }
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public long[][] readLongArray2D(String name, long[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<long[]> longArrays = new ArrayList<long[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    longArrays.add(readLongArray(n.getNodeName(), null));
                                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return longArrays.toArray(new long[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public short readShort(String name, short defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Short.parseShort(tmpString);
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public short[] readShortArray(String name, short[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            short[] tmp = new short[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Short.parseShort(strings[i]);
            }
            return tmp;

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public short[][] readShortArray2D(String name, short[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<short[]> shortArrays = new ArrayList<short[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    shortArrays.add(readShortArray(n.getNodeName(), null));
                                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return shortArrays.toArray(new short[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public boolean readBoolean(String name, boolean defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            return Boolean.parseBoolean(tmpString);
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public boolean[] readBooleanArray(String name, boolean[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens( tmpEl.getAttribute("data") );
            boolean[] tmp = new boolean[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Boolean.parseBoolean(strings[i]);
            }
            return tmp;

        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public boolean[][] readBooleanArray2D(String name, boolean[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<boolean[]> booleanArrays = new ArrayList<boolean[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                    // Very unsafe assumption
                    booleanArrays.add(readBooleanArray(n.getNodeName(), null));
                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return booleanArrays.toArray(new boolean[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public String readString(String name, String defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
        	// Nothing is really done to a string
            return tmpString;
            
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public String[] readStringArray(String name, String[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = tmpEl.getChildNodes();
            List<String> strings = new ArrayList<String>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("String")) {
                	// Very unsafe assumption
                	String tmpString = ((Element) n).getAttribute("value");
                	tmpString = decodeString( tmpString );
                    strings.add( tmpString );
                }
            }
            return strings.toArray(new String[0]);

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public String[][] readStringArray2D(String name, String[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            NodeList nodes = mCurrentElement.getChildNodes();
            List<String[]> stringArrays = new ArrayList<String[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
            	Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                	// Very unsafe assumption
                    stringArrays.add(readStringArray(n.getNodeName(), null));
                }
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
            return stringArrays.toArray(new String[0][]);
            
        } catch (IOException ioe) {
            throw ioe;
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public BitSet readBitSet(String name, BitSet defVal) throws IOException {
        String tmpString = resolveAttribute( mCurrentElement, name );
        if ( tmpString == null ) return defVal;
        
        hasProcessed( name );
        try {
            BitSet set = new BitSet();
            String[] strings = parseTokens(tmpString);
            for (int i = 0; i < strings.length; i++) {
                int isSet = Integer.parseInt(strings[i]);
                if (isSet == 1) {
                        set.set(i);
                }
            }
            return set;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public Savable readSavable(
    	String name, Savable defVal
    ) throws IOException {
        Savable ret = defVal;

        Element tmpEl = null;
        if ( name != null ) {
        	// Locate the child of the given name
            hasProcessed( name );

            tmpEl = findChildElement( mCurrentElement, name );
            if ( tmpEl == null ) {
                String tmpString = resolveAttribute( mCurrentElement, name );
                if ( tmpString == null ) return defVal;

                // Use a proxy
                return( new SavableString( tmpString ) );
            }
        } else if ( mCurrentElement == mDocument.getDocumentElement() ) {
        	// At the very top of the tree, use it as is
            tmpEl = mDocument.getDocumentElement();
        } else {
        	// Assume we are interested in the first
            tmpEl = findFirstChildElement( mCurrentElement );
        }
        ret = readSavableFromElem( tmpEl, defVal, false );
        return ret;
    }

    @Override
    public Savable[] readSavableArray(
    	String name, Savable[] defVal
    ) throws IOException {
        Savable[] ret = defVal;

    	Element savedCurrentElement = mCurrentElement;
        Element tmpEl = findChildElement(mCurrentElement, name);
        if (tmpEl == null) {
            return defVal;
        }
        hasProcessed( name );

        List<Savable> savables = new ArrayList<Savable>();
        for (mCurrentElement = findFirstChildElement(tmpEl);
                mCurrentElement != null;
                mCurrentElement = findNextSiblingElement(mCurrentElement)) {
        	hasProcessed( mCurrentElement.getNodeName() );
            savables.add(readSavableFromCurrentElem(null));
        }
        ret = savables.toArray(new Savable[0]);
        mCurrentElement = savedCurrentElement;
        return ret;
    }

    @Override
    public Savable[][] readSavableArray2D(
    	String name, Savable[][] defVal
    ) throws IOException {
        Savable[][] ret = defVal;

    	Element savedCurrentElement = mCurrentElement;
        Element tmpEl = findChildElement(mCurrentElement, name);
        if (tmpEl == null) {
            return defVal;
        }
        hasProcessed( name );

        int size_outer = Integer.parseInt(tmpEl.getAttribute("size_outer"));
        int size_inner = Integer.parseInt(tmpEl.getAttribute("size_outer"));

        Savable[][] tmp = new Savable[size_outer][size_inner];
        mCurrentElement = findFirstChildElement(tmpEl);
        for (int i = 0; i < size_outer; i++) {
            for (int j = 0; j < size_inner; j++) {
            	hasProcessed( mCurrentElement.getNodeName() );
                tmp[i][j] = (readSavableFromCurrentElem(null));
                if (i == size_outer - 1 && j == size_inner - 1) {
                    break;
                }
                mCurrentElement = findNextSiblingElement(mCurrentElement);
            }
        }
        ret = tmp;
        mCurrentElement = savedCurrentElement;
        return ret;
    }

    @Override
    public ArrayList<Savable> readSavableArrayList(
    	String name, ArrayList defVal
    ) throws IOException {
    	Element savedCurrentElement = mCurrentElement;
        Element tmpEl = findChildElement(mCurrentElement, name);
        if (tmpEl == null) {
            return defVal;
        }
        hasProcessed( name );

        ArrayList<Savable> savables = new ArrayList<Savable>();
        for (mCurrentElement = findFirstChildElement(tmpEl);
                mCurrentElement != null;
                mCurrentElement = findNextSiblingElement(mCurrentElement)) {
        	hasProcessed( mCurrentElement.getNodeName() );
            savables.add(readSavableFromCurrentElem(null));
        }
        mCurrentElement = savedCurrentElement;
        return savables;
    }

    @Override
    public ArrayList<Savable>[] readSavableArrayListArray(
        String name, ArrayList[] defVal
    ) throws IOException {
    	Element savedCurrentElement = mCurrentElement;
        Element tmpEl = findChildElement(mCurrentElement, name);
        if (tmpEl == null) {
            return defVal;
        }
        hasProcessed( name );

        mCurrentElement = tmpEl;

        ArrayList<Savable> sal;
        List<ArrayList<Savable>> savableArrayLists = new ArrayList<ArrayList<Savable>>();
        int i = -1;
        while( (sal = readSavableArrayList ("SavableArrayList_" + ++i, null )) != null ) {
            savableArrayLists.add( sal );
        }
        mCurrentElement = savedCurrentElement;
        return savableArrayLists.toArray( new ArrayList[0] );
    }

    @Override
    public ArrayList<Savable>[][] readSavableArrayListArray2D(
    	String name, ArrayList[][] defVal
    ) throws IOException {
    	Element savedCurrentElement = mCurrentElement;
        Element tmpEl = findChildElement(mCurrentElement, name);
        if (tmpEl == null) {
            return defVal;
        }
        hasProcessed( name );

        mCurrentElement = tmpEl;
        ArrayList<Savable>[] arr;
        List<ArrayList<Savable>[]> sall = new ArrayList<ArrayList<Savable>[]>();
        int i = -1;
        while ((arr = readSavableArrayListArray( "SavableArrayListArray_" + ++i, null)) != null ) {
        	sall.add(arr);
        }
        mCurrentElement = savedCurrentElement;
        return sall.toArray(new ArrayList[0][]);
    }

    @Override
    public ArrayList<FloatBuffer> readFloatBufferArrayList(
            String name, ArrayList<FloatBuffer> defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            ArrayList<FloatBuffer> tmp = new ArrayList<FloatBuffer>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                tmp.add(readFloatBuffer(null, null));
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return tmp;

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public Map<? extends Savable, ? extends Savable> readSavableMap(String name, Map<? extends Savable, ? extends Savable> defVal) throws IOException {
        Map<Savable, Savable> ret;
        
        Element tempEl = findChildElement(mCurrentElement, name);
        hasProcessed( name );

        ret = new HashMap<Savable, Savable>();

        NodeList nodes = tempEl.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
            if (n instanceof Element && n.getNodeName().equals(XMLExporter.ELEMENT_MAPENTRY)) {
                Element elem = (Element) n;
                        mCurrentElement = elem;
                        Savable key = readSavable(XMLExporter.ELEMENT_KEY, null);
                        Savable val = readSavable(XMLExporter.ELEMENT_VALUE, null);
                        ret.put(key, val);
                }
        }
        mCurrentElement = (Element) tempEl.getParentNode();
        return ret;
    }

    @Override
    public Map<String, ? extends Savable> readStringSavableMap(
    	String name
    , 	Map<String, ? extends Savable> defVal
    ) throws IOException {
        Element tempEl = findChildElement(mCurrentElement, name);

        if (tempEl != null) {
        	// Mark the name as being processed
            hasProcessed( name );
            
            // Read the map
            Map<String, ? extends Savable> ret = readStringSavableMap( tempEl );
            return ret;
        } else {
        	// No such item
        	return defVal;
        }
    }
    public Map<String, ? extends Savable> readStringSavableMap(
    	Element pContext
    ) throws IOException {
    	Element savedCurrentElement = mCurrentElement;
    	Map<String, Savable> ret = new HashMap<String, Savable>();

        NodeList nodes = pContext.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node childNode = nodes.item(i);
            if ( childNode instanceof Element ) {
            	String nodeName = childNode.getNodeName();
            	if ( nodeName.equals(XMLExporter.ELEMENT_MAPENTRY ) ) {
            		// Old style mapping
                    Element elem = (Element)childNode;
                    mCurrentElement = elem;
                    String key = mCurrentElement.getAttribute("key");
                    Savable val = readSavable("Savable", null);
                    ret.put(key, val);
            	} else {
            		// Simpler mapping based on nodename and 'value' or first child
            		mCurrentElement = (Element)childNode;
            		Savable aValue;
            		String aString = mCurrentElement.getAttribute( "value" );
            		if ( aString.isEmpty() ) {
            			// NOT a simple attribute string, look for a full definition
                		aValue = readSavable( "value", null );
                		if ( aValue == null ) {
                			// Nothing for 'value=', is the element itself a Savable?
                			aValue = readSavableFromElem( mCurrentElement, null, true );
                			
                			if ( aValue == null ) {
	                			// How about the first child?
	                			Element tempE2 = findFirstChildElement( mCurrentElement );
	                			if ( tempE2 != null ) {
	                				aValue = readSavableFromElem( tempE2, null, true );
	                			}
                			}
                		}
            		} else {
            			// Support the string as a proxy for the savable
            			aValue = new SavableString( decodeString( aString ) );
            		}
                	if ( aValue != null ) {
                		// Use the node name as the key string
                		ret.put( nodeName, aValue );
                	} else {
    	        		throw new IOException( "readStringSavableMap - Missing Savable for: " + nodeName );
                	}
            	}
            }
        }
        mCurrentElement = savedCurrentElement;
        return ret;
    }

    @Override
    public IntMap<? extends Savable> readIntSavableMap(String name, IntMap<? extends Savable> defVal) throws IOException {
        IntMap<Savable> ret = null;
        Element tempEl = findChildElement(mCurrentElement, name);

        if (tempEl != null) {
            hasProcessed( name );

            ret = new IntMap<Savable>();

            NodeList nodes = tempEl.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().equals("MapEntry")) {
                    Element elem = (Element) n;
                    mCurrentElement = elem;
                    int key = Integer.parseInt(mCurrentElement.getAttribute("key"));
                    Savable val = readSavable("Savable", null);
                    ret.put(key, val);
                }
            }
        } else {
                return defVal;
            }
        mCurrentElement = (Element) tempEl.getParentNode();
        return ret;
    }

    @Override
    public FloatBuffer readFloatBuffer(String name, FloatBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);

            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            FloatBuffer tmp = BufferUtils.createFloatBuffer(strings.length);
            for (String s : strings) tmp.put(Float.parseFloat(s));
            tmp.flip();
            return tmp;

        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public IntBuffer readIntBuffer(String name, IntBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            IntBuffer tmp = BufferUtils.createIntBuffer(strings.length);
            for (String s : strings) tmp.put(Integer.parseInt(s));
            tmp.flip();
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public ByteBuffer readByteBuffer(String name, ByteBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            ByteBuffer tmp = BufferUtils.createByteBuffer(strings.length);
            for (String s : strings) tmp.put(Byte.valueOf(s));
            tmp.flip();
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public ShortBuffer readShortBuffer(String name, ShortBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            ShortBuffer tmp = BufferUtils.createShortBuffer(strings.length);
            for (String s : strings) tmp.put(Short.valueOf(s));
            tmp.flip();
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public ArrayList<ByteBuffer> readByteBufferArrayList(String name, ArrayList<ByteBuffer> defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            hasProcessed( name );

            ArrayList<ByteBuffer> tmp = new ArrayList<ByteBuffer>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                tmp.add(readByteBuffer(null, null));
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return tmp;
            
        } catch (NumberFormatException nfe) {
            IOException io = new IOException(nfe.toString());
            io.initCause(nfe);
            throw io;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public <T extends Enum<T>> T readEnum(
    	String 		name
    , 	Class<T> 	enumType
    , 	T 			defVal
    ) throws IOException {
        T ret = defVal;
        try {
            String eVal = resolveAttribute( mCurrentElement, name );
            if ( eVal != null ) {
                hasProcessed( name );
                ret = Enum.valueOf( enumType, eVal );
            }
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
        return ret;
    }


}
/** Helper class to support 'internal' definitions for substitutions */
class XMLInputCapsuleBundle
	extends ResourceBundle
{
	/** The set of entries */
	protected Hashtable<String,Object>	mEntries;
	/** Cached set of keys */
	protected Set<String>				mKeys;
	
	/** Standard constructor */
	XMLInputCapsuleBundle(
	) {
		mEntries = new Hashtable( 11 );
	}
	
	/** Define a new entry */
	public void put(
		String		pKey
	,	Object		pValue
	) {
		mEntries.put( pKey, pValue );
		
		// Force refresh of key set
		mKeys = null;
	}
	
	/** List the keys */
	@Override
    public Enumeration<String> getKeys(
    ) { 
		return mEntries.keys(); 
	}
    
    /** Locate the given key */
    @Override
    protected Object handleGetObject(
    	String	pKey
    ) { 
    	return mEntries.get( pKey ); 
    }
    
    /** Keep the key set fresh */
    @Override
    protected Set<String> handleKeySet(
    ) {
    	if ( mKeys == null ) {
    		mKeys = mEntries.keySet();
    	}
    	return mKeys;
    }

};

