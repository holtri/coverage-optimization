package de.ior.coverage;

import gnu.trove.procedure.TIntProcedure;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Ellipse2D.Double;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import net.sf.jsi.Rectangle;
import net.sf.jsi.SpatialIndex;
import net.sf.jsi.rtree.RTree;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.opengis.feature.simple.SimpleFeature;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.vividsolutions.jts.geom.MultiPolygon;

import de.ior.coverage.SolutionSet.Solution;
import de.ior.utils.ProjectProperties;



public class CoverageOptimization {

	static ArrayList<PolygonWrapper> polygons = new ArrayList<PolygonWrapper>();
    private static final Logger _log = LogManager.getLogger(CoverageOptimization.class.getName());
	private static File tmp;

	static class DetectedPolygons implements TIntProcedure{

		private List<Integer> ids = new ArrayList<Integer>();
		public boolean execute(int id) {
			getIds().add(id);
			return true;
		}
		public List<Integer> getIds() {
			return ids;
		}		
	}
	
	public static void main(String[] args) throws Exception {
		long millis = System.currentTimeMillis();
		
		SimpleFeatureSource featureSource = loadShapefile();

		extractPolygons(featureSource);
		
		SpatialIndex si = setupRTree();
		HashSet<Point2D> PIPS = calculatePIPS(si);
		_log.info("found " + PIPS.size() + " polygon intersection points.");
		
		SolutionSet solutionSet = reducePIPS(PIPS, si);
		_log.info("reduced polygon intersection points to " + solutionSet.getSolutionPoints().size());
		outputSolutionSet(solutionSet);
		
//		outputWeights();
		
//		exportPIPS(PIPS,ProjectProperties.getProperties().getProperty("export-folder"),ProjectProperties.getProperties().getProperty("export-filename"));
		
		createXPressDataFile(solutionSet,ProjectProperties.getProperties().getProperty("export-folder"),ProjectProperties.getProperties().getProperty("export-filename"));
		
		HashSet<Integer> optimalFacilityLocations = new MCLPXpressWrapper().solveMCLP("xpress\\tmpData.dat");
		
		_log.info(optimalFacilityLocations);
		_log.info("total time: " + (System.currentTimeMillis() - millis));
		_log.info("done");
		
		//tmp.delete();
		// displayMap(featureSource);
	}

	private static void createXPressDataFile(SolutionSet solutionSet,
			String folder, String filename) throws IOException {
		tmp = new File("xpress\\tmpData.dat");
		File file = new File(folder + filename + System.currentTimeMillis() + ".dat");
		_log.info("exporting to " + file.getAbsolutePath());
		file.createNewFile();
		PrintWriter pw = new PrintWriter(file);
		
		pw.println("numberOfPolygons: " + polygons.size());
		pw.println("numberOfFacilityLocations: " + solutionSet.getSolutions().size());
		pw.print("polygonWeight: [");
		for(int i=0; i<polygons.size();i++){
			pw.print(polygons.get(i).getWeight() + " ");
		}
		pw.println("]");
		pw.print("n: [");
		HashMap<Integer, HashSet<Integer>> polygonToSolutionMapping = solutionSet.getPolygonToSolutionMapping();
		for(Integer i : polygonToSolutionMapping.keySet()){
			pw.print("(" + (i+1) + ")" + "[");
			for(Integer j : polygonToSolutionMapping.get(i)){
				pw.print(j + " ");
			}
			pw.print("]");
		}
		pw.print("]");
		pw.flush();
		pw.close();
		if(tmp.exists()){tmp.delete();}
		Files.copy(file.toPath(), tmp.toPath());
	}

	private static void outputWeights() {
		HashMap<Integer, java.lang.Double> polygonWeights = new HashMap<Integer, java.lang.Double>();
		for(int i=0; i < polygons.size(); i++){
			polygonWeights.put(i, polygons.get(i).getWeight());
		}
		_log.info("weights:");
		_log.info(polygonWeights);
	}

	private static void outputSolutionSet(SolutionSet solutionSet) {
		if (_log.isDebugEnabled()) {
			List<Solution> solutions = solutionSet.getSolutions();
			for (Solution s : solutions) {
				_log.debug("Solution " + s.getId() + " covers polygons: "
						+ s.getCoveredPolygonIds());
			}
			_log.info("polygon to solution mapping: " );
			for(Integer solutionId : solutionSet.getPolygonToSolutionMapping().keySet()){
				_log.debug(solutionId + " --> " + solutionSet.getPolygonToSolutionMapping().get(solutionId));
			}
		}
	}

	private static SolutionSet reducePIPS(HashSet<Point2D> PIPS, SpatialIndex si) {
		
		_log.info("reducing PIPS...");
		SolutionSet solutionSet = new SolutionSet();
		double circleRadius = java.lang.Double.parseDouble(ProjectProperties.getProperties().getProperty("circle-radius"));
		int i=0;
		for(Point2D p : PIPS){
			i++;
//			_log.info("checking PIP " + i + " of " + PIPS.size());
			Ellipse2D.Double serviceRadius = new Ellipse2D.Double(p.getX() - circleRadius, p.getY() - circleRadius, circleRadius * 2 , circleRadius * 2);
			java.awt.Rectangle bounds = serviceRadius.getBounds();
			Rectangle searchRectangle = new Rectangle((float)bounds.getMinX(), (float)bounds.getMinY(), (float)bounds.getMaxX(), (float)bounds.getMaxY());
			
			DetectedPolygons contained = new DetectedPolygons();
			si.intersects(searchRectangle, contained);
			List<Integer> ids = contained.getIds();
			
			HashSet<Integer> coveredPolygonIds = calculateCoveredPolygons(serviceRadius, ids);
			
			solutionSet.addSolution(p, coveredPolygonIds);
		}
		
		return solutionSet;
	}

	private static HashSet<Integer> calculateCoveredPolygons(
			Ellipse2D.Double serviceRadius, List<Integer> ids
			) {
		HashSet<Integer> coveredPolygonIds = new HashSet<Integer>();;
		for(Integer id : ids){
			PolygonWrapper polygon = polygons.get(id);
			boolean coverage = true;
			double eps = 0.02;
			for(Point2D vertice : polygon.getPolygonVertices()){
				if(!serviceRadius.intersects(vertice.getX()- eps/2, vertice.getY()-eps/2, eps, eps)){//(vertice)){
					coverage = false;
					break;
				}
			}
			if(coverage){
				coveredPolygonIds.add(id);
			}
		}
		return coveredPolygonIds;
	}

	private static void exportPIPS(HashSet<Point2D> PIPS, String folder, String filename) throws IOException {

		File file = new File(folder + filename + System.currentTimeMillis() + ".csv");
		_log.info("exporting to " + file.getAbsolutePath());
		file.createNewFile();
		CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(file), CSVFormat.EXCEL.withHeader("x-cor", "y-cor"));
		csvPrinter.printRecord("x-cor","y-cor");
		DecimalFormat df = new DecimalFormat(".###");
		for(Point2D p : PIPS){
			csvPrinter.printRecord(df.format(p.getX()),df.format(p.getY()));
		}
		csvPrinter.close();
	}

	private static HashSet<Point2D> calculatePIPS(SpatialIndex si) {
		
		HashSet<Point2D> PIPS = new HashSet<Point2D>();
		
		for (int i = 0; i < polygons.size(); i++) {
//			_log.info("calculating PIPS for polygon " + i + " of " + polygons.size());
			PolygonWrapper polygonToCheck = polygons.get(i);

			DetectedPolygons intersected = new DetectedPolygons();
			si.intersects(polygonToCheck.getSpatialStorageRectangle(), intersected );
			List<Integer> ids = intersected.getIds();
			
			intersectPolygon(PIPS, i, polygonToCheck, ids); 
//			_log.info("found " + ids.size() + " intersection points");
		}
		
		
		return PIPS;
	}

	

	private static void intersectPolygon(HashSet<Point2D> PIPS, int i,
			PolygonWrapper polygonToCheck, List<Integer> ids) {
		if(ids.size()==1) {
			PIPS.addAll(polygonToCheck.getCoveringCircleIntersectionPoints());
		}
		else{
			for(Integer id : ids){
				if(id!=i){
					List<Point2D> intersectionPoints = polygonToCheck.getIntersectionPoints(polygons.get(id));
					PIPS.addAll(intersectionPoints);
				}
			}
		}
	}

	private static SpatialIndex setupRTree() {
		SpatialIndex si = new RTree();
		si.init(null);

		for (int i = 0; i < polygons.size(); i++) {
			si.add(polygons.get(i).getSpatialStorageRectangle(), i);
		}
		return si;
	}

	private static void extractPolygons(SimpleFeatureSource featureSource)
			throws Exception {
		SimpleFeatureIterator features = featureSource.getFeatures().features();
		while (features.hasNext()) {
			SimpleFeature next = features.next();
			MultiPolygon mp = (MultiPolygon) next.getDefaultGeometry();
			polygons.add(new PolygonWrapper(mp));
		}

	}

	private static SimpleFeatureSource loadShapefile() throws IOException {
		 File file = new File(ProjectProperties.getProperties().getProperty("filename"));
//		File file = new File("testdata" + File.separator + "testdata.shp");
		FileDataStore store = FileDataStoreFinder.getDataStore(file);
		SimpleFeatureSource featureSource = store.getFeatureSource();
		return featureSource;
	}

	private static void displayMap(SimpleFeatureSource featureSource)
			throws SchemaException {
		MapContent map = new MapContent();
		map.setTitle("TestData");

		Style style = SLD.createSimpleStyle(featureSource.getSchema());
		Layer layer = new FeatureLayer(featureSource, style);

		map.addLayer(layer);
		JMapFrame.showMap(map);
	}

}
