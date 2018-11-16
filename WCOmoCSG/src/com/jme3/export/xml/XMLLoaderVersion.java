package com.jme3.export.xml;

import java.util.logging.Level;
import java.util.logging.Logger;

public class XMLLoaderVersion 
{
	/** Version tracking support */
	public static final int sVersionMajor = 0;
	public static final int sVersionMinor = 91;
	// NOTE that Rev and Date are auto-filled by SVN processing based on SVN keywords 
	public static final String sRevision="$Rev: $";
	public static final String sDate="$Date: $";

	public static void reportVersion(
		Logger		pLogger
	) {
		pLogger.log( Level.INFO, "XMLLoader: v." + sVersionMajor + "." + sVersionMinor
								+ ", " + sDate );
	}
}
