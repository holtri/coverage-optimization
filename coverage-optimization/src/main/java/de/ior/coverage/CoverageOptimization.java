package de.ior.coverage;

import gnu.trove.procedure.TIntProcedure;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.sf.jsi.Rectangle;
import net.sf.jsi.SpatialIndex;
import net.sf.jsi.rtree.RTree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import de.ior.coverage.SolutionSet.Solution;
import de.ior.utils.ProjectProperties;

public class CoverageOptimization {

	static ArrayList<PolygonWrapper> polygons = new ArrayList<PolygonWrapper>();
	private static final Logger _log = LogManager
			.getLogger(CoverageOptimization.class.getName());
	private static File tmp;

	static class DetectedPolygons implements TIntProcedure {

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

		runCoverageOptimization();

	}

	private static void runCoverageOptimization() throws IOException, Exception {


		for (int circleRadius = 30; circleRadius <= 70; circleRadius = circleRadius + 10) {
			for (int numberOfFacilities = 5; numberOfFacilities < 40; numberOfFacilities++) {
				
				ProjectProperties.setNumberOfFacilities(numberOfFacilities);
				ProjectProperties.setCircleRadius(circleRadius);
				_log.info("####### run with circleRadius: " + circleRadius + " numberOfFacilities: " + numberOfFacilities);
				
				SpatialIndex si = setup();
				
				long time = System.currentTimeMillis();

				HashSet<Point2D> PIPS = calculatePIPS(si);
				_log.info("found " + PIPS.size()
						+ " polygon intersection points.");

				SolutionSet solutionSet = reducePIPS(PIPS, si);
				_log.info("reduced polygon intersection points to "
						+ solutionSet.getSolutions().keySet().size());

				createXPressDataFile(
						solutionSet,
						ProjectProperties.getProperties().getProperty(
								"export-folder"),
						ProjectProperties.getProperties().getProperty(
								"export-filename"));

				HashSet<Integer> optimalFacilityLocations = new MCLPXpressWrapper()
						.solveMCLP("xpress\\tmpData.dat");
				time = System.currentTimeMillis() - time;

				double coverage = calculateCoverage(optimalFacilityLocations, solutionSet);
				_log.info("achieved coverage: " + coverage);
				exportShapeFiles(solutionSet, optimalFacilityLocations, time,
						coverage, PIPS.size(), solutionSet.getSolutions().keySet().size());
			}
		}
		// _log.info("total time: " + (time));
		// _log.info("done");
	}

	private static double calculateCoverage(HashSet<Integer> optimalFacilityLocations, SolutionSet solutions) {
		double totalArea = 0;
		
		for (PolygonWrapper pw : polygons) {
			totalArea += pw.getWeight();
		}
		_log.debug("totalArea" + totalArea);
		double coverage = 0;
		HashSet<Integer> tmp = new HashSet<Integer>();
		for (Integer key : optimalFacilityLocations) {
			Solution solution = solutions.getSolutions().get(key);
			tmp.addAll(solution.getCoveredPolygonIds());
		}
		for(Integer key : tmp){
			coverage += polygons.get(key).getWeight();
		}
		_log.debug("coverage: " + coverage);
		return coverage/totalArea;
	}

	private static SpatialIndex setup() throws IOException, Exception {
		SimpleFeatureSource featureSource = loadShapefile();

		extractPolygons(featureSource);

		SpatialIndex si = setupRTree();
		return si;
	}

	private static void exportShapeFiles(SolutionSet solutionSet,
			HashSet<Integer> optimalFacilityLocations, long time,
			double coverage, int PIPSSize, int solutionSetSize) throws IOException {

		List<Coordinate> coordinates = new ArrayList<Coordinate>();
		_log.debug("solution point indices: " + optimalFacilityLocations);
		for (Integer i : optimalFacilityLocations) {
			// TODO DOUBLE CHECK INDEX WITH XPRESS (xpress might start with 1,
			// java with 0!!!)
			if (i != 0) {
				Point2D solutionPoint = solutionSet.getSolutions().get(i)
						.getSolutionPoint();
				coordinates.add(new Coordinate(solutionPoint.getX(),
						solutionPoint.getY()));
			} else {
				_log.error("index 0!");
			}
		}
		
		StringBuffer sb = new StringBuffer();
		
		sb.append(ProjectProperties.getProperties()
						.getProperty("export-folder"));
		sb.append("\\" + System.currentTimeMillis() + "campus_service_");
		sb.append("r_" + (int)ProjectProperties.getCircleRadius());
		sb.append("p_" + ProjectProperties.getNumberOfFacilities());
		
		
		new File(sb.toString()).mkdir();
		File export =  new File(sb.toString() + "\\points.shp");
		new ShapeFileExporter(Point.class).exportShapes(coordinates, export);

		export = new File(sb.toString() + "\\polygons.shp");
		new ShapeFileExporter(Polygon.class).exportShapes(coordinates, export);

		export = new File(sb.toString() + "\\description.txt");
		export.createNewFile();
		
		exportDescription(time, coverage, PIPSSize, solutionSetSize, export);
	}

	private static void exportDescription(long time, double coverage,
			int PIPSSize, int solutionSetSize, File export)
			throws FileNotFoundException {
		PrintWriter pw = new PrintWriter(export);
		pw.println("radius: " + ProjectProperties.getCircleRadius());
		pw.println("number of facilities: "
				+ ProjectProperties.getNumberOfFacilities());
		pw.println("time: " + time);
		pw.println("coverage: " + coverage);
		pw.println("PIPS size: " + PIPSSize);
		pw.println("RPIPS: " + solutionSetSize);
		pw.close();
	}

	private static void createXPressDataFile(SolutionSet solutionSet,
			String folder, String filename) throws IOException {
		tmp = new File("xpress\\tmpData.dat");
		File file = new File(folder + filename + System.currentTimeMillis()
				+ ".dat");
		_log.info("exporting to " + file.getAbsolutePath());
		file.createNewFile();
		PrintWriter pw = new PrintWriter(file);

		pw.println("numberOfPolygons: " + polygons.size());
		pw.println("numberOfFacilityLocations: "
				+ solutionSet.getSolutions().size());
		pw.print("polygonWeight: [");
		for (int i = 0; i < polygons.size(); i++) {
			pw.print(polygons.get(i).getWeight() + " ");
		}
		pw.println("]");
		pw.print("n: [");
		HashMap<Integer, HashSet<Integer>> polygonToSolutionMapping = solutionSet
				.getPolygonToSolutionMapping();
		for (Integer i : polygonToSolutionMapping.keySet()) {
			pw.print("(" + (i + 1) + ")" + "[");
			for (Integer j : polygonToSolutionMapping.get(i)) {
				pw.print(j + " ");
			}
			pw.print("]");
		}
		pw.print("]");
		pw.flush();
		pw.close();
		if (tmp.exists()) {
			tmp.delete();
		}
		Files.copy(file.toPath(), tmp.toPath());
	}

	private static SolutionSet reducePIPS(HashSet<Point2D> PIPS, SpatialIndex si) {

		_log.info("reducing PIPS...");
		SolutionSet solutionSet = new SolutionSet();
		// double circleRadius =
		// java.lang.Double.parseDouble(ProjectProperties.getProperties().getProperty("circle-radius"));
		int i = 0;
		for (Point2D p : PIPS) {
			i++;
			if (i % 1000 == 0) {
				_log.debug("checking " + i + " of " + PIPS.size());
			}
			Ellipse2D.Double serviceRadius = new Ellipse2D.Double(p.getX()
					- ProjectProperties.getCircleRadius(), p.getY()
					- ProjectProperties.getCircleRadius(),
					ProjectProperties.getCircleRadius() * 2,
					ProjectProperties.getCircleRadius() * 2);
			java.awt.Rectangle bounds = serviceRadius.getBounds();
			Rectangle searchRectangle = new Rectangle((float) bounds.getMinX(),
					(float) bounds.getMinY(), (float) bounds.getMaxX(),
					(float) bounds.getMaxY());

			DetectedPolygons contained = new DetectedPolygons();
			si.intersects(searchRectangle, contained);
			List<Integer> ids = contained.getIds();

			HashSet<Integer> coveredPolygonIds = calculateCoveredPolygons(
					serviceRadius, ids);
			// _log.info("covered polygon ids: " + coveredPolygonIds);
			solutionSet.addSolution(p, coveredPolygonIds);
		}

		return solutionSet;
	}

	private static HashSet<Integer> calculateCoveredPolygons(
			Ellipse2D.Double serviceRadius, List<Integer> ids) {
		HashSet<Integer> coveredPolygonIds = new HashSet<Integer>();
		;
		for (Integer id : ids) {
			PolygonWrapper polygon = polygons.get(id);
			boolean coverage = true;
			double eps = 0.02;
			for (Point2D vertice : polygon.getPolygonVertices()) {
				if (!serviceRadius.intersects(vertice.getX() - eps / 2,
						vertice.getY() - eps / 2, eps, eps)) {// (vertice)){
					coverage = false;
					break;
				}
			}
			if (coverage) {
				coveredPolygonIds.add(id);
			}
		}
		return coveredPolygonIds;
	}

	private static HashSet<Point2D> calculatePIPS(SpatialIndex si) {

		HashSet<Point2D> PIPS = new HashSet<Point2D>();

		for (int i = 0; i < polygons.size(); i++) {
			_log.debug("calculating PIPS for polygon " + i + " of "
					+ polygons.size());
			PolygonWrapper polygonToCheck = polygons.get(i);

			DetectedPolygons intersected = new DetectedPolygons();
			si.intersects(polygonToCheck.getSpatialStorageRectangle(),
					intersected);
			List<Integer> ids = intersected.getIds();

			intersectPolygon(PIPS, i, polygonToCheck, ids);
		}
		return PIPS;
	}

	private static void intersectPolygon(HashSet<Point2D> PIPS, int i,
			PolygonWrapper polygonToCheck, List<Integer> ids) {
		if (ids.size() == 1) {
			PIPS.addAll(polygonToCheck.getCoveringCircleIntersectionPoints());
		} else {
			for (Integer id : ids) {
				if (id != i) {
					List<Point2D> intersectionPoints = polygonToCheck
							.getIntersectionPoints(polygons.get(id));
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
		File file = new File(ProjectProperties.getProperties().getProperty(
				"filename"));
		// File file = new File("testdata" + File.separator + "testdata.shp");
		FileDataStore store = FileDataStoreFinder.getDataStore(file);
		SimpleFeatureSource featureSource = store.getFeatureSource();
		store.dispose();
		return featureSource;
	}

}
