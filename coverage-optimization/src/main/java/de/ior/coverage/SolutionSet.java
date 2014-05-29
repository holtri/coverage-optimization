package de.ior.coverage;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolutionSet {
    private static final Logger _log = LogManager.getLogger(SolutionSet.class.getName());

	int id = 1;
	
//	private List<Solution> solutions;
	private HashMap<Integer, Solution> solutions;

	public SolutionSet(){
//		this.solutions = new ArrayList<Solution>();
		this.solutions = new HashMap<Integer, SolutionSet.Solution>();
	}
	
	public void addSolution(Point2D solution, HashSet<Integer> coveredPolygonIds){
		boolean isDominated = false;
		
		for(Integer i : getSolutions().keySet()){
			Solution s = solutions.get(i);
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
//			this.getSolutions().add(new Solution(coveredPolygonIds, solution, id++));
			this.getSolutions().put(id, new Solution(coveredPolygonIds, solution, id));
			id++;
		}
	}
	
//	public List<Point2D> getSolutionPoints(){
//		List<Point2D> result = new ArrayList<Point2D>();
//		for(Solution s : getSolutions()){
//			result.add(s.getSolutionPoint());
//		}
//		
//		for(Solution)
//		return result;
//	}
	
//	public List<Solution> getSolutions() {
//		return solutions;
//	}

	public HashMap<Integer, Solution> getSolutions(){
		return solutions;
	}
	
	public HashMap<Integer, HashSet<Integer>> getPolygonToSolutionMapping(){
		HashMap<Integer, HashSet<Integer>> polygonToSolution = new HashMap<Integer, HashSet<Integer>>();
		
		for(Integer i : solutions.keySet()){
			Solution s = solutions.get(i);
//			_log.info("mapping solution Id: " + s.getId() );
			for(Integer polygonId : s.getCoveredPolygonIds()){
				if(polygonToSolution.get(polygonId) == null){
					HashSet<Integer> solutionIds = new HashSet<Integer>();
					solutionIds.add(s.getId());
					polygonToSolution.put(polygonId, solutionIds);
				}else{
					polygonToSolution.get(polygonId).add(s.getId());
				}
			}
		}
		return polygonToSolution;
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
