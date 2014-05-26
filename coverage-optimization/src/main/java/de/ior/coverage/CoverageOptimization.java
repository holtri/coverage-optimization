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
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
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
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;










import com.vividsolutions.jts.geom.MultiPolygon;

import de.ior.coverage.SolutionSet.Solution;
import de.ior.utils.ProjectProperties;

public class CoverageOptimization {

	static ArrayList<PolygonWrapper> polygons = new ArrayList<PolygonWrapper>();
    private static final Logger _log = LogManager.getLogger(CoverageOptimization.class.getName());

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
		SimpleFeatureSource featureSource = loadShapefile();

		extractPolygons(featureSource);

		SpatialIndex si = setupRTree();
		
		HashSet<Point2D> PIPS = calculatePIPS(si);
		_log.info("found " + PIPS.size() + " polygon intersection points.");
		
		SolutionSet solutionSet = reducePIPS(PIPS, si);
		
		outputSolutionSet(solutionSet);
		
		exportPIPS(PIPS,ProjectProperties.getProperties().getProperty("export-folder"),ProjectProperties.getProperties().getProperty("export-filename"));
		
		_log.info("done");
		// displayMap(featureSource);
	}

	private static void outputSolutionSet(SolutionSet solutionSet) {
		List<Solution> solutions = solutionSet.getSolutions();
		for(Solution s : solutions){
			System.out.println("Solution " + s.getId() + " covers polygons: " + s.getCoveredPolygonIds());
		}
	}

	private static SolutionSet reducePIPS(HashSet<Point2D> PIPS, SpatialIndex si) {
		SolutionSet solutionSet = new SolutionSet();
		int i=0;
		double circleRadius = java.lang.Double.parseDouble(ProjectProperties.getProperties().getProperty("circle-radius"));
		for(Point2D p : PIPS){
			i++;
//			_log.info("reducing solution point " + i + " of " + PIPS.size());
			Ellipse2D.Double serviceRadius = new Ellipse2D.Double(p.getX() - circleRadius, p.getY() - circleRadius, circleRadius * 2 , circleRadius * 2);
//			_log.info("p_x: " + p.getX() + " y:" + p.getY() + " serviceRadius: x:" + serviceRadius.getCenterX() + " y:" + serviceRadius.getCenterY());
			java.awt.Rectangle bounds = serviceRadius.getBounds();
			Rectangle searchRectangle = new Rectangle((float)bounds.getMinX(), (float)bounds.getMinY(), (float)bounds.getMaxX(), (float)bounds.getMaxY());
			
			DetectedPolygons contained = new DetectedPolygons();
			si.intersects(searchRectangle, contained);
			List<Integer> ids = contained.getIds();
//			_log.info("this solution point intersects polygon with ids: " + ids);
			HashSet<Integer> coveredPolygonIds = new HashSet<Integer>();
			for(Integer id : ids){
				PolygonWrapper polygon = polygons.get(id);
				boolean coverage = true;
				double eps = 0.02;
				for(Point2D vertice : polygon.getPolygonVertices()){
					if(!serviceRadius.intersects(vertice.getX()- eps/2, vertice.getY()-eps/2, eps, eps)){//(vertice)){
//					if(!serviceRadius.contains(vertice)){
//					_log.info("distance x: " + (serviceRadius.getCenterX() - vertice.getX()) + " distance y: " + (serviceRadius.getCenterY() - vertice.getY()));
						coverage = false;
//						_log.info("polygon not covered");
						break;
					}
				}
				if(coverage){
					coveredPolygonIds.add(id);
				}
			}
			solutionSet.addSolution(p, coveredPolygonIds);
		}
		
		//TODO round to cm
		//TODO put point to temp hashset to avoid duplicates
		//TODO map critical point to polygons that are covered: e.g. s_1 -> {p1, p2, p3} 
		
		return solutionSet;
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
			_log.info("calculating PIPS for polygon " + i + " of " + polygons.size());
			PolygonWrapper polygonToCheck = polygons.get(i);

			DetectedPolygons intersected = new DetectedPolygons();
			si.intersects(polygonToCheck.getSpatialStorageRectangle(), intersected );
			List<Integer> ids = intersected.getIds();
			
			intersectPolygon(PIPS, i, polygonToCheck, ids); 
			_log.info("found " + ids.size() + " intersection points");
		}
		
		
		return PIPS;
	}

	

	private static void intersectPolygon(HashSet<Point2D> PIPS, int i,
			PolygonWrapper polygonToCheck, List<Integer> ids) {
		if(ids.size()==1) {
			PIPS.addAll(polygonToCheck.getCoveringCircleIntersectionPoints());
//				System.out.println("no intersecting polygon for item " + i);
		}
		else{
			for(Integer id : ids){
				if(id!=i){
//						System.out.println("add ips of " + i + " and " + id + " to PIPS");
					List<Point2D> intersectionPoints = polygonToCheck.getIntersectionPoints(polygons.get(id));
					PIPS.addAll(intersectionPoints);
//					_log.info("intersection point between circle " + i + " and circle " + id + " at: ");
//					for(Point2D p : intersectionPoints){
//						_log.info("x: " + p.getX() + " y: " + p.getY());
//						
//					}
					
				}
			}
		}
	}

	private static SpatialIndex setupRTree() {
		SpatialIndex si = new RTree();
		si.init(null);

		// store in R tree
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
