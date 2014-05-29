package de.ior.coverage;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.util.GeometricShapeFactory;

import de.ior.utils.ProjectProperties;

/**
 * modified from http://docs.geotools.org/stable/userguide/tutorial/feature/csv2shp.html
 */
public class ShapeFileExporter{
	
	private static final Logger _log = LogManager.getLogger(ShapeFileExporter.class.getName());
	Class<? extends Geometry> type;

	public ShapeFileExporter(Class<? extends Geometry> type){
		this.type = type;
	}
	
	public void exportShapes(List<Coordinate> coordinates, File export){

        List<SimpleFeature> features = new ArrayList<SimpleFeature>();
        
        final SimpleFeatureType TYPE = createFeatureType(type);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        buildFeatures(features, featureBuilder, coordinates, type);
        exportFeatures(export, TYPE, features);
       _log.info("exported shapefile to " + export.getAbsolutePath());
    }

	private static void exportFeatures(File newFile, final SimpleFeatureType TYPE,
			List<SimpleFeature> features) {

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        try {
			params.put("url", newFile.toURI().toURL());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore;
		try {
			newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
			newDataStore.createSchema(TYPE);   
			writeToShapeFile(TYPE, features, newDataStore);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeToShapeFile(final SimpleFeatureType TYPE,
			List<SimpleFeature> features, ShapefileDataStore newDataStore)
			throws IOException {
		
        Transaction transaction = new DefaultTransaction("create");

        String typeName = newDataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            
            SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
        } else {
            _log.error(typeName + " does not support read/write access");
        }
	}
	
	private static void buildFeatures(List<SimpleFeature> features,
			SimpleFeatureBuilder featureBuilder, List<Coordinate> coordinates, Class<? extends Geometry> type){
		
		for (Coordinate c : coordinates) {
			if (type == Point.class) {
				Point point = createPoint(c);
				featureBuilder.add(point);
			} else if (type == Polygon.class) {
//				Polygon circle = createCircle(c,
//						java.lang.Double.parseDouble(ProjectProperties
//								.getProperties().getProperty("circle-radius")));
				Polygon circle = createCircle(c,ProjectProperties.getCircleRadius());
				featureBuilder.add(circle);
			}
			SimpleFeature feature = featureBuilder.buildFeature(null);
	        features.add(feature);
		}
	}
 
    private static Polygon createCircle(Coordinate coord, final double radius) {
		GeometricShapeFactory shapeFactory = new GeometricShapeFactory();
		shapeFactory.setNumPoints(32);
		shapeFactory.setCentre(coord);
		shapeFactory.setSize(radius * 2);
		return shapeFactory.createCircle();
	}
    private static Point createPoint(Coordinate coord) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point point = geometryFactory.createPoint(coord);
		return point;
	}
    
    private static SimpleFeatureType createFeatureType(Class<? extends Geometry> type) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Location");
        CoordinateReferenceSystem crs = null;
        try {
			crs = CRS.decode("EPSG:32632");
		} catch (NoSuchAuthorityCodeException e) {
			e.printStackTrace();
		} catch (FactoryException e) {
			e.printStackTrace();
		}
        builder.setCRS(crs);
        builder.add("the_geom", type);
        final SimpleFeatureType LOCATION = builder.buildFeatureType();

        return LOCATION;
    }
}