package de.ior.coverage;

import gnu.trove.procedure.TIntProcedure;

import java.awt.geom.Point2D;
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

import de.ior.utils.ProjectProperties;

public class CoverageOptimization {

	static ArrayList<PolygonWrapper> polygons = new ArrayList<PolygonWrapper>();
    private static final Logger _log = LogManager.getLogger(CoverageOptimization.class.getName());

	static class IntersectedPolygons implements TIntProcedure{

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
		exportPIPS(PIPS,ProjectProperties.getProperties().getProperty("export-folder"),ProjectProperties.getProperties().getProperty("export-filename"));
		_log.info("done");
		// displayMap(featureSource);
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

			IntersectedPolygons intersected = new IntersectedPolygons();
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
					PIPS.addAll(polygonToCheck.getIntersectionPoints(polygons.get(id)));
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
