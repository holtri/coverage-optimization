package de.ior.coverage;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import net.sf.jsi.Rectangle;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiPolygon;

public class PolygonWrapper {

	public static double circleRadius = 20;
	private MultiPolygon mp;
	private Area coveringCirclesIntersection;

	public Area getCoveringCirclesIntersection() {
		return coveringCirclesIntersection;
	}


	public PolygonWrapper(MultiPolygon mp) throws Exception {
		this.mp = mp;
		calculateCoveringCirclesIntersection();

		java.awt.Rectangle bounds = coveringCirclesIntersection.getBounds();
		
		double sqrt = Math.sqrt(Math.pow(bounds.width,2) * Math.pow(bounds.height,2));
		if(sqrt<circleRadius){
			throw new Exception("circle Radius does not cover polygon");
		}
	}

	public Rectangle getSpatialStorageRectangle(){
		java.awt.Rectangle bounds = coveringCirclesIntersection.getBounds();
		
		Rectangle rTreeRectangle = new Rectangle((float)bounds.getMinX(), (float)bounds.getMinY(), (float)bounds.getMaxX(), (float)bounds.getMaxY());
		
		return rTreeRectangle;
	}

	private void calculateCoveringCirclesIntersection() {
		Coordinate[] coordinates = mp.getCoordinates();
		Area intersection = new Area(new Ellipse2D.Double(coordinates[1].x - circleRadius, coordinates[1].y - circleRadius, circleRadius * 2, circleRadius * 2));
		for (int i = 2; i < coordinates.length; i++) {
			intersection.intersect(new Area(new Ellipse2D.Double(coordinates[i].x - circleRadius, coordinates[i].y - circleRadius, circleRadius * 2, circleRadius * 2)));
		}
		this.coveringCirclesIntersection = intersection;
	}


	public List<Point2D> getIntersectionPoints(Area area) {
		
		Area intersectionBetweenBothAreas = (Area) coveringCirclesIntersection.clone();
		intersectionBetweenBothAreas.intersect(area);
		
		List<Point2D> intersectionPointCandidates = getVertices(intersectionBetweenBothAreas);
		getIntersectionPoints(area, intersectionPointCandidates);
		
		return intersectionPointCandidates;
	}


	private void getIntersectionPoints(Area area,
			List<Point2D> intersectionPointCandidates) {
		List<Point2D> result = new ArrayList<Point2D>();
		
		for(Point2D p : intersectionPointCandidates){
			Point2D p1 = new Point2D.Double(p.getX()-0.001,p.getY());
			Point2D p2 = new Point2D.Double(p.getX()+ 0.001,p.getY());
			boolean isInArea1 = area.contains(p1) && area.contains(p2);
			boolean isInArea2 = coveringCirclesIntersection.contains(p1) && coveringCirclesIntersection.contains(p2);
			if(!isInArea1&&!isInArea2){
				result.add(p);
			}
		}
	}

	private List<Point2D> getVertices(Area area) {
		PathIterator pathIterator = area.getPathIterator(null);

		List<Point2D> intersectionPoints = new ArrayList<Point2D>();; 

		while (!pathIterator.isDone()) {
			double[] coords = new double[6];
			if (pathIterator.currentSegment(coords) == PathIterator.SEG_LINETO) {
				intersectionPoints.add(new Point2D.Double(coords[0], coords[1]));
			}
			pathIterator.next();
		}
		return intersectionPoints;
	}

	@Override
	public String toString() {
		StringBuffer polygonDescription = new StringBuffer();
		polygonDescription.append("polygon (x,y) vertices: ");
		Coordinate[] coordinateArray = mp.getCoordinates();
		for (int i=1;i<coordinateArray.length;i++) {
			polygonDescription.append(String.format("(%.2f,%.2f) ", coordinateArray[i].x, coordinateArray[i].y));
		}
		return polygonDescription.toString();
	}

	public String coveringCircleIntersectionPoints() {
		StringBuffer result = new StringBuffer();
		result.append("covering circle intersection (x,y) vertices: ");
		
		List<Point2D> intersectionPoints = getVertices(coveringCirclesIntersection);
		for(Point2D p : intersectionPoints){
			result.append(String.format("(%.2f,%.2f) ", p.getX(),p.getY()));
		}
		return result.toString();
	}

}
