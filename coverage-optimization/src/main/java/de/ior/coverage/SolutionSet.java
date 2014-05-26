package de.ior.coverage;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolutionSet {
    private static final Logger _log = LogManager.getLogger(SolutionSet.class.getName());

	int id = 1;
	
	private List<Solution> solutions;
	
	public SolutionSet(){
		this.solutions = new ArrayList<Solution>();
	}
	
	public void addSolution(Point2D solution, HashSet<Integer> coveredPolygonIds){
		boolean isDominated = false;
		
		for(Solution s : getSolutions()){
			if(s.coveredPolygonIds.containsAll(coveredPolygonIds)){
				isDominated = true;
				if(_log.isDebugEnabled()){
					_log.debug("##found dominated solution");
					_log.debug("#### dominating covers: " + s.getCoveredPolygonIds());
					_log.debug("#### dominated  covers: " + coveredPolygonIds);
				}
				
				break;
			}
		}
		if(!isDominated){
			this.getSolutions().add(new Solution(coveredPolygonIds, solution, id++));
		}
	}
	
	public List<Point2D> getSolutionPoints(){
		List<Point2D> result = new ArrayList<Point2D>();
		for(Solution s : getSolutions()){
			result.add(s.getSolutionPoint());
		}
		return result;
	}
	
	public List<Solution> getSolutions() {
		return solutions;
	}


	class Solution{
		private HashSet<Integer> coveredPolygonIds;
		private Point2D solutionPoint;
		private int id;

		public Solution(HashSet<Integer> coveredPolygonIds, Point2D solution,
				int id) {
			super();
			this.coveredPolygonIds = coveredPolygonIds;
			this.solutionPoint = solution;
			this.id = id;
		}

		public HashSet<Integer> getCoveredPolygonIds(){
			return this.coveredPolygonIds;
		}
		public int getId(){
			return this.id;
		}
		public Point2D getSolutionPoint() {
			return solutionPoint;
		}
	}
}
