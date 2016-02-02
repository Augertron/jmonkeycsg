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
package net.wcomohundro.jme3.csg.test;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.AppState;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.asset.NonCachingKey;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.blender.math.Vector3d;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.PointLightShadowFilter;
import com.jme3.shadow.PointLightShadowRenderer;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGGeometry;
import net.wcomohundro.jme3.csg.CSGGeonode;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGSpatial;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.iob.CSGEnvironmentIOB;
import net.wcomohundro.jme3.csg.iob.CSGFace;
import net.wcomohundro.jme3.csg.iob.CSGVertexIOB;
import net.wcomohundro.jme3.csg.shape.*;

/** Simple test of the CSG support 
 		Test case taken from CSG forum on jme -- remove a randomly positioned sphere from a cube
 */
public class CSGTestM 
	extends CSGTestSceneBase
	implements Runnable
{
	public static void main(
		String[] 	pArgs
	) {
	    SimpleApplication app = new CSGTestM();		    
	    app.start();
	}

	protected static List<Vector3f> 	sPositions = new ArrayList<Vector3f>();
	static {
/*** Initial test set
		sPositions.add( new Vector3f(0.80005103f,0.06314170f,0.37440395f));
		sPositions.add( new Vector3f(0.28343803f,0.43108004f,0.48746461f));
		sPositions.add( new Vector3f(0.12868416f,0.01397777f,0.45756876f));
		sPositions.add( new Vector3f(0.77755153f,0.57811391f,0.31153154f));
		sPositions.add( new Vector3f(0.54848605f,0.43463808f,0.08887690f));
		sPositions.add( new Vector3f(0.44293296f,0.26147729f,0.59465259f));
		sPositions.add( new Vector3f(0.49685276f,0.70028597f,0.25030762f));
		sPositions.add( new Vector3f(0.69402820f,0.70010322f,0.17900819f));
		sPositions.add( new Vector3f(0.60632563f,0.76545787f,0.00868839f));
		sPositions.add( new Vector3f(0.07231724f,0.82769102f,0.52386624f));
		sPositions.add( new Vector3f(0.57993245f,0.11181229f,0.35487765f));
		sPositions.add( new Vector3f(0.76333338f,0.57130849f,0.41553390f));
		sPositions.add( new Vector3f(0.90540063f,0.15399516f,0.31880611f));
		sPositions.add( new Vector3f(0.48946434f,0.56392515f,0.92612267f));
		sPositions.add( new Vector3f(0.20164603f,0.28323406f,0.33062303f));
		sPositions.add( new Vector3f(0.48186016f,0.83080268f,0.67133898f));
		sPositions.add( new Vector3f(0.46120077f,0.96177906f,0.63590240f));
		sPositions.add( new Vector3f(0.24107927f,0.82240766f,0.69494921f));
		sPositions.add( new Vector3f(0.80022192f,0.86946529f,0.77864534f));
		sPositions.add( new Vector3f(0.21218884f,0.17488194f,0.89337271f));
		sPositions.add( new Vector3f(0.97576815f,0.74024606f,0.29970086f));
		sPositions.add( new Vector3f(0.17829001f,0.22013688f,0.41068947f));
		sPositions.add( new Vector3f(0.86896533f,0.60720319f,0.40294641f));
		sPositions.add( new Vector3f(0.60520381f,0.94561607f,0.30677772f));
		sPositions.add( new Vector3f(0.79588902f,0.16585821f,0.37771285f));
		sPositions.add( new Vector3f(0.74539816f,0.76406616f,0.57494253f));
		sPositions.add( new Vector3f(0.27781421f,0.94732559f,0.99107915f));
		sPositions.add( new Vector3f(0.55746430f,0.05100375f,0.81786209f));
		sPositions.add( new Vector3f(0.19038093f,0.98681915f,0.40325123f));
		sPositions.add( new Vector3f(0.64273006f,0.25286716f,0.04005140f));
		sPositions.add( new Vector3f(0.50846046f,0.35735011f,0.22371590f));
		sPositions.add( new Vector3f(0.04853362f,0.85462672f,0.32378966f));
		sPositions.add( new Vector3f(0.43576503f,0.99821311f,0.55919290f));
		sPositions.add( new Vector3f(0.33736432f,0.96088225f,0.70555288f));
		sPositions.add( new Vector3f(0.26486641f,0.21563309f,0.56654203f));
		sPositions.add( new Vector3f(0.39975405f,0.90919274f,0.80785227f));
		sPositions.add( new Vector3f(0.54797322f,0.55813938f,0.40844613f));
****/		
		
/********
		// The following includes a partial sphere in the middle of open
		// space around step 320....
		// A triangle is dropped around step 40 .... (which was pruned back to fail on step four)
		sPositions.add( new Vector3f(0.05513537f,0.88032979f,0.10482091f)); // 1
		sPositions.add( new Vector3f(0.01727545f,0.81447572f,0.25078678f)); // 2
//		sPositions.add( new Vector3f(0.00964278f,0.75274724f,0.49159241f)); // 3
//		sPositions.add( new Vector3f(0.34691769f,0.09261131f,0.60147893f)); // 4
//		sPositions.add( new Vector3f(0.69540179f,0.69519514f,0.91767484f)); // 5
//		sPositions.add( new Vector3f(0.45522231f,0.25658977f,0.15800303f)); // 6
//		sPositions.add( new Vector3f(0.68503022f,0.10441309f,0.19599450f)); // 7
//		sPositions.add( new Vector3f(0.27355307f,0.05526632f,0.27010518f)); // 8
//		sPositions.add( new Vector3f(0.88642603f,0.56086296f,0.32621509f)); // 9
//		sPositions.add( new Vector3f(0.49355364f,0.77155322f,0.17881960f)); // 10
//		sPositions.add( new Vector3f(0.53787941f,0.13245857f,0.50564462f)); // 11
//		sPositions.add( new Vector3f(0.05438501f,0.99762332f,0.58884567f)); // 12
//		sPositions.add( new Vector3f(0.68432063f,0.04203987f,0.19327503f)); // 13
//		sPositions.add( new Vector3f(0.25664705f,0.45784527f,0.30261987f)); // 14
//		sPositions.add( new Vector3f(0.51876885f,0.11807352f,0.15427589f)); // 15
//		sPositions.add( new Vector3f(0.43090326f,0.61390901f,0.23078674f)); // 16
//		sPositions.add( new Vector3f(0.63003170f,0.78192043f,0.12087834f)); // 17
//		sPositions.add( new Vector3f(0.77975059f,0.84080863f,0.38836056f)); // 18
		sPositions.add( new Vector3f(0.00496906f,0.86867005f,0.15753710f)); // 19
//		sPositions.add( new Vector3f(0.09614569f,0.26102722f,0.69942343f)); // 20
//		sPositions.add( new Vector3f(0.20217830f,0.42087638f,0.33125544f)); // 21
//		sPositions.add( new Vector3f(0.34031171f,0.54910898f,0.82116657f)); // 22
		//sPositions.add( new Vector3f(0.16091627f,0.47485572f,0.02282894f)); // 23
//		sPositions.add( new Vector3f(0.85077649f,0.79833710f,0.61496258f)); // 24
//		sPositions.add( new Vector3f(0.65552402f,0.07291752f,0.35230213f)); // 25
//		sPositions.add( new Vector3f(0.47137702f,0.67304271f,0.85828179f)); // 26
//		sPositions.add( new Vector3f(0.01689065f,0.91937882f,0.32014155f)); // 27
//		sPositions.add( new Vector3f(0.73249668f,0.59019595f,0.15075213f)); // 28
//		sPositions.add( new Vector3f(0.47108001f,0.85620421f,0.29385579f)); // 29
//		sPositions.add( new Vector3f(0.73933154f,0.99609506f,0.40732390f)); // 30
//		sPositions.add( new Vector3f(0.85529649f,0.99524736f,0.85486835f)); // 31
//		sPositions.add( new Vector3f(0.06391132f,0.37001842f,0.87378722f)); // 32
//		sPositions.add( new Vector3f(0.09235853f,0.75912493f,0.80872524f)); // 33
//		sPositions.add( new Vector3f(0.02314013f,0.93846476f,0.27705157f)); // 34
//		sPositions.add( new Vector3f(0.58085811f,0.18888384f,0.31971484f)); // 35
//		sPositions.add( new Vector3f(0.78751755f,0.98498267f,0.98273730f)); // 36
//		sPositions.add( new Vector3f(0.42793310f,0.86566174f,0.85597354f)); // 37
		sPositions.add( new Vector3f(0.01277292f,0.84769166f,0.02223247f)); // 38
		sPositions.add( new Vector3f(0.21314627f,0.05904144f,0.80193853f)); // 39
		sPositions.add( new Vector3f(0.97566402f,0.19212711f,0.56259418f)); // 40
		sPositions.add( new Vector3f(0.65481883f,0.34751093f,0.24168313f)); // 41
		sPositions.add( new Vector3f(0.33052021f,0.12427908f,0.75739115f)); // 42
		sPositions.add( new Vector3f(0.23592401f,0.40129310f,0.05710280f)); // 43
		sPositions.add( new Vector3f(0.35897756f,0.30885029f,0.27300751f)); // 44
		sPositions.add( new Vector3f(0.84873444f,0.19966125f,0.39437073f)); // 45
		sPositions.add( new Vector3f(0.68468893f,0.03339911f,0.73629576f)); // 46
		sPositions.add( new Vector3f(0.44850886f,0.32358396f,0.50102943f)); // 47
		sPositions.add( new Vector3f(0.80905706f,0.45503235f,0.87814331f)); // 48
		sPositions.add( new Vector3f(0.63140577f,0.10416991f,0.65027869f)); // 49
		sPositions.add( new Vector3f(0.88935548f,0.17837226f,0.94144696f)); // 50
		sPositions.add( new Vector3f(0.04223430f,0.69737887f,0.43428874f)); // 51
		sPositions.add( new Vector3f(0.24976748f,0.60331196f,0.21436733f)); // 52
		sPositions.add( new Vector3f(0.39394099f,0.06641769f,0.36173356f)); // 53
		sPositions.add( new Vector3f(0.25943804f,0.79548889f,0.36639076f)); // 54
		sPositions.add( new Vector3f(0.64456630f,0.20246375f,0.91185755f)); // 55
		sPositions.add( new Vector3f(0.62517208f,0.59350681f,0.81747103f)); // 56
		sPositions.add( new Vector3f(0.30804980f,0.20123655f,0.70530850f)); // 57
		sPositions.add( new Vector3f(0.63472164f,0.86760098f,0.42857718f)); // 58
		sPositions.add( new Vector3f(0.98078394f,0.99698627f,0.76484501f)); // 59
		sPositions.add( new Vector3f(0.59934020f,0.50368285f,0.21715748f)); // 60
		sPositions.add( new Vector3f(0.64947671f,0.73443222f,0.13317740f)); // 61
		sPositions.add( new Vector3f(0.15139073f,0.68737143f,0.95630693f)); // 62
		sPositions.add( new Vector3f(0.96652812f,0.54772842f,0.72867239f)); // 63
		sPositions.add( new Vector3f(0.24165642f,0.31273896f,0.31109405f)); // 64
		sPositions.add( new Vector3f(0.35331500f,0.51919276f,0.88022989f)); // 65
		sPositions.add( new Vector3f(0.40121591f,0.86859459f,0.24398226f)); // 66
		sPositions.add( new Vector3f(0.50399810f,0.21314645f,0.13688499f)); // 67
		sPositions.add( new Vector3f(0.30790389f,0.06824666f,0.69640404f)); // 68
		sPositions.add( new Vector3f(0.91859519f,0.09687066f,0.88251531f)); // 69
		sPositions.add( new Vector3f(0.06732333f,0.89095014f,0.66874236f)); // 70
		sPositions.add( new Vector3f(0.97204810f,0.13203216f,0.70286059f)); // 71
		sPositions.add( new Vector3f(0.96874744f,0.89120930f,0.03976709f)); // 72
		sPositions.add( new Vector3f(0.04630119f,0.11723185f,0.37255782f)); // 73
		sPositions.add( new Vector3f(0.75632286f,0.35214937f,0.57129508f)); // 74
		sPositions.add( new Vector3f(0.50969344f,0.91674083f,0.08090138f)); // 75
		sPositions.add( new Vector3f(0.17911822f,0.13079226f,0.66945761f)); // 76
		sPositions.add( new Vector3f(0.79423028f,0.19341350f,0.45908391f)); // 77
		sPositions.add( new Vector3f(0.04874396f,0.02077711f,0.39644349f)); // 78
		sPositions.add( new Vector3f(0.24924034f,0.64919019f,0.01828563f)); // 79
		sPositions.add( new Vector3f(0.89959973f,0.02321768f,0.46918595f)); // 80
		sPositions.add( new Vector3f(0.67423987f,0.52985305f,0.25962967f)); // 81
		sPositions.add( new Vector3f(0.73686135f,0.15920740f,0.19587654f)); // 82
		sPositions.add( new Vector3f(0.88694674f,0.03200811f,0.37241763f)); // 83
		sPositions.add( new Vector3f(0.25880188f,0.92807651f,0.72439599f)); // 84
		sPositions.add( new Vector3f(0.40963125f,0.81503457f,0.89314884f)); // 85
		sPositions.add( new Vector3f(0.51909065f,0.09161651f,0.35808384f)); // 86
		sPositions.add( new Vector3f(0.53521824f,0.04243594f,0.26071382f)); // 87
		sPositions.add( new Vector3f(0.52783418f,0.90232497f,0.93097520f)); // 88
		sPositions.add( new Vector3f(0.96681893f,0.41211617f,0.53677917f)); // 89
		sPositions.add( new Vector3f(0.76558107f,0.59707558f,0.04794681f)); // 90
		sPositions.add( new Vector3f(0.00212675f,0.59338140f,0.37066579f)); // 91
		sPositions.add( new Vector3f(0.61766815f,0.23912096f,0.32278168f)); // 92
		sPositions.add( new Vector3f(0.72699332f,0.16579860f,0.15888673f)); // 93
		sPositions.add( new Vector3f(0.28718758f,0.92855936f,0.12123269f)); // 94
		sPositions.add( new Vector3f(0.23468232f,0.02924645f,0.51584297f)); // 95
		sPositions.add( new Vector3f(0.22011060f,0.02919871f,0.34099507f)); // 96
		sPositions.add( new Vector3f(0.06806040f,0.63899517f,0.73381197f)); // 97
		sPositions.add( new Vector3f(0.74079913f,0.40895468f,0.89581400f)); // 98
		sPositions.add( new Vector3f(0.05682313f,0.51308668f,0.95954490f)); // 99
		sPositions.add( new Vector3f(0.81536257f,0.60235244f,0.77243781f)); // 100
		sPositions.add( new Vector3f(0.27945852f,0.12315160f,0.16866910f)); // 101
		sPositions.add( new Vector3f(0.53353989f,0.99512333f,0.89027494f)); // 102
		sPositions.add( new Vector3f(0.86843216f,0.71992272f,0.71736175f)); // 103
		sPositions.add( new Vector3f(0.54515803f,0.35333019f,0.37744540f)); // 104
		sPositions.add( new Vector3f(0.27411401f,0.76602656f,0.69727647f)); // 105
		sPositions.add( new Vector3f(0.97653538f,0.58801329f,0.61934650f)); // 106
		sPositions.add( new Vector3f(0.15662831f,0.69663250f,0.91590309f)); // 107
		sPositions.add( new Vector3f(0.49108475f,0.34944850f,0.54672563f)); // 108
		sPositions.add( new Vector3f(0.43127525f,0.92430997f,0.66084778f)); // 109
		sPositions.add( new Vector3f(0.34999275f,0.40480989f,0.51139939f)); // 110
		sPositions.add( new Vector3f(0.43578249f,0.22418147f,0.54713470f)); // 111
		sPositions.add( new Vector3f(0.65307969f,0.82781512f,0.64144289f)); // 112
		sPositions.add( new Vector3f(0.58661878f,0.77895612f,0.86137509f)); // 113
		sPositions.add( new Vector3f(0.32392716f,0.41915369f,0.59246594f)); // 114
		sPositions.add( new Vector3f(0.18071061f,0.69976133f,0.38272476f)); // 115
		sPositions.add( new Vector3f(0.95283365f,0.21134967f,0.93637621f)); // 116
		sPositions.add( new Vector3f(0.46406931f,0.11123765f,0.67547047f)); // 117
		sPositions.add( new Vector3f(0.41597748f,0.76538068f,0.47978163f)); // 118
		sPositions.add( new Vector3f(0.97271150f,0.10583484f,0.96401924f)); // 119
		sPositions.add( new Vector3f(0.80988300f,0.26986820f,0.49168974f)); // 120
		sPositions.add( new Vector3f(0.04632342f,0.29934603f,0.90946388f)); // 121
		sPositions.add( new Vector3f(0.42677331f,0.43728334f,0.52082258f)); // 122
		sPositions.add( new Vector3f(0.13683790f,0.44563287f,0.07109326f)); // 123
		sPositions.add( new Vector3f(0.28282470f,0.11225420f,0.29825139f)); // 124
		sPositions.add( new Vector3f(0.63409454f,0.29033279f,0.68833017f)); // 125
		sPositions.add( new Vector3f(0.87600988f,0.48265249f,0.28584498f)); // 126
		sPositions.add( new Vector3f(0.72917444f,0.48020619f,0.80598742f)); // 127
		sPositions.add( new Vector3f(0.82206684f,0.57696748f,0.88255054f)); // 128
		sPositions.add( new Vector3f(0.82044649f,0.87111187f,0.15093797f)); // 129
		sPositions.add( new Vector3f(0.65487850f,0.89031696f,0.61694664f)); // 130
		sPositions.add( new Vector3f(0.62499249f,0.31495190f,0.85484016f)); // 131
		sPositions.add( new Vector3f(0.34088355f,0.47150940f,0.41247708f)); // 132
		sPositions.add( new Vector3f(0.77599216f,0.70532864f,0.19347876f)); // 133
		sPositions.add( new Vector3f(0.43330395f,0.22190183f,0.70799023f)); // 134
		sPositions.add( new Vector3f(0.37436378f,0.45224816f,0.87213695f)); // 135
		sPositions.add( new Vector3f(0.43473250f,0.84950149f,0.64129657f)); // 136
		sPositions.add( new Vector3f(0.11979491f,0.62971652f,0.20490432f)); // 137
		sPositions.add( new Vector3f(0.81843883f,0.78830034f,0.25005108f)); // 138
		sPositions.add( new Vector3f(0.31493992f,0.18444997f,0.55557895f)); // 139
		sPositions.add( new Vector3f(0.13063031f,0.88116813f,0.67521352f)); // 140
		sPositions.add( new Vector3f(0.05300593f,0.78304631f,0.58781213f)); // 141
		sPositions.add( new Vector3f(0.09330779f,0.39000714f,0.14844424f)); // 142
		sPositions.add( new Vector3f(0.46780205f,0.46540207f,0.44684219f)); // 143
		sPositions.add( new Vector3f(0.85406542f,0.81551850f,0.19547433f)); // 144
		sPositions.add( new Vector3f(0.21794993f,0.55125636f,0.69239169f)); // 145
		sPositions.add( new Vector3f(0.29502976f,0.70696044f,0.18129730f)); // 146
		sPositions.add( new Vector3f(0.96909559f,0.72057515f,0.94336164f)); // 147
		sPositions.add( new Vector3f(0.29502439f,0.41857874f,0.03889787f)); // 148
		sPositions.add( new Vector3f(0.07176030f,0.69151950f,0.61856085f)); // 149
		sPositions.add( new Vector3f(0.87866449f,0.76024610f,0.81688416f)); // 150
		sPositions.add( new Vector3f(0.10700238f,0.97205645f,0.37770879f)); // 151
		sPositions.add( new Vector3f(0.33117032f,0.14165372f,0.80529863f)); // 152
		sPositions.add( new Vector3f(0.13486665f,0.59293228f,0.59295315f)); // 153
		sPositions.add( new Vector3f(0.69782782f,0.19464946f,0.18771338f)); // 154
		sPositions.add( new Vector3f(0.51175535f,0.43543923f,0.29948545f)); // 155
		sPositions.add( new Vector3f(0.02834958f,0.08872920f,0.76511312f)); // 156
		sPositions.add( new Vector3f(0.79162651f,0.54864448f,0.40549618f)); // 157
		sPositions.add( new Vector3f(0.19354653f,0.71420521f,0.49325156f)); // 158
		sPositions.add( new Vector3f(0.17903471f,0.64603233f,0.56215894f)); // 159
		sPositions.add( new Vector3f(0.24912262f,0.74653709f,0.86866730f)); // 160
		sPositions.add( new Vector3f(0.93102539f,0.36940682f,0.71936423f)); // 161
		sPositions.add( new Vector3f(0.67951858f,0.90606660f,0.28909171f)); // 162
		sPositions.add( new Vector3f(0.95923603f,0.68101758f,0.30301589f)); // 163
		sPositions.add( new Vector3f(0.43097627f,0.97016710f,0.17730933f)); // 164
		sPositions.add( new Vector3f(0.46436250f,0.23689079f,0.14438891f)); // 165
		sPositions.add( new Vector3f(0.34050602f,0.02154738f,0.74667937f)); // 166
		sPositions.add( new Vector3f(0.00368696f,0.13584572f,0.03439081f)); // 167
		sPositions.add( new Vector3f(0.37873375f,0.10896057f,0.84783870f)); // 168
		sPositions.add( new Vector3f(0.03580874f,0.56130564f,0.40572405f)); // 169
		sPositions.add( new Vector3f(0.90091282f,0.45430285f,0.92207277f)); // 170
		sPositions.add( new Vector3f(0.68269622f,0.89529121f,0.51138067f)); // 171
		sPositions.add( new Vector3f(0.29124391f,0.56116951f,0.69142276f)); // 172
		sPositions.add( new Vector3f(0.23912889f,0.81574166f,0.03673387f)); // 173
		sPositions.add( new Vector3f(0.98693001f,0.61842257f,0.85171258f)); // 174
		sPositions.add( new Vector3f(0.36001188f,0.10456461f,0.77511650f)); // 175
		sPositions.add( new Vector3f(0.91090953f,0.78281069f,0.81700963f)); // 176
		sPositions.add( new Vector3f(0.60507011f,0.02262914f,0.98961723f)); // 177
		sPositions.add( new Vector3f(0.59661269f,0.43467081f,0.25425643f)); // 178
		sPositions.add( new Vector3f(0.47638625f,0.02046758f,0.30792993f)); // 179
		sPositions.add( new Vector3f(0.45556790f,0.58943754f,0.46200901f)); // 180
		sPositions.add( new Vector3f(0.06114572f,0.27841598f,0.77298003f)); // 181
		sPositions.add( new Vector3f(0.14088517f,0.47909504f,0.22601002f)); // 182
		sPositions.add( new Vector3f(0.93590176f,0.01790124f,0.89089972f)); // 183
		sPositions.add( new Vector3f(0.53461403f,0.89424819f,0.54897577f)); // 184
		sPositions.add( new Vector3f(0.56789672f,0.54310006f,0.99809611f)); // 185
		sPositions.add( new Vector3f(0.54214245f,0.12944335f,0.39856291f)); // 186
		sPositions.add( new Vector3f(0.24880010f,0.08860993f,0.88564509f)); // 187
		sPositions.add( new Vector3f(0.34337914f,0.15894264f,0.00982565f)); // 188
		sPositions.add( new Vector3f(0.07917029f,0.87305528f,0.02248400f)); // 189
		sPositions.add( new Vector3f(0.42757994f,0.40290093f,0.01547360f)); // 190
		sPositions.add( new Vector3f(0.13193512f,0.82674879f,0.92807680f)); // 191
		sPositions.add( new Vector3f(0.20933473f,0.30384415f,0.44675201f)); // 192
		sPositions.add( new Vector3f(0.78135979f,0.42947668f,0.12009382f)); // 193
		sPositions.add( new Vector3f(0.56777477f,0.05204016f,0.57420117f)); // 194
		sPositions.add( new Vector3f(0.84268206f,0.51395172f,0.62621903f)); // 195
		sPositions.add( new Vector3f(0.77776587f,0.56165230f,0.19387215f)); // 196
		sPositions.add( new Vector3f(0.95763820f,0.40094376f,0.85312784f)); // 197
		sPositions.add( new Vector3f(0.15787017f,0.35993737f,0.47325975f)); // 198
		sPositions.add( new Vector3f(0.88609087f,0.34369218f,0.79815966f)); // 199
		sPositions.add( new Vector3f(0.37957633f,0.36735737f,0.48247665f)); // 200
		sPositions.add( new Vector3f(0.56458449f,0.49662375f,0.06448191f)); // 201
		sPositions.add( new Vector3f(0.07385808f,0.73236537f,0.26548243f)); // 202
		sPositions.add( new Vector3f(0.52206397f,0.64386761f,0.11751980f)); // 203
		sPositions.add( new Vector3f(0.13569218f,0.37971592f,0.85671312f)); // 204
		sPositions.add( new Vector3f(0.26372683f,0.81546080f,0.81702334f)); // 205
		sPositions.add( new Vector3f(0.84917659f,0.95392525f,0.01838273f)); // 206
		sPositions.add( new Vector3f(0.76502657f,0.73509413f,0.09728211f)); // 207
		sPositions.add( new Vector3f(0.19629478f,0.82356465f,0.63111621f)); // 208
		sPositions.add( new Vector3f(0.82975960f,0.70463699f,0.26333576f)); // 209
		sPositions.add( new Vector3f(0.28076702f,0.98191303f,0.15335131f)); // 210
		sPositions.add( new Vector3f(0.04048818f,0.60191786f,0.02998686f)); // 211
		sPositions.add( new Vector3f(0.87342757f,0.64529121f,0.40648216f)); // 212
		sPositions.add( new Vector3f(0.46776152f,0.07534862f,0.59594667f)); // 213
		sPositions.add( new Vector3f(0.49688089f,0.56947529f,0.31763172f)); // 214
		sPositions.add( new Vector3f(0.99046993f,0.14117658f,0.50166690f)); // 215
		sPositions.add( new Vector3f(0.84631443f,0.27603328f,0.08043611f)); // 216
		sPositions.add( new Vector3f(0.06774676f,0.54257762f,0.08407408f)); // 217
		sPositions.add( new Vector3f(0.19367188f,0.30792099f,0.03688920f)); // 218
		sPositions.add( new Vector3f(0.92429638f,0.40657777f,0.14380145f)); // 219
		sPositions.add( new Vector3f(0.75332850f,0.81054467f,0.50228441f)); // 220
		sPositions.add( new Vector3f(0.05730093f,0.55451143f,0.60112840f)); // 221
		sPositions.add( new Vector3f(0.00070918f,0.87352639f,0.41127694f)); // 222
		sPositions.add( new Vector3f(0.33587307f,0.99608850f,0.41425335f)); // 223
		sPositions.add( new Vector3f(0.34565592f,0.01795000f,0.76139534f)); // 224
		sPositions.add( new Vector3f(0.57973951f,0.34237963f,0.76198953f)); // 225
		sPositions.add( new Vector3f(0.47813910f,0.17026913f,0.12317699f)); // 226
		sPositions.add( new Vector3f(0.66834152f,0.76820993f,0.98370749f)); // 227
		sPositions.add( new Vector3f(0.58737099f,0.10946119f,0.88178509f)); // 228
		sPositions.add( new Vector3f(0.82090104f,0.37794340f,0.76674610f)); // 229
		sPositions.add( new Vector3f(0.72581053f,0.53701586f,0.79855090f)); // 230
		sPositions.add( new Vector3f(0.93883193f,0.14827138f,0.12453973f)); // 231
		sPositions.add( new Vector3f(0.12633288f,0.57509816f,0.47456896f)); // 232
		sPositions.add( new Vector3f(0.57395852f,0.00825131f,0.65628970f)); // 233
		sPositions.add( new Vector3f(0.62715042f,0.39298016f,0.94378793f)); // 234
		sPositions.add( new Vector3f(0.94870645f,0.85453188f,0.77715307f)); // 235
		sPositions.add( new Vector3f(0.02084237f,0.56594712f,0.21059215f)); // 236
		sPositions.add( new Vector3f(0.50697136f,0.83951747f,0.96494740f)); // 237
		sPositions.add( new Vector3f(0.22217983f,0.33287728f,0.51314819f)); // 238
		sPositions.add( new Vector3f(0.02887368f,0.37850440f,0.42435968f)); // 239
		sPositions.add( new Vector3f(0.43856061f,0.31180120f,0.08839929f)); // 240
		sPositions.add( new Vector3f(0.06766105f,0.16739905f,0.66811872f)); // 241
		sPositions.add( new Vector3f(0.95344275f,0.25409710f,0.75481296f)); // 242
		sPositions.add( new Vector3f(0.70799029f,0.67127711f,0.80395710f)); // 243
		sPositions.add( new Vector3f(0.80635476f,0.41931671f,0.64261204f)); // 244
		sPositions.add( new Vector3f(0.80630225f,0.56224847f,0.00245458f)); // 245
		sPositions.add( new Vector3f(0.63877296f,0.15025395f,0.55199283f)); // 246
		sPositions.add( new Vector3f(0.36806679f,0.37883025f,0.89672410f)); // 247
		sPositions.add( new Vector3f(0.00145096f,0.75812727f,0.95401883f)); // 248
		sPositions.add( new Vector3f(0.94978112f,0.48716134f,0.73813128f)); // 249
		sPositions.add( new Vector3f(0.06314677f,0.58337379f,0.59470528f)); // 250
		sPositions.add( new Vector3f(0.06344801f,0.52994257f,0.65199906f)); // 251
		sPositions.add( new Vector3f(0.16385549f,0.09859920f,0.55887699f)); // 252
		sPositions.add( new Vector3f(0.94375384f,0.28527802f,0.03478062f)); // 253
		sPositions.add( new Vector3f(0.35010493f,0.94235224f,0.57228345f)); // 254
		sPositions.add( new Vector3f(0.84351069f,0.69013500f,0.96302658f)); // 255
		sPositions.add( new Vector3f(0.46515203f,0.97387594f,0.81908739f)); // 256
		sPositions.add( new Vector3f(0.06830442f,0.86164618f,0.26404321f)); // 257
		sPositions.add( new Vector3f(0.55751771f,0.59663033f,0.21036208f)); // 258
		sPositions.add( new Vector3f(0.42674834f,0.36846191f,0.43346483f)); // 259
		sPositions.add( new Vector3f(0.18656290f,0.08120853f,0.69941270f)); // 260
		sPositions.add( new Vector3f(0.09526348f,0.10420984f,0.45381165f)); // 261
		sPositions.add( new Vector3f(0.76331884f,0.19665068f,0.54233211f)); // 262
		sPositions.add( new Vector3f(0.13611263f,0.49947363f,0.23582178f)); // 263
		sPositions.add( new Vector3f(0.09602195f,0.26584524f,0.12952638f)); // 264
		sPositions.add( new Vector3f(0.80902362f,0.34772485f,0.04518306f)); // 265
		sPositions.add( new Vector3f(0.76912570f,0.18878877f,0.81674552f)); // 266
		sPositions.add( new Vector3f(0.00633842f,0.45333731f,0.64997393f)); // 267
		sPositions.add( new Vector3f(0.83576655f,0.01936752f,0.61853641f)); // 268
		sPositions.add( new Vector3f(0.37074190f,0.65255940f,0.86798853f)); // 269
		sPositions.add( new Vector3f(0.19265753f,0.18121713f,0.02097040f)); // 270
		sPositions.add( new Vector3f(0.71916139f,0.57030433f,0.78005213f)); // 271
		sPositions.add( new Vector3f(0.66381156f,0.91960287f,0.68323326f)); // 272
		sPositions.add( new Vector3f(0.32860607f,0.12574244f,0.97142226f)); // 273
		sPositions.add( new Vector3f(0.36719173f,0.99451816f,0.12897688f)); // 274
		sPositions.add( new Vector3f(0.98938310f,0.22321045f,0.12245089f)); // 275
		sPositions.add( new Vector3f(0.86918795f,0.40928048f,0.07854438f)); // 276
		sPositions.add( new Vector3f(0.22159171f,0.09196126f,0.70154625f)); // 277
		sPositions.add( new Vector3f(0.07596558f,0.97967792f,0.94830537f)); // 278
		sPositions.add( new Vector3f(0.61305857f,0.91961497f,0.53501165f)); // 279
		sPositions.add( new Vector3f(0.79596120f,0.29546291f,0.14380115f)); // 280
		sPositions.add( new Vector3f(0.39494765f,0.37456232f,0.12796104f)); // 281
		sPositions.add( new Vector3f(0.66382486f,0.70608306f,0.29822123f)); // 282
		sPositions.add( new Vector3f(0.16997063f,0.73404622f,0.22289234f)); // 283
		sPositions.add( new Vector3f(0.06810552f,0.81339967f,0.65006959f)); // 284
		sPositions.add( new Vector3f(0.84349620f,0.87741417f,0.19024575f)); // 285
		sPositions.add( new Vector3f(0.66429496f,0.17991990f,0.25736505f)); // 286
		sPositions.add( new Vector3f(0.33917081f,0.16549492f,0.39249980f)); // 287
		sPositions.add( new Vector3f(0.06900114f,0.67059547f,0.93496495f)); // 288
		sPositions.add( new Vector3f(0.67157334f,0.78357059f,0.28530127f)); // 289
		sPositions.add( new Vector3f(0.41873926f,0.91240221f,0.29381263f)); // 290
		sPositions.add( new Vector3f(0.60375595f,0.33504915f,0.56022155f)); // 291
		sPositions.add( new Vector3f(0.36770535f,0.22523290f,0.40945941f)); // 292
		sPositions.add( new Vector3f(0.45079213f,0.08122331f,0.14576125f)); // 293
		sPositions.add( new Vector3f(0.64088291f,0.02789688f,0.26168084f)); // 294
		sPositions.add( new Vector3f(0.37611645f,0.74702591f,0.26353168f)); // 295
		sPositions.add( new Vector3f(0.83067733f,0.58517283f,0.51794130f)); // 296
		sPositions.add( new Vector3f(0.26038510f,0.47602427f,0.88533610f)); // 297
		sPositions.add( new Vector3f(0.87459648f,0.09149009f,0.90395224f)); // 298
		sPositions.add( new Vector3f(0.02643645f,0.84212834f,0.28121048f)); // 299
		sPositions.add( new Vector3f(0.95312726f,0.58899033f,0.21434379f)); // 300
		sPositions.add( new Vector3f(0.19815081f,0.59543395f,0.79936022f)); // 301
		sPositions.add( new Vector3f(0.91971451f,0.89794987f,0.63811266f)); // 302
		sPositions.add( new Vector3f(0.12590277f,0.46543670f,0.89663684f)); // 303
		sPositions.add( new Vector3f(0.18880659f,0.17018366f,0.00402421f)); // 304
		sPositions.add( new Vector3f(0.38834709f,0.38863462f,0.61984086f)); // 305
		sPositions.add( new Vector3f(0.65844190f,0.35036606f,0.87594569f)); // 306
		sPositions.add( new Vector3f(0.27443373f,0.58217072f,0.24998850f)); // 307
		sPositions.add( new Vector3f(0.06043398f,0.75297123f,0.59813595f)); // 308
		sPositions.add( new Vector3f(0.01845926f,0.78743339f,0.31774199f)); // 309
		sPositions.add( new Vector3f(0.13658017f,0.06484038f,0.90722966f)); // 310
		sPositions.add( new Vector3f(0.94518125f,0.01128495f,0.23519045f)); // 311
		sPositions.add( new Vector3f(0.76400006f,0.99802166f,0.47374356f)); // 312
		sPositions.add( new Vector3f(0.28873682f,0.64153397f,0.52674842f)); // 313
		sPositions.add( new Vector3f(0.05677271f,0.06218123f,0.78432703f)); // 314
		sPositions.add( new Vector3f(0.79088092f,0.25657886f,0.56594640f)); // 315
		sPositions.add( new Vector3f(0.11723733f,0.24594188f,0.85252506f)); // 316
		sPositions.add( new Vector3f(0.73270577f,0.15702993f,0.20987600f)); // 317
		sPositions.add( new Vector3f(0.07698172f,0.99023467f,0.61531776f)); // 318
		sPositions.add( new Vector3f(0.18848920f,0.32700181f,0.71938676f)); // 319
		sPositions.add( new Vector3f(0.68327332f,0.40418237f,0.26158917f)); // 320
		sPositions.add( new Vector3f(0.58637935f,0.14078426f,0.20244640f)); // 321
		sPositions.add( new Vector3f(0.01259488f,0.69972742f,0.00135130f)); // 322
		sPositions.add( new Vector3f(0.48849034f,0.61647427f,0.16988271f)); // 323
		sPositions.add( new Vector3f(0.97197604f,0.21985835f,0.36409473f)); // 324
		sPositions.add( new Vector3f(0.46833771f,0.23570085f,0.81340796f)); // 325
		sPositions.add( new Vector3f(0.48543668f,0.65766650f,0.94259256f)); // 326
		sPositions.add( new Vector3f(0.44569886f,0.80745363f,0.15037769f)); // 327
		sPositions.add( new Vector3f(0.89019805f,0.97937948f,0.82174891f)); // 328
		sPositions.add( new Vector3f(0.84882212f,0.58234125f,0.24493229f)); // 329
		sPositions.add( new Vector3f(0.00239909f,0.90765458f,0.64921969f)); // 330
		sPositions.add( new Vector3f(0.77310586f,0.45466256f,0.67202914f)); // 331
		sPositions.add( new Vector3f(0.29384410f,0.70013845f,0.73701704f)); // 332
		sPositions.add( new Vector3f(0.69697464f,0.37983549f,0.36267853f)); // 333
		sPositions.add( new Vector3f(0.36967516f,0.90504348f,0.20145524f)); // 334
		sPositions.add( new Vector3f(0.23499566f,0.46324724f,0.53976440f)); // 335
		sPositions.add( new Vector3f(0.16462982f,0.02611572f,0.61550331f)); // 336
		sPositions.add( new Vector3f(0.41169584f,0.17189682f,0.40301746f)); // 337
		sPositions.add( new Vector3f(0.72930288f,0.19264102f,0.63703346f)); // 338
***************/
		/// Bogus triangle around step 96 (seen from below)
/*****
sPositions.add( new Vector3f(0.27770352f,0.46122688f,0.51517385f)); // 1
sPositions.add( new Vector3f(0.27608168f,0.34551549f,0.06539202f)); // 2
sPositions.add( new Vector3f(0.52630138f,0.03579539f,0.02474916f)); // 3
sPositions.add( new Vector3f(0.20595354f,0.66981322f,0.42971194f)); // 4
sPositions.add( new Vector3f(0.18904364f,0.52505225f,0.93140197f)); // 5
sPositions.add( new Vector3f(0.97935265f,0.68711704f,0.48158187f)); // 6
sPositions.add( new Vector3f(0.48596126f,0.23843467f,0.93357599f)); // 7
sPositions.add( new Vector3f(0.01421112f,0.27380580f,0.19773597f)); // 8
sPositions.add( new Vector3f(0.71298790f,0.87922210f,0.51476270f)); // 9
sPositions.add( new Vector3f(0.79485250f,0.75485551f,0.99515438f)); // 10
sPositions.add( new Vector3f(0.77157128f,0.77839476f,0.05931848f)); // 11
sPositions.add( new Vector3f(0.33765650f,0.29436308f,0.59538013f)); // 12
sPositions.add( new Vector3f(0.64518267f,0.11867213f,0.96176815f)); // 13
sPositions.add( new Vector3f(0.24848801f,0.15109754f,0.61993992f)); // 14
sPositions.add( new Vector3f(0.45582271f,0.61759496f,0.86084294f)); // 15
sPositions.add( new Vector3f(0.80071312f,0.78237444f,0.29684871f)); // 16
sPositions.add( new Vector3f(0.32384932f,0.78567892f,0.68176097f)); // 17
sPositions.add( new Vector3f(0.88186044f,0.89698583f,0.38646924f)); // 18
sPositions.add( new Vector3f(0.31246120f,0.99689889f,0.60366225f)); // 19
sPositions.add( new Vector3f(0.69722843f,0.17010564f,0.40289277f)); // 20
sPositions.add( new Vector3f(0.56737870f,0.51288295f,0.00321507f)); // 21
sPositions.add( new Vector3f(0.82534564f,0.57062274f,0.28261465f)); // 22
sPositions.add( new Vector3f(0.37348580f,0.81682342f,0.67368442f)); // 23
sPositions.add( new Vector3f(0.66826415f,0.99113899f,0.97949696f)); // 24
sPositions.add( new Vector3f(0.22438675f,0.61881346f,0.43422037f)); // 25
sPositions.add( new Vector3f(0.25544399f,0.43739003f,0.49165052f)); // 26
sPositions.add( new Vector3f(0.87480676f,0.11029208f,0.22472137f)); // 27
sPositions.add( new Vector3f(0.89608353f,0.91551888f,0.23170120f)); // 28
sPositions.add( new Vector3f(0.33723837f,0.05473995f,0.15784162f)); // 29
sPositions.add( new Vector3f(0.60865152f,0.61520189f,0.97737426f)); // 30
sPositions.add( new Vector3f(0.14142460f,0.39914387f,0.24599236f)); // 31
sPositions.add( new Vector3f(0.22566748f,0.41332263f,0.00950694f)); // 32
sPositions.add( new Vector3f(0.15485024f,0.57639825f,0.70119578f)); // 33
sPositions.add( new Vector3f(0.05290598f,0.79258788f,0.22659695f)); // 34
sPositions.add( new Vector3f(0.10861605f,0.91088176f,0.68035346f)); // 35
sPositions.add( new Vector3f(0.04377919f,0.44823188f,0.15094322f)); // 36
sPositions.add( new Vector3f(0.68920767f,0.66604137f,0.94466478f)); // 37
sPositions.add( new Vector3f(0.04464799f,0.50060058f,0.37275547f)); // 38
sPositions.add( new Vector3f(0.62931901f,0.45162231f,0.35753506f)); // 39
sPositions.add( new Vector3f(0.65202111f,0.51006013f,0.78109592f)); // 40
sPositions.add( new Vector3f(0.68563026f,0.31424040f,0.61688083f)); // 41
sPositions.add( new Vector3f(0.08739573f,0.56442362f,0.07361543f)); // 42
sPositions.add( new Vector3f(0.02775884f,0.40120864f,0.25368088f)); // 43
sPositions.add( new Vector3f(0.42561126f,0.08508533f,0.52417850f)); // 44
sPositions.add( new Vector3f(0.93444425f,0.77615744f,0.98109519f)); // 45
sPositions.add( new Vector3f(0.92854166f,0.68288749f,0.46841764f)); // 46
sPositions.add( new Vector3f(0.37007278f,0.39217353f,0.14524925f)); // 47
sPositions.add( new Vector3f(0.14926779f,0.44578153f,0.17686743f)); // 48
sPositions.add( new Vector3f(0.87827176f,0.81727195f,0.18523437f)); // 49
sPositions.add( new Vector3f(0.11627805f,0.33141774f,0.59711856f)); // 50
sPositions.add( new Vector3f(0.02587420f,0.94024926f,0.71002156f)); // 51
sPositions.add( new Vector3f(0.44550377f,0.82438296f,0.62283421f)); // 52
sPositions.add( new Vector3f(0.72342467f,0.46676254f,0.39422596f)); // 53
sPositions.add( new Vector3f(0.92897040f,0.50566000f,0.61273122f)); // 54
sPositions.add( new Vector3f(0.47882491f,0.65437132f,0.83060253f)); // 55
sPositions.add( new Vector3f(0.27306068f,0.57173127f,0.13774878f)); // 56
sPositions.add( new Vector3f(0.42655313f,0.59532160f,0.31875283f)); // 57
sPositions.add( new Vector3f(0.38511860f,0.54043925f,0.47768301f)); // 58
sPositions.add( new Vector3f(0.24471194f,0.82192814f,0.48830944f)); // 59
sPositions.add( new Vector3f(0.67278528f,0.54504466f,0.09745085f)); // 60
sPositions.add( new Vector3f(0.35128516f,0.65485710f,0.61607915f)); // 61
sPositions.add( new Vector3f(0.15284234f,0.09497422f,0.11146510f)); // 62
sPositions.add( new Vector3f(0.70446914f,0.65306264f,0.58151633f)); // 63
sPositions.add( new Vector3f(0.60797894f,0.51344681f,0.92530870f)); // 64
sPositions.add( new Vector3f(0.34852356f,0.07145715f,0.17945975f)); // 65
sPositions.add( new Vector3f(0.37254894f,0.80542964f,0.88890177f)); // 66
sPositions.add( new Vector3f(0.73616028f,0.39821291f,0.06801569f)); // 67
sPositions.add( new Vector3f(0.20351452f,0.91303581f,0.72320229f)); // 68
sPositions.add( new Vector3f(0.95461959f,0.21565855f,0.91324383f)); // 69
sPositions.add( new Vector3f(0.46340734f,0.40685672f,0.19976103f)); // 70
sPositions.add( new Vector3f(0.11011094f,0.90722120f,0.49331629f)); // 71
sPositions.add( new Vector3f(0.48457295f,0.46555865f,0.85865045f)); // 72
sPositions.add( new Vector3f(0.19199139f,0.71535790f,0.86834544f)); // 73
sPositions.add( new Vector3f(0.04115498f,0.82713747f,0.42915702f)); // 74
sPositions.add( new Vector3f(0.61800349f,0.06707484f,0.62543017f)); // 75
sPositions.add( new Vector3f(0.88783878f,0.43486172f,0.12229562f)); // 76
sPositions.add( new Vector3f(0.63693637f,0.49088907f,0.79137754f)); // 77
sPositions.add( new Vector3f(0.87867749f,0.78637511f,0.14664972f)); // 78
sPositions.add( new Vector3f(0.05955619f,0.38885713f,0.14290255f)); // 79
sPositions.add( new Vector3f(0.13591242f,0.11856502f,0.32858527f)); // 80
sPositions.add( new Vector3f(0.45130950f,0.12749821f,0.22140574f)); // 81
sPositions.add( new Vector3f(0.99816906f,0.39879960f,0.67688763f)); // 82
sPositions.add( new Vector3f(0.27599066f,0.87235457f,0.10976708f)); // 83
sPositions.add( new Vector3f(0.28443813f,0.34108049f,0.17074001f)); // 84
sPositions.add( new Vector3f(0.59220099f,0.64068037f,0.29883301f)); // 85
sPositions.add( new Vector3f(0.21032435f,0.69509870f,0.22718686f)); // 86
sPositions.add( new Vector3f(0.96133840f,0.07793272f,0.98381025f)); // 87
sPositions.add( new Vector3f(0.86025840f,0.55727190f,0.13104689f)); // 88
sPositions.add( new Vector3f(0.47240525f,0.90199143f,0.77609187f)); // 89
sPositions.add( new Vector3f(0.78712440f,0.55742973f,0.76311892f)); // 90
sPositions.add( new Vector3f(0.61108696f,0.10808033f,0.72990811f)); // 91
sPositions.add( new Vector3f(0.48142982f,0.81970024f,0.52060682f)); // 92
sPositions.add( new Vector3f(0.67186862f,0.77876258f,0.23121035f)); // 93
sPositions.add( new Vector3f(0.15493429f,0.40537888f,0.23185605f)); // 94
sPositions.add( new Vector3f(0.58995610f,0.33311659f,0.00705606f)); // 95
sPositions.add( new Vector3f(0.39417428f,0.47937870f,0.61971760f)); // 96
sPositions.add( new Vector3f(0.54585254f,0.68676496f,0.87139761f)); // 97
sPositions.add( new Vector3f(0.06776696f,0.87885374f,0.14896846f)); // 98
sPositions.add( new Vector3f(0.37081677f,0.18404460f,0.54669964f)); // 99
sPositions.add( new Vector3f(0.17017645f,0.54999292f,0.11128503f)); // 100
sPositions.add( new Vector3f(0.56053740f,0.78079951f,0.30992448f)); // 101
sPositions.add( new Vector3f(0.32001537f,0.44523436f,0.08620554f)); // 102
sPositions.add( new Vector3f(0.76590687f,0.03684270f,0.59364378f)); // 103
sPositions.add( new Vector3f(0.80683553f,0.18522841f,0.85459214f)); // 104
sPositions.add( new Vector3f(0.15438688f,0.41930842f,0.33797449f)); // 105
sPositions.add( new Vector3f(0.69640076f,0.19685644f,0.05853683f)); // 106
sPositions.add( new Vector3f(0.73739821f,0.09245175f,0.41357023f)); // 107
sPositions.add( new Vector3f(0.57426214f,0.51166886f,0.70436966f)); // 108
sPositions.add( new Vector3f(0.78882986f,0.07467782f,0.57291675f)); // 109
sPositions.add( new Vector3f(0.15640312f,0.40672356f,0.02575749f)); // 110
sPositions.add( new Vector3f(0.73176700f,0.54052198f,0.32932806f)); // 111
sPositions.add( new Vector3f(0.80149478f,0.98603159f,0.81871510f)); // 112
sPositions.add( new Vector3f(0.45161510f,0.24455494f,0.32157898f)); // 113
sPositions.add( new Vector3f(0.69247639f,0.65278059f,0.74903792f)); // 114
sPositions.add( new Vector3f(0.57290655f,0.11720842f,0.89983869f)); // 115
sPositions.add( new Vector3f(0.02047265f,0.66252160f,0.35670352f)); // 116
sPositions.add( new Vector3f(0.21933514f,0.60925192f,0.47442764f)); // 117
sPositions.add( new Vector3f(0.47672379f,0.40354192f,0.61204731f)); // 118
sPositions.add( new Vector3f(0.71098053f,0.22188783f,0.74071270f)); // 119
sPositions.add( new Vector3f(0.35400301f,0.96791077f,0.88958502f)); // 120
sPositions.add( new Vector3f(0.00277239f,0.11852777f,0.46382076f)); // 121
sPositions.add( new Vector3f(0.76961392f,0.95164806f,0.00021791f)); // 122
sPositions.add( new Vector3f(0.04299396f,0.94793499f,0.06643200f)); // 123
****/		

		// Infinite splits ????
		sPositions.add(new Vector3f(0.83990216f,0.01416303f,0.41397852f)); //1, 
		sPositions.add(new Vector3f(0.14809741f,0.30853847f,0.32987046f)); //2, 
		sPositions.add(new Vector3f(0.54515183f,0.29772508f,0.26086247f)); //3, 
		sPositions.add(new Vector3f(0.50555390f,0.28294140f,0.73018849f)); //4,
		sPositions.add(new Vector3f(0.67798418f,0.78858733f,0.27823582f)); //5, 
		sPositions.add(new Vector3f(0.00424497f,0.13861489f,0.73975629f)); //6,
		sPositions.add(new Vector3f(0.37070101f,0.58056056f,0.03278502f)); //7, 

	}
	
	public static List<Vector3d> sDebugFace = new ArrayList(3);
	public static List<Vector3d> sDebugFace2 = new ArrayList(3);
	public static Vector3d[] sDebugIntersection = new Vector3d[2];
	static {
		sDebugFace.add( new Vector3d( 0.8684289, 0.013462324, 0.6622519) );
		sDebugFace.add( new Vector3d( 0.8845707, 0.0631417, 0.6622519) );
		sDebugFace.add( new Vector3d( 0.9622432, 0.0631417, 0.62678003) );
		
		sDebugFace.add( new Vector3d( 0.8845707,  0.0631417,  0.6622519 ) );
		sDebugFace.add( new Vector3d( 0.93126726, 0.15847588, 0.62678003 ) );
		sDebugFace.add( new Vector3d( 0.9622432,  0.0631417,  0.62678003 ) );
		
//		sDebugFace2.add( new Vector3d( 0.15480220317840576, 0.0943608283996582, 0.169720858335495 ) );
//		sDebugFace2.add( new Vector3d( 0.10256612300872803, 0.09436081349849701, 0.169720858335495 ) );
//		sDebugFace2.add( new Vector3d( 0.17880430817604065, 0.16823174059391022, 0.2051926851272583 ) );
		
		// BEFORE INVERT
		sDebugFace2.add( new Vector3d(0.46167982, 0.9314737, 0.08121036 ) );
		sDebugFace2.add( new Vector3d(0.42916352, 0.95509815, 0.23121035) );
		sDebugFace2.add( new Vector3d(0.5020609, 0.95989114, 0.1483221) );
		// AFTER INVERT
		sDebugFace2.add( new Vector3d(0.5020609, 0.95989114, 0.1483221) );
		sDebugFace2.add( new Vector3d(0.42916352, 0.95509815, 0.23121035) );
		sDebugFace2.add( new Vector3d(0.46167982, 0.9314737, 0.08121036 ) );
			
		sDebugIntersection[0] 
			= new Vector3d( -0.22993217408657074, 0.6713560223579407, 0.02223246917128563);
		sDebugIntersection[1] 
				= new Vector3d(-0.19741584360599518, 0.6949805617332458, 0.1722324937582016);
	}
	
	protected ConstructiveSolidGeometry.CSGSpatial	mBlendedShape;
	protected Stack<CSGShape>	mPreviousShape;
	protected CSGShape			mSphere;
	protected int				mPositionIndex;
	protected boolean			mSingleStep;
	protected int				mAction;
	protected int				mCounter;

	public CSGTestM(
	) {
		//super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
		super( new FlyCamAppState() );
	}
	
    @Override
    protected void commonApplicationInit(
    ) {
		super.commonApplicationInit();    
	    flyCam.setMoveSpeed( 3 );			// Move a bit slower
		
		this.mPostText.push( "<SPC> to blend, <BKSPC> to rollback, QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
		
		CSGEnvironmentIOB myEnv = (CSGEnvironmentIOB)CSGEnvironment.resolveEnvironment( null, null );
//		myEnv.mStructuralDebug = false;
		
//		myEnv.mEpsilonBetweenPointsDbl = FastMath.FLT_EPSILON;			// 1.0e-7;
//		myEnv.mEpsilonOnPlaneDbl = 1.0e-8;					// 1.0e-7;
//		myEnv.mEpsilonNearZeroDbl = 1.0e-9; // FastMath.FLT_EPSILON; 	// 1.0e-7;			// 
		// Remember to keep the magnitude in range with NearZero, otherwise
		// points will appear to NOT be on their associated plane
//		myEnv.mRationalizeValues = false;
//		myEnv.mEpsilonMagnitudeRange = 30; 	// 22
		
		myEnv.mRemoveUnsplitFace = false;
		
		// The position index controls how many pre-canned motions we use
		//		-1 := no precanned,  0 := all precanned
		mPositionIndex = -1;
		mSingleStep = false;
		mPreviousShape = new Stack();
		
		// This is the progressive shape
		mBlendedShape = new CSGGeonode( "TheBlend" );
		mBlendedShape.forceSingleMaterial( false );
		mBlendedShape.deferSceneChanges( true );

	    //Material cubeMaterial = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
        Material cubeMaterial = assetManager.loadMaterial( "Textures/BrickWall/BrickWallRpt.xml"  );
	    mBlendedShape.setDefaultMaterial( cubeMaterial );
	  	
        Light aLight = new AmbientLight();
        aLight.setColor( ColorRGBA.White );
        rootNode.addLight( aLight );

	    // This is the starting base cube of cheese
	  	CSGShape aCube = new CSGShape( "Box", new CSGBox(1,1,1) );
	  	mBlendedShape.addShape(aCube);
	  	mPreviousShape.push( mBlendedShape.regenerate() );
	  	
	  	// This is the spherical mouse
	  	mSphere = new CSGShape( "Sphere", new CSGSphere( 5, 5, 0.3f ) );
	    Material sphereMaterial = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
	    mSphere.setMaterial( sphereMaterial );
	  	
	   	rootNode.attachChild( (Spatial)mBlendedShape );
    }
    @Override
    public void simpleUpdate(
    	float tpf
    ) {
    	super.simpleUpdate( tpf );
    	
        // Apply any deferred changes now
    	mBlendedShape.applySceneChanges();
    }

    protected void blendSphere(
    	int		pAction
    ) {
    	mCounter += 1;
    	mBlendedShape.addShape( mPreviousShape.peek() );
      	
    	if ( (mPositionIndex >= 0) && (mPositionIndex < sPositions.size() ) ) {
      		mSphere.setLocalTranslation( sPositions.get( mPositionIndex++ ) );
      	}else{
      		mSphere.setLocalTranslation( ((Spatial)mBlendedShape).getLocalTranslation() );
        	mSphere.move( new Vector3f(   FastMath.rand.nextFloat()
        								, FastMath.rand.nextFloat()
        								, FastMath.rand.nextFloat() ).multLocal(1f) );
      	}
      	System.out.println( "sPositions.add( new Vector3f" + dump( mSphere.getLocalTranslation())+"); // " + mCounter );
      	mBlendedShape.addShape( mSphere, CSGOperator.DIFFERENCE );
      	
      	try {
      		mPreviousShape.push( mBlendedShape.regenerate() );
    	    mPostText.push( "Rebuilt in " + (mBlendedShape.getShapeRegenerationNS() / 1000000) + "ms [" + mCounter + "]");    	    
    		mRefreshText = true;
      	} catch( Exception ex ){
      		ex.printStackTrace();
      		System.exit(1);
      	}
      	mBlendedShape.removeAllShapes();
    }
    protected String dump(
    	Vector3f pos
    ) {
		return String.format("(%01.8ff,%01.8ff,%01.8ff)",pos.x,pos.y,pos.z);
	}
    
    /** Service routine to activate the interactive listeners */
    @Override
    protected void createListeners(
    ) {
    	super.createListeners();
    	
    	final SimpleApplication thisApp = this;
        inputManager.addMapping( "blend", new KeyTrigger( KeyInput.KEY_SPACE ) );
        inputManager.addMapping( "prior", new KeyTrigger( KeyInput.KEY_BACK ) );
        
        ActionListener aListener = new ActionListener() {
            public void onAction(
                String      pName
            ,   boolean     pKeyPressed
            ,   float       pTimePerFrame
            ) {
                if ( pKeyPressed ) {
                    if ( pName.equals( "blend" ) ) {
                	    takeAction( 1 );
                    } else if ( pName.equals( "prior" ) ) {
                    	mCounter -= 1;
                    	mPositionIndex -= 1;
                    	mPreviousShape.pop();
                		CSGTestDriver.postText( thisApp, mTextDisplay, "** Seq: " + mCounter );
                    }
                } else {
                	if ( pName.equals( "blend" ) ) {
                		stopAction();
                	}
                }
            }
        };  
        inputManager.addListener( aListener, "blend" );
        inputManager.addListener( aListener, "prior" );
    }
    /** Service routine to trigger the action */
    protected void takeAction(
    	int		pAction
    ) {
        // Confirm we are NOT in the middle of a regen
    	if ( mAction == 0 ) synchronized( this ) {
    		mAction = pAction;
    		CSGTestDriver.postText( this, mTextDisplay, "** Rebuilding Shape" );
    		this.notifyAll();
    	}
    }
    protected void stopAction(
    ) {
    	if ( mAction > 0 ) synchronized( this ) {
    		mAction = 0;
    		this.notifyAll();
    	}
    }

    /////////////////////// Implement Runnable ////////////////
    public void run(
    ) {
    	boolean isActive = true;
    	while( isActive ) {
    		synchronized( this ) {
	    		if ( mAction == 0 ) try {
	    			this.wait();
	    		} catch( InterruptedException ex ) {
	    			isActive = false;
	    			break;
	    		}
    		}
        	// Blend another shape into the prior result
    		if ( (mAction > 0) && (mBlendedShape.getShapeRegenerationNS() >= 0) ) {
    			blendSphere( mAction );
    			
    			if ( mSingleStep ) {
    				mAction = 0;
    			}
    		} else try {
    			Thread.currentThread().sleep( 20l );
    		} catch( InterruptedException ex ) { 
    			isActive = false;
    		}
    	}
    }

}