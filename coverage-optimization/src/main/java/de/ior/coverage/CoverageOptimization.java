package de.ior.coverage;

import gnu.trove.procedure.TIntProcedure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import net.sf.jsi.SpatialIndex;
import net.sf.jsi.rtree.RTree;

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

import com.vividsolutions.jts.geom.MultiPolygon;

public class CoverageOptimization {

	static ArrayList<PolygonWrapper> polygons = new ArrayList<PolygonWrapper>();
	private static Properties properties;
	
	public static void main(String[] args) throws IOException, SchemaException {
		
		loadProperties();
		SimpleFeatureSource featureSource = loadShapefile();

		extractPolygons(featureSource);
		
		SpatialIndex si = new RTree();
	    si.init(null);
	    
	    for(int i=0;i<polygons.size();i++){
	    	si.add(polygons.get(i).getSpatialStorageRectangle(), i);
	    }
		si.intersects(polygons.get(1).getSpatialStorageRectangle(), new TIntProcedure() {
			public boolean execute(int id) {
				System.out.println("found id " + id);
				return true;
			}
		}); 
		System.out.println(polygons.get(0));
		System.out.println(polygons.get(1));
		System.out.println(polygons.get(2));

//        displayMap(featureSource);
	}

	private static void loadProperties() throws IOException,
			FileNotFoundException {
		properties = new Properties();
		properties.load(new FileInputStream("project.properties"));
	}

	private static void extractPolygons(SimpleFeatureSource featureSource)
			throws IOException {
		SimpleFeatureIterator features = featureSource.getFeatures().features();
		while(features.hasNext()){
        	SimpleFeature next = features.next();
        	MultiPolygon mp = (MultiPolygon) next.getDefaultGeometry();
        	polygons.add(new PolygonWrapper(mp));
		}

	}

	private static SimpleFeatureSource loadShapefile() throws IOException {
//		File file = new File(properties.getProperty("filename"));
		File file = new File("testdata" + File.separator + "testdata.shp");
		FileDataStore store = FileDataStoreFinder.getDataStore(file);
        SimpleFeatureSource featureSource = store.getFeatureSource();
		return featureSource;
	}

	private static void displayMap(SimpleFeatureSource featureSource) throws SchemaException {
		// Create a map content and add our shapefile to it
        MapContent map = new MapContent();
        map.setTitle("TestData");
        
        Style style = SLD.createSimpleStyle(featureSource.getSchema());
        Layer layer = new FeatureLayer(featureSource, style);
               
        map.addLayer(layer);
        JMapFrame.showMap(map);
	}

}
