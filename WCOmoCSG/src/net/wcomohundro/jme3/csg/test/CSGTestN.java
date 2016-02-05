package net.wcomohundro.jme3.csg.test;

import java.util.ArrayList;
import java.util.Random;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import net.wcomohundro.jme3.csg.*;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGOperator;
import net.wcomohundro.jme3.csg.iob.CSGEnvironmentIOB;
import net.wcomohundro.jme3.csg.shape.*;

/** Sample from the jme forum -- I am not sure I plan on keeping it.... */
public class CSGTestN 
	extends SimpleApplication
{
	public static void main(String[] 	pArgs) {
	    SimpleApplication app = new CSGTestN();		    
	    app.start();
	}
	
	private boolean	bThreaded = false; // set to true to skip the exceptions and retry
	private int	iMaxSluggishThreads = Runtime.getRuntime().availableProcessors();
	
	private CSGGeometry	geom;
	private CSGShape	shapePrev;
	private CSGShape	shapeSphere;
	private CSGShape	shapeSphereI;
	private int iInteractionCount;
	private boolean bDancing=false;
	ArrayList<RThread> aSluggishThreads = new ArrayList<RThread>();
	ArrayList<Vector3f> apos = new ArrayList<Vector3f>();
	ArrayList<Vector3f> aposI = new ArrayList<Vector3f>();
	private Material	mat;
	private long	ltimep;
	private CSGGeometry	geomI;
	private BoundingBox	bbPrev = new BoundingBox();
	
	public CSGTestN() {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}
	
	@Override
	public void simpleInitApp(
	) {
	    flyCam.setMoveSpeed( 5 );
	    flyCam.setDragToRotate( true );		// Only use the mouse while it is clicked
	    
	    CSGEnvironmentIOB csgEnv = (CSGEnvironmentIOB)CSGEnvironment.resolveEnvironment();
	    csgEnv.mRemoveUnsplitFace = true;
	    
	    
	  	geom = new CSGGeometry("hi");
	  	
	  	CSGShape aCube = new CSGShape("Box", new CSGBox(1,1,1));
	  	geom.addShape(aCube);
	  	geom.regenerate();
	  	
	    mat = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );
	  	geom.setMaterial( mat );
	  	
	  	shapeSphere	= new CSGShape( "Sphere", new CSGSphere( 5, 5, 0.3f ) );
	  	shapeSphereI = new CSGShape( "Sphere", new CSGSphere( 5, 5, 0.3f ) );
	  	
	   	rootNode.attachChild( geom );
	   		
	  	geomI = new CSGGeometry("hiI");
	  	geomI.setMaterial(mat);
	  	geomI.move(new Vector3f(2,0,0));
	  	rootNode.attachChild(geomI);
	   	
	  	testErrSplits();
	  	
	  	System.out.println("private void testErrSplits(){");
	  }
	
	/**
	 * intersection pos is predicted
	 */
	private void testErrSplits(){
		apos.add(new Vector3f(0.83990216f,0.01416303f,0.41397852f)); //1, [0:1454110976715ms][0:(0.0, 0.0, 0.0);0.0]
		apos.add(new Vector3f(0.14809741f,0.30853847f,0.32987046f)); //2, [1:58ms][1:(0.1648531, 0.2834636, 0.3);0.4444414]
		apos.add(new Vector3f(0.54515183f,0.29772508f,0.26086247f)); //3, [2:11ms][2:(0.2713526, 0.28531697, 0.3);0.49501315]
		apos.add(new Vector3f(0.50555390f,0.28294140f,0.73018849f)); //4, [3:8ms][3:(0.2713526, 0.27231753, 0.3);0.4876362]
		apos.add(new Vector3f(0.67798418f,0.78858733f,0.27823582f)); //5, [4:3ms][4:(0.27135253, 0.28531694, 0.29456878);0.4917405]
		apos.add(new Vector3f(0.00424497f,0.13861489f,0.73975629f)); //6, [5:5ms][5:(0.23324549, 0.24334115, 0.28711078);0.44277644]
		apos.add(new Vector3f(0.37070101f,0.58056056f,0.03278502f)); //7, [6:4ms][6:(0.24722548, 0.28531694, 0.24818724);0.45179987]
		//errDelay:29.85s
		/*
		java.lang.IllegalArgumentException: CSGSolid.splitFaces - too many splits:629502
			at net.wcomohundro.jme3.csg.iob.CSGSolid.splitFaces(CSGSolid.java:206)
			at net.wcomohundro.jme3.csg.iob.CSGShapeIOB.composeSolid(CSGShapeIOB.java:614)
			at net.wcomohundro.jme3.csg.iob.CSGShapeIOB.intersection(CSGShapeIOB.java:311)
			at net.wcomohundro.jme3.csg.iob.CSGShapeIOB.intersection(CSGShapeIOB.java:1)
			at net.wcomohundro.jme3.csg.CSGShape.intersection(CSGShape.java:670)
			at net.wcomohundro.jme3.csg.CSGGeometry.regenerate(CSGGeometry.java:398)
			at net.wcomohundro.jme3.csg.CSGGeometry.regenerate(CSGGeometry.java:345)
		*/
	}
	
  @Override
  public void simpleUpdate(float tpf) {
  	
  	if(shapePrev==null)shapePrev = new CSGShape("Box", new CSGBox(1,1,1));
  	geom.addShape(shapePrev);
  	
  	String fromArray="";
  	if(apos.size()>0){
  		shapeSphere.setLocalTranslation(apos.remove(0));
  		fromArray="A:";
  	}else{
  		shapeSphere.setLocalTranslation(geom.getLocalTranslation());
    	shapeSphere.move(randPos(null).multLocal(0.9f));
  	}
  	
  	System.out.println("apos.add(new Vector3f"+dump(shapeSphere.getLocalTranslation())+"); //"
  			+fromArray+(++iInteractionCount)+", "
  			+"["+(iInteractionCount-1)+":"+(System.currentTimeMillis()-ltimep)+"ms]"
  			+"["+(iInteractionCount-1)+":"+bbPrev.getExtent(null)+";"+bbPrev.getExtent(null).length()+"]"
  	);
  	ltimep = System.currentTimeMillis();
  	
  	geom.addShape( shapeSphere, CSGOperator.DIFFERENCE );
  	
  	try{
    	shapePrev = geom.regenerate();
    	if(bThreaded ){
      	while(!threadedDance()){
      		System.err.println("//Too many threads "+aSluggishThreads.size()+", unable to start a new."+System.currentTimeMillis()+"...");
      		Thread.sleep(3000);
      	}
    	}else{
    		intersectionDance();
    	}
  	}catch(Exception ex){
  		System.err.println("//errDelay:"+(System.currentTimeMillis()-ltimep)/1000f+"s");
  		System.err.println("/*");
  		ex.printStackTrace();
  		System.err.println("*/");
  		exit1();
  	}
  	
  	geom.removeAllShapes();
  }
  
  private class RThread extends Thread{
  	RRun r;
  	public RThread(RRun r,int i){
  		super(r);
  		this.r=r;
    	setName("thread:"+i);
  	}
  	public RRun r(){
  		return r;
  	}
  }
  private class RRun implements Runnable{
  	long lTime=System.currentTimeMillis();
		boolean bSuccess=false;
		int i;
		public RRun(int i){
			this.i=i;
		}
		@Override
		public void run() {
			try{
				intersectionDance();
				bSuccess=true;
	  	}catch(Exception ex){
	  		System.err.println("// EXCEPTION: thread:"+i+", "+getLifeTime()+", "+ex.getMessage());
	  		return; //would still be dancing... 
	  	}
			bDancing=false;
		}
		public String getLifeTime(){
			return ""+(System.currentTimeMillis()-lTime)/1000f+"s";
		}
  }
  
  private boolean threadedDance(){
  	boolean bStartedNewThread=false;
  	// avoid clog cpu
  	if(aSluggishThreads.size() < iMaxSluggishThreads ){
  		bDancing=true;
    	RThread t = new RThread(new RRun(iInteractionCount),iInteractionCount);
    	t.start();
    	bStartedNewThread=true;
    	
    	aSluggishThreads.add(t);
  	
	//  	int iHundredths=10;//100;
	  	int iHundredths=500;
	  	try {
	  		int iSleepCount=0;
	  		while(++iSleepCount<iHundredths){
	    		Thread.sleep(10);
	  			if(!t.isAlive()){
	//      		System.err.println("// THREAD-ENDED "+t.getName());
	  				aSluggishThreads.remove(t); //ended properly and in time
	  				break;
	  			}
	  		}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	  	
	  	if(bDancing){
	  		System.err.println("// THREAD-INTERRUPTING "+t.getName()+" intersection calc totThreads="+aSluggishThreads.size());
	  		t.setPriority(Thread.MIN_PRIORITY);
	  		t.interrupt();
	  	}
  	}
  	
		for(RThread t2 : aSluggishThreads.toArray(new RThread[0])){
			if(!t2.isAlive()){
				if(t2.r().bSuccess){
      		System.err.println("// THREAD actually ended properly: "+t2.getName()+", "+t2.r().getLifeTime());
				}//else was the exception log message
				aSluggishThreads.remove(t2);
			}
		}
		
		return bStartedNewThread;
  }
  
  private void intersectionDance(){
  	geomI.removeAllShapes(); //begin cleaning
  	
  	CSGShape shapePrevToI = new CSGShape("cloneToI",geom.getMesh().deepClone());
  	geomI.addShape(shapePrevToI);
  	
  	Vector3f pos = shapeSphere.getLocalTranslation();
  	shapeSphereI.setLocalTranslation(pos);
  	shapeSphereI.move(randPos(pos.length()).multLocal(0.1f));
  	geomI.addShape(shapeSphereI, CSGOperator.INTERSECTION);
  	
 		CSGShape shapeI = geomI.regenerate(); // <---<<
  	boundsCheck();
  }
  
  private void boundsCheck(){
	//	geomI.updateGeometricState();
		geomI.updateModelBound();
		BoundingBox bb = ((BoundingBox)geomI.getModelBound());
		bbPrev = bb;
		if(bb.getExtent(null).length() > 2f){
			System.err.println("//"+bb.toString());
			exit1();
		}
  }
  
  /**
   * 
   * @param fSeed grants predictable randoms for predefined positions array
   * @return
   */
  private Vector3f randPos(Float fSeed){
  	Random rnd = fSeed==null ? FastMath.rand : new Random(fSeed.longValue());
  	//return new Vector3f(rnd.nextFloat()*2f-1f, rnd.nextFloat()*2f-1f, rnd.nextFloat()*2f-1f);
  	return new Vector3f(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat());
  }
  
	private String dump(Vector3f pos) {
		return String.format("(%01.8ff,%01.8ff,%01.8ff)",pos.x,pos.y,pos.z);
	}
	
	private void exit1(){
		System.err.println("}");
		System.exit(1);
	}
  
}