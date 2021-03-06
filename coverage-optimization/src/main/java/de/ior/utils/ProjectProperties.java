package de.ior.utils;

import java.io.FileInputStream;
import java.util.Properties;

public class ProjectProperties {


	private static double circleRadius;
	private static Properties properties;
	static {
		try {
			setProperties(new Properties());
			FileInputStream inStream = new FileInputStream("project.properties");
			getProperties().load(inStream);
			inStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static Properties getProperties() {
		return properties;
	}
	public static void setProperties(Properties properties) {
		ProjectProperties.properties = properties;
	}

	public static double getCircleRadius() {
		return circleRadius;
	}
	public static void setCircleRadius(double circleRadius) {
		ProjectProperties.circleRadius = circleRadius;
	}

}
