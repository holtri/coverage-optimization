package de.ior.coverage;


import java.io.IOException;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dashoptimization.XPRM;
import com.dashoptimization.XPRMCompileException;
import com.dashoptimization.XPRMLicenseError;
import com.dashoptimization.XPRMModel;

import de.ior.utils.ProjectProperties;

public class MCLPXpressWrapper {

	private static final Logger _log = LogManager.getLogger(MCLPXpressWrapper.class.getName());
		
	public static class MySol{
    	public int value;
    }
	
	public static class MyData{
		public int p;
    	public String initFile;

	}
	public static void main (String[] args){
		new MCLPXpressWrapper().solveMCLP("xpress\\tmpData.dat");
	}
	
	public HashSet<Integer> solveMCLP(String dataFile) {
		MySol[] solution = null;
		HashSet<Integer> facilitiesIndex = new HashSet<Integer>();
		try {
			solution = runXpressModel(dataFile);
		} catch (XPRMLicenseError e) {
			_log.error("License error : " + e.getMessage());
		} catch (java.lang.Exception e) {
			_log.error("Model loading or execution error : " + e.getMessage());
		}
		for (MySol s : solution) {
			facilitiesIndex.add(s.value);
		}
		return facilitiesIndex;
	}
	
	static MySol[] runXpressModel(String dataFile) throws XPRMLicenseError, IOException, XPRMCompileException
    {
	 String modelFile = "xpress\\MCLP_v002.mos";
     XPRMModel model;
     XPRM xprm;

     xprm = new XPRM();

     xprm.compile(modelFile);
     model=xprm.loadModel("xpress\\MCLP_v002.bim");
     
     MyData myData = new MyData();
     myData.p = Integer.parseInt(ProjectProperties.getProperties().getProperty("number-of-facilities"));
     myData.initFile = dataFile;
     
     MySol[] solution = new MySol[myData.p];
     for(int i=0;i<solution.length;i++) {
    	 solution[i] = new MySol();
     }
     
     model.bind("sol", solution);
     model.bind("data", myData);
     model.execParams = "SOL='sol(value)', P='data(p)', INIT='data(initFile)'";
     _log.info("run xpress model using model file " + modelFile + "...");
     model.run();
     
	if (model.getExecStatus() != XPRMModel.RT_OK) {
			throw new java.lang.RuntimeException("Error during execution");
		} else {
			_log.debug("Model execution returned: " + model.getResult());
		}
	return solution;
	}
}
