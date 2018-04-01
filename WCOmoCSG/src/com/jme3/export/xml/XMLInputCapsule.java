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
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.export.SavableClassUtil;
import com.jme3.export.xml.XMLExporter;
import com.jme3.util.BufferUtils;
import com.jme3.util.IntMap;

/** This is a basic rip-off of the DOMInputCapsule, leveraging all the standard readBlah() support,
 	but providing extra XML processing services as well. 	
 	
 	The basic premise is that every element in the DOM represents some Savable (or item within a
 	Savable)  We know which 'current' element we are working on, and the parent .read() processing
 	directs us from item to item, each item becoming the 'current' as it is worked on.
 */
public class XMLInputCapsule 
	implements InputCapsule
{
    /** The importer that is driving this process */
    protected JmeImporter					mImporter;
    /** The DOM Document that is being processed */
    protected Document 						mDocument;
    /** The Element in the DOM that is actively being processed */
    protected Element 						mCurrentElement;
    /** Format version of the overall XML, as defined by format_version='123' in the XML itself */
    protected int							mFormatVersion;
    /** Library of registered values, as set by id='xxx', referenced by ref='xxx' */
    protected Map<String, Savable>			mReferencedSavables;
    /** Library of ResourceBundles used for string translation */
    protected Map<String,ResourceBundle>	mResourceBundles;
    /** Version info registered with the Savables, filled in dynamically as elements are processed */
    protected int[] 						mClassHierarchyVersions;
    /** The Savable that is actively being filled */
    private Savable 						mSavable;

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
        mFormatVersion = aVersion.equals("") ? 0 : Integer.parseInt( aVersion );
    }
    
    /** Allow predefined 'seed' values to be provided into the processing */
    public Map<String, Savable>	getReferencedSavables() { return mReferencedSavables; }
    public void seedReferencedSavables(
    	Map<String,Savable>	pSeedValues
    ) {
    	mReferencedSavables.putAll( pSeedValues );
    }
    
    /** Allow translation bundles to be defined */
    public void setResourceBundles( 
    	Map<String,ResourceBundle>	pBundles
    ) {
    	mResourceBundles = pBundles;
    }

    /** DOM service routine:  clean up and interpret a string */
    protected String decodeString(
    	String	pText
    ) {
        if ( pText == null ) {
            return null;
        }
        pText = pText.replaceAll("\\&quot;", "\"").replaceAll("\\&lt;", "<").replaceAll("\\&amp;", "&");
        
        if ( mResourceBundles != null ) {
        	// Look for dynamic substitutions of the form ${bundle.key.key.key}
        	int start = 0, prior = 0;
        	StringBuilder aBuffer = null;
        	
        	while( (start = pText.indexOf( "${", prior )) >= prior ) {
        		start += 2;
        		int end = pText.indexOf( "}", start );
        		int dot = pText.indexOf( ".", start );
        		
        		// We need a minimum of ${a.b}
        		if ( (end > start) && ((end-start) >= 5) && (dot > start+1) && (dot < end-1) ) {
        			// The a. portion picks the bundle
        			String libraryRef = pText.substring( start, dot );
        			ResourceBundle aBundle = mResourceBundles.get( libraryRef );
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

    /** DOM service routine:  scan for the first child Element within a parent */
    protected Element findFirstChildElement(
    	Element 	parent
    ) {
        Node ret = parent.getFirstChild();
        while (ret != null && (!(ret instanceof Element))) {
            ret = ret.getNextSibling();
        }
        return (Element) ret;
    }

    /** DOM service routine:  scan for the first child Element of a given name within a parent */
    protected Element findChildElement(
    	Element 	parent
    , 	String 		name
    ) {
        if (parent == null) {
            return null;
        }
        Node ret = parent.getFirstChild();
        while (ret != null && (!(ret instanceof Element) || !ret.getNodeName().equals(name))) {
            ret = ret.getNextSibling();
        }
        return (Element) ret;
    }
    
    /** DOM service routine:  scan for the next sibling Element after the given one */
    protected Element findNextSiblingElement(
    	Element 	pCurrent
    ) {
        Node ret = pCurrent.getNextSibling();
        while (ret != null) {
            if (ret instanceof Element) {
                return (Element) ret;
            }
            ret = ret.getNextSibling();
        }
        return null;
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
    	Savable defVal
    ) throws InstantiationException, ClassNotFoundException, IOException, IllegalAccessException {
        Savable ret = defVal;
        Savable tmp = null;

        if ( (mCurrentElement == null) || mCurrentElement.getNodeName().equals("null") ) {
            return null;
        }
        // A reference to a previously constructed item overrides all other processing
        String reference = mCurrentElement.getAttribute("ref");
        if (reference.length() > 0) {
        	// Look up the reference
            ret = mReferencedSavables.get(reference);
        } else {
        	// Process the current node, where the class='...' attribute tells us
        	// the class to instantiate, or else the node name is expected to be
        	// the class name.
            String className;
            if ( mCurrentElement.hasAttribute("class") ) {
            	// An explicit class attribute is the override
                className = mCurrentElement.getAttribute("class");
            } else if ( defVal != null ) {
            	// The given default value will direct the class to use
            	className = defVal.getClass().getName();
            } else {
            	// The node name itself is expected to direct the class
            	className = mCurrentElement.getNodeName();
            }
            // Instantiate the Savable instance
            tmp = SavableClassUtil.fromName( className, null );
            
            // Check for version constraints
            String versionsStr = mCurrentElement.getAttribute("savable_versions");
            if (versionsStr != null && !versionsStr.equals("")){
                String[] versionStr = versionsStr.split(",");
                mClassHierarchyVersions = new int[versionStr.length];
                for (int i = 0; i < mClassHierarchyVersions.length; i++){
                    mClassHierarchyVersions[i] = Integer.parseInt(versionStr[i].trim());
                }
            } else {
                mClassHierarchyVersions = null;
            }
            // If we have reference_ID='blah' or id='blah', then save this
            // instance for subsequent reference
            String refID = mCurrentElement.getAttribute( "reference_ID" );
            if (refID.length() < 1) refID = mCurrentElement.getAttribute("id");
            if (refID.length() > 0) mReferencedSavables.put( refID, tmp );
            
            if ( tmp != null ) {
                // We fill the empty instance constructed above by letting
            	// it read itself.  We save the current element 'context'
            	// within mSavable
                mSavable = tmp;
                tmp.read( mImporter );
                ret = tmp;
            }
        }
        return ret;
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
            return SavableClassUtil.getSavedSavableVersion(
            		mSavable, pDesiredClass, mClassHierarchyVersions, mFormatVersion );
        } else {
        	// Nothing in particular is known
            return 0;
        }
    }
        
    @Override
    public byte readByte(String name, byte defVal) throws IOException {
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of bytes for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains "
                            + strings.length);
            }
            byte[] tmp = new byte[strings.length];
            for (int i = 0; i < strings.length; i++) {
                tmp[i] = Byte.parseByte(strings[i]);
            }
            return tmp;
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
    public byte[][] readByteArray2D(String name, byte[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<byte[]> byteArrays = new ArrayList<byte[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    byteArrays.add(readByteArray(n.getNodeName(), null));
                                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (byteArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + byteArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of ints for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            int[] tmp = new int[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Integer.parseInt(strings[i]);
            }
            return tmp;
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
    public int[][] readIntArray2D(String name, int[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");

            NodeList nodes = mCurrentElement.getChildNodes();
            List<int[]> intArrays = new ArrayList<int[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                	// Very unsafe assumption
                    intArrays.add(readIntArray(n.getNodeName(), null));
                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (intArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + intArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
        try {
            return Float.parseFloat(tmpString);
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of floats for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            float[] tmp = new float[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Float.parseFloat(strings[i]);
            }
            return tmp;
        } catch (IOException ioe) {
            throw ioe;
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            int size_outer = Integer.parseInt(tmpEl.getAttribute("size_outer"));
            int size_inner = Integer.parseInt(tmpEl.getAttribute("size_outer"));

            float[][] tmp = new float[size_outer][size_inner];

            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            for (int i = 0; i < size_outer; i++) {
                tmp[i] = new float[size_inner];
                for (int k = 0; k < size_inner; k++) {
                    tmp[i][k] = Float.parseFloat(strings[i]);
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of doubles for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            double[] tmp = new double[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Double.parseDouble(strings[i]);
            }
            return tmp;
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
    public double[][] readDoubleArray2D(String name, double[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<double[]> doubleArrays = new ArrayList<double[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    doubleArrays.add(readDoubleArray(n.getNodeName(), null));
                                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (doubleArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + doubleArrays.size());
            }
            mCurrentElement = (Element) mCurrentElement.getParentNode();
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of longs for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            long[] tmp = new long[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Long.parseLong(strings[i]);
            }
            return tmp;
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
    public long[][] readLongArray2D(String name, long[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<long[]> longArrays = new ArrayList<long[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    longArrays.add(readLongArray(n.getNodeName(), null));
                                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (longArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + longArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
             Element tmpEl;
             if (name != null) {
                 tmpEl = findChildElement(mCurrentElement, name);
             } else {
                 tmpEl = mCurrentElement;
             }
             if (tmpEl == null) {
                 return defVal;
             }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of shorts for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            short[] tmp = new short[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Short.parseShort(strings[i]);
            }
            return tmp;
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
    public short[][] readShortArray2D(String name, short[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<short[]> shortArrays = new ArrayList<short[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().contains("array")) {
                // Very unsafe assumption
                    shortArrays.add(readShortArray(n.getNodeName(), null));
                                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (shortArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + shortArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens( tmpEl.getAttribute("data") );
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of bools for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            boolean[] tmp = new boolean[strings.length];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = Boolean.parseBoolean(strings[i]);
            }
            return tmp;
        } catch (IOException ioe) {
            throw ioe;
        } catch (DOMException de) {
            IOException io = new IOException(de.toString());
            io.initCause(de);
            throw io;
        }
    }

    @Override
    public boolean[][] readBooleanArray2D(String name, boolean[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<boolean[]> booleanArrays = new ArrayList<boolean[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                    // Very unsafe assumption
                    booleanArrays.add(readBooleanArray(n.getNodeName(), null));
                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (booleanArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + booleanArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
        tmpString = decodeString( tmpString );
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
             Element tmpEl;
             if (name != null) {
                 tmpEl = findChildElement(mCurrentElement, name);
             } else {
                 tmpEl = mCurrentElement;
             }
             if (tmpEl == null) {
                 return defVal;
             }
            String sizeString = tmpEl.getAttribute("size");
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
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + strings.size());
            }
            return strings.toArray(new String[0]);
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
    public String[][] readStringArray2D(String name, String[][] defVal) throws IOException {
        try {
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            NodeList nodes = mCurrentElement.getChildNodes();
            List<String[]> stringArrays = new ArrayList<String[]>();

            for (int i = 0; i < nodes.getLength(); i++) {
            	Node n = nodes.item(i);
                if (n instanceof Element && n.getNodeName().contains("array")) {
                	// Very unsafe assumption
                    stringArrays.add(readStringArray(n.getNodeName(), null));
                }
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (stringArrays.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + stringArrays.size());
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
        String tmpString = mCurrentElement.getAttribute(name);
        if (tmpString == null || tmpString.length() < 1) return defVal;
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
    public Savable readSavable(String name, Savable defVal) throws IOException {
        Savable ret = defVal;
        try {
            Element tmpEl = null;
            if ( name != null ) {
            	// Locate the child of the given name
                tmpEl = findChildElement(mCurrentElement, name);
                if (tmpEl == null) {
                    return defVal;
                }
            } else if ( mCurrentElement == mDocument.getDocumentElement() ) {
            	// At the very top of the tree, use it as is
                tmpEl = mDocument.getDocumentElement();
            } else {
            	// Assume we are interested in the first
                tmpEl = findFirstChildElement(mCurrentElement);
            }
            mCurrentElement = tmpEl;
            ret = readSavableFromCurrentElem(defVal);
            if (mCurrentElement.getParentNode() instanceof Element) {
                mCurrentElement = (Element) mCurrentElement.getParentNode();
            } else {
                mCurrentElement = null;
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
        return ret;
    }

    @Override
    public Savable[] readSavableArray(String name, Savable[] defVal) throws IOException {
        Savable[] ret = defVal;
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            List<Savable> savables = new ArrayList<Savable>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                savables.add(readSavableFromCurrentElem(null));
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (savables.size() != requiredSize)
                    throw new IOException("Wrong number of Savables for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + savables.size());
            }
            ret = savables.toArray(new Savable[0]);
            mCurrentElement = (Element) tmpEl.getParentNode();
            return ret;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
    }

    @Override
    public Savable[][] readSavableArray2D(String name, Savable[][] defVal) throws IOException {
        Savable[][] ret = defVal;
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            int size_outer = Integer.parseInt(tmpEl.getAttribute("size_outer"));
            int size_inner = Integer.parseInt(tmpEl.getAttribute("size_outer"));

            Savable[][] tmp = new Savable[size_outer][size_inner];
            mCurrentElement = findFirstChildElement(tmpEl);
            for (int i = 0; i < size_outer; i++) {
                for (int j = 0; j < size_inner; j++) {
                    tmp[i][j] = (readSavableFromCurrentElem(null));
                    if (i == size_outer - 1 && j == size_inner - 1) {
                        break;
                    }
                    mCurrentElement = findNextSiblingElement(mCurrentElement);
                }
            }
            ret = tmp;
            mCurrentElement = (Element) tmpEl.getParentNode();
            return ret;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
    }

    @Override
    public ArrayList<Savable> readSavableArrayList(String name, ArrayList defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            ArrayList<Savable> savables = new ArrayList<Savable>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                savables.add(readSavableFromCurrentElem(null));
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (savables.size() != requiredSize)
                    throw new IOException(
                            "Wrong number of Savable arrays for '" + name
                            + "'.  size says " + requiredSize
                            + ", data contains " + savables.size());
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return savables;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
    }

    @Override
    public ArrayList<Savable>[] readSavableArrayListArray(
            String name, ArrayList[] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            mCurrentElement = tmpEl;

            String sizeString = tmpEl.getAttribute("size");
            int requiredSize = (sizeString.length() > 0)
                             ? Integer.parseInt(sizeString)
                             : -1;

            ArrayList<Savable> sal;
            List<ArrayList<Savable>> savableArrayLists =
                    new ArrayList<ArrayList<Savable>>();
            int i = -1;
            while (true) {
                sal = readSavableArrayList("SavableArrayList_" + ++i, null);
                if (sal == null && savableArrayLists.size() >= requiredSize)
                    break;
                savableArrayLists.add(sal);
            }

            if (requiredSize > -1 && savableArrayLists.size() != requiredSize)
                throw new IOException(
                        "String array contains wrong element count.  "
                        + "Specified size " + requiredSize
                        + ", data contains " + savableArrayLists.size());
            mCurrentElement = (Element) tmpEl.getParentNode();
            return savableArrayLists.toArray(new ArrayList[0]);
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
    public ArrayList<Savable>[][] readSavableArrayListArray2D(String name, ArrayList[][] defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }
            mCurrentElement = tmpEl;
            String sizeString = tmpEl.getAttribute("size");

            ArrayList<Savable>[] arr;
            List<ArrayList<Savable>[]> sall = new ArrayList<ArrayList<Savable>[]>();
            int i = -1;
            while ((arr = readSavableArrayListArray(
                    "SavableArrayListArray_" + ++i, null)) != null) sall.add(arr);
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (sall.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + sall.size());
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return sall.toArray(new ArrayList[0][]);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
    }

    @Override
    public ArrayList<FloatBuffer> readFloatBufferArrayList(
            String name, ArrayList<FloatBuffer> defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            ArrayList<FloatBuffer> tmp = new ArrayList<FloatBuffer>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                tmp.add(readFloatBuffer(null, null));
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (tmp.size() != requiredSize)
                    throw new IOException(
                            "String array contains wrong element count.  "
                            + "Specified size " + requiredSize
                            + ", data contains " + tmp.size());
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return tmp;
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
    public Map<? extends Savable, ? extends Savable> readSavableMap(String name, Map<? extends Savable, ? extends Savable> defVal) throws IOException {
        Map<Savable, Savable> ret;
        Element tempEl;

        if (name != null) {
                tempEl = findChildElement(mCurrentElement, name);
        } else {
                tempEl = mCurrentElement;
        }
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
    public Map<String, ? extends Savable> readStringSavableMap(String name, Map<String, ? extends Savable> defVal) throws IOException {
        Map<String, Savable> ret = null;
        Element tempEl;

        if (name != null) {
                tempEl = findChildElement(mCurrentElement, name);
        } else {
                tempEl = mCurrentElement;
        }
        if (tempEl != null) {
                ret = new HashMap<String, Savable>();

                NodeList nodes = tempEl.getChildNodes();
                    for (int i = 0; i < nodes.getLength(); i++) {
                                Node n = nodes.item(i);
                                if (n instanceof Element && n.getNodeName().equals(XMLExporter.ELEMENT_MAPENTRY)) {
                                        Element elem = (Element) n;
                                        mCurrentElement = elem;
                                        String key = mCurrentElement.getAttribute("key");
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
    public IntMap<? extends Savable> readIntSavableMap(String name, IntMap<? extends Savable> defVal) throws IOException {
        IntMap<Savable> ret = null;
        Element tempEl;

        if (name != null) {
                tempEl = findChildElement(mCurrentElement, name);
        } else {
                tempEl = mCurrentElement;
        }
        if (tempEl != null) {
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
            Element tmpEl;
            if (name != null) {
                tmpEl = findChildElement(mCurrentElement, name);
            } else {
                tmpEl = mCurrentElement;
            }
            if (tmpEl == null) {
                return defVal;
            }
            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of float buffers for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            FloatBuffer tmp = BufferUtils.createFloatBuffer(strings.length);
            for (String s : strings) tmp.put(Float.parseFloat(s));
            tmp.flip();
            return tmp;
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
    public IntBuffer readIntBuffer(String name, IntBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of int buffers for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            IntBuffer tmp = BufferUtils.createIntBuffer(strings.length);
            for (String s : strings) tmp.put(Integer.parseInt(s));
            tmp.flip();
            return tmp;
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
    public ByteBuffer readByteBuffer(String name, ByteBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of byte buffers for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            ByteBuffer tmp = BufferUtils.createByteBuffer(strings.length);
            for (String s : strings) tmp.put(Byte.valueOf(s));
            tmp.flip();
            return tmp;
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
    public ShortBuffer readShortBuffer(String name, ShortBuffer defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            String[] strings = parseTokens(tmpEl.getAttribute("data"));
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (strings.length != requiredSize)
                    throw new IOException("Wrong number of short buffers for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + strings.length);
            }
            ShortBuffer tmp = BufferUtils.createShortBuffer(strings.length);
            for (String s : strings) tmp.put(Short.valueOf(s));
            tmp.flip();
            return tmp;
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
    public ArrayList<ByteBuffer> readByteBufferArrayList(String name, ArrayList<ByteBuffer> defVal) throws IOException {
        try {
            Element tmpEl = findChildElement(mCurrentElement, name);
            if (tmpEl == null) {
                return defVal;
            }

            String sizeString = tmpEl.getAttribute("size");
            ArrayList<ByteBuffer> tmp = new ArrayList<ByteBuffer>();
            for (mCurrentElement = findFirstChildElement(tmpEl);
                    mCurrentElement != null;
                    mCurrentElement = findNextSiblingElement(mCurrentElement)) {
                tmp.add(readByteBuffer(null, null));
            }
            if (sizeString.length() > 0) {
                int requiredSize = Integer.parseInt(sizeString);
                if (tmp.size() != requiredSize)
                    throw new IOException("Wrong number of short buffers for '"
                            + name + "'.  size says " + requiredSize
                            + ", data contains " + tmp.size());
            }
            mCurrentElement = (Element) tmpEl.getParentNode();
            return tmp;
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

    public <T extends Enum<T>> T readEnum(
    	String 		name
    , 	Class<T> 	enumType
    , 	T 			defVal
    ) throws IOException {
        T ret = defVal;
        try {
            String eVal = mCurrentElement.getAttribute(name);
            if (eVal != null && eVal.length() > 0) {
            	eVal = decodeString( eVal );
                ret = Enum.valueOf(enumType, eVal);
            }
        } catch (Exception e) {
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        }
        return ret;
    }


}
