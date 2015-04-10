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
**/
package net.wcomohundro.jme3.csg.shape;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Spline;
import com.jme3.math.Spline.SplineType;

/** Define a helper class that can construct a Spline for us, especially from .xml Savable files */
public class CSGSplineGenerator
	implements Savable
{
	/** The generated Spline */
	protected Spline	mSpline;
	
	
	/** Null constructor */
	public CSGSplineGenerator(
	) {
	}
	
	/** Constructor based on a given spline */
	public CSGSplineGenerator(
		Spline		pSpline
	) {
		mSpline = pSpline;
	}
	
	/** Accessor to the spline */
	public Spline getSpline() { return mSpline; }
	public void setSpline( Spline pSpline ) { mSpline = pSpline; }
	
	
	/** Implement minimal Savable */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.writeSavableArrayList( (ArrayList)mSpline.getControlPoints(), "controlPoints", null );
        outCapsule.write( mSpline.getType(), "type", SplineType.CatmullRom );
        outCapsule.write( mSpline.getCurveTension(), "curveTension", 0.5f );
        outCapsule.write( mSpline.isCycle(), "cycle", false );
    }

    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule in = pImporter.getCapsule(this);

        List<Vector3f> controlPoints = (ArrayList<Vector3f>)in.readSavableArrayList( "controlPoints", null );
        SplineType aType = in.readEnum( "type", SplineType.class, SplineType.CatmullRom );
        float curveTension = in.readFloat ("curveTension", 0.5f );
        boolean doCycle = in.readBoolean( "cycle", false );
        
        // Build the spline
        mSpline = new Spline( aType, controlPoints, curveTension, doCycle );
    }

}
