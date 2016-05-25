package perflab.CSVAnalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.TimeZone;
import java.sql.*;
import java.time.ZoneId;

import org.relique.jdbc.csv.CsvDriver;

import antlr.StringUtils;
import hudson.Util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.Day;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;

public class Analysis {
	
	private PrintStream logger;
	private String csvFiles;
	private String outputFiles;
	private String extraKeyFilter;
	
	public Analysis(String csvFiles, String outputFiles, String extraKeyFilter, PrintStream logger)
	{
		this.csvFiles = csvFiles;
		this.logger = logger;
		this.outputFiles = outputFiles;
		this.extraKeyFilter = extraKeyFilter;
	}
	
	/**
     * Execute sequence of Controller and Analysis
     *
     * @return
     */
	public boolean execute() {

		boolean okay = true;

		try
	    {
			
		  logger.println("[Analysis] : Executing CSV analysis for folder " + this.csvFiles);
			
		  Collection<File> filesToAnalyze = getAllFilesThatMatchFilenameExtension(csvFiles, "csv");
		  for (File oneFile : filesToAnalyze){
			  normalizeCSVHeader(oneFile);
		  }

	      // Load the driver.
	      Class.forName("org.relique.jdbc.csv.CsvDriver");
	      Connection conn = DriverManager.getConnection("jdbc:relique:csv:" + this.csvFiles);

	      //////////////////////////////////////////////////////////////////////
	      LinkedList<String> metrics = new LinkedList<String>();
	      logger.println("[Analysis]: Collecting list of metrics in all files");
		  for (File oneFile : filesToAnalyze){
			  String scvFileNoExtension = FilenameUtils.getBaseName(oneFile.getName());			  
			  LinkedList<String> mtrx = getCustomColumns(conn, scvFileNoExtension);
			  for(String m : mtrx) {
				  if(!metrics.contains(m))
				  {
					  metrics.add(m);
					  logger.println("Metric name = " + m);
				  }
			  }
		  }


	    	  ///////////////////////////////////////////////////////////////////
	    	  int metricIdx = 0;
	    	  int numOfMetrics = metrics.size();
		      for(String metric : metrics) {
		    	  metricIdx++;
		    	  logger.println("#####\tExtracting metric (" + metricIdx + "/" + numOfMetrics + ") : " + metric);
		    	  
		    	  TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
		    	  
				  //////////////////////////////////////////////////////////////////////
				  for (File oneFile : filesToAnalyze){
			    	  //logger.println("[Analysis]:analyzing file: " + oneFile);
			    	  
			    	  // Select the ID and NAME columns from sample.csv
			    	  String scvFileNoExtension = FilenameUtils.getBaseName(oneFile.getName());
				      // Retrieve custom column names
			    	  LinkedList<KeyObject> uniqueKeys = getUniqueKeys(conn, scvFileNoExtension);
			    	  
			    	  int keyIdx = 0;
			    	  int numOfKeys = uniqueKeys.size();
			    	  
			    	  try{
			    		  for(KeyObject key : uniqueKeys){
				    		  keyIdx++;
				    		  logger.println("#####\t\t(" + keyIdx + "/" + numOfKeys + ") from " + scvFileNoExtension);
				    		  TimeSeries tseries = getDataset(conn, scvFileNoExtension, metric, key.getWhereClause(), key);	
				    		  seriesCollection.addSeries(tseries);
				    	  } 
			    	  }catch(SQLException sqlex){
			    		  logger.println("[ERROR]\t\tCan't extract "+metric+" form  " + scvFileNoExtension + " : " + sqlex.getMessage());
			    	  }
				  }/// end of files loop
		    	  
		    	  String imageFile = csvFiles + "\\"+metric+".png";
		    	  logger.println(String.format("#####\tDrawing [%s] to [%s]", metric, imageFile));

			      JFreeChart timechart = ChartFactory.createTimeSeriesChart(
					         metric,           //title
					         "Time",           //x label
					         "Value",          //y label
					         seriesCollection, //dataset
					         true,             //legend 
					         true,             //tooltips
					         false);            //generate urls ?

			      int width = 1024;  //Width of the image 
			      int height = 768;  //Height of the image
			      File timeChartFile = new File( csvFiles + "\\"+metric+".png" );
			      
			      ChartUtilities.saveChartAsPNG(timeChartFile, timechart, width, height);
			      logger.println("#####################################################################");
		      }
		  
	      
	      // Clean up
	      conn.close();
	    }
	    catch(Exception e)
	    {
	    	e.printStackTrace();
	    	okay = false;
	    }
		
		return okay;
	}
	
	private void normalizeCSVHeader(File csvFile) throws IOException {

		RandomAccessFile raInputFile = new RandomAccessFile(csvFile, "rw");
		String origHeaderRow = raInputFile.readLine();
		
		origHeaderRow = origHeaderRow.replaceAll(" ", "_");             //replace all spaces
		origHeaderRow = origHeaderRow.replaceAll("[^A-Za-z0-9,_]", "_");//replace all non alpha numeric, not comma and not _
		origHeaderRow = origHeaderRow.replaceFirst(",$", " ");          //remove trailing comma
		
		raInputFile.seek(0);
		raInputFile.writeBytes(origHeaderRow);
		raInputFile.close();
		
		logger.println("Finished to fix file " + csvFile);
	}

	private TimeSeries getDataset(Connection conn, String table, String metric, String where, KeyObject key) throws SQLException{
		String query = "SELECT Timestamp, "+ metric +
						" FROM " + table + 
						" WHERE " + where +
						" ORDER BY Timestamp";
		
		Statement stmt = conn.createStatement();
		ResultSet results = stmt.executeQuery(query);
		
		//logger.println("query="+query);
		//CsvDriver.writeToCsv(results, logger, true);

		String seriesName = key.processName + " (" + key.pid + ")";
		
		TimeSeries timeSeries = new TimeSeries(seriesName, Second.class);
		
		while(results.next()){
			long secondsFrom1970 = Long.parseLong(results.getString("Timestamp"));
			double value = Double.parseDouble(results.getString(metric));

			//Create time series object
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(secondsFrom1970*1000);
			
			Second timestamp = new Second(cal.get(Calendar.SECOND), cal.get(Calendar.MINUTE), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
			
			try {
				//logger.println("Adding ["+timestamp+"] ["+value+"]");
				//timeSeries.add(timestamp, value);
				timeSeries.addOrUpdate(timestamp, value);
			}catch(SeriesException sx){				
				TimeSeriesDataItem alreadyThere = timeSeries.getDataItem(timestamp);
				
				logger.println(String.format("Can't add to [%s] metric [%s] : timestamp [%s] value [%s] - there:[%s] length:[%d]", 
						seriesName, 
						metric, 
						secondsFrom1970, 
						value,
						alreadyThere.getValue(),
						timeSeries.getItemCount()));
				logger.println(sx);
			}
		}
		
		return timeSeries;
	}
	
	// Retrieve list of unique keys (HOST + process + PID) combinations
	private LinkedList<KeyObject> getUniqueKeys(Connection conn, String scvFileNoExtension) throws SQLException {
		// Create a Statement object to execute the query with.
		Statement stmt = conn.createStatement();
		
		//SELECT Host, Process, PID 
		//FROM runner1_unixMonitor_31697 
		//WHERE Process = 'java' OR Process = 'node' 
		//GROUP BY Host, Process, PID  
		//ORDER BY Host, Process, PID
		
		String keysQuery = "SELECT Host, Process, PID FROM " + scvFileNoExtension;
		
		if(this.extraKeyFilter != "" && !this.extraKeyFilter.isEmpty()){
			keysQuery += " WHERE " + this.extraKeyFilter;
		}
		
		keysQuery += " GROUP BY Host, Process, PID "+
					 " ORDER BY Host, Process, PID ";
		
		ResultSet results = stmt.executeQuery(keysQuery);
		
		LinkedList<KeyObject> uniqueKeysObjects = new LinkedList<KeyObject>();
		
		while(results.next())
		{
			KeyObject key = new KeyObject(results.getString("Host"), results.getString("Process"), results.getString("PID"));
			uniqueKeysObjects.add(key);			
		}
		return uniqueKeysObjects;
	}

	private LinkedList<String> getCustomColumns(Connection conn, String scvFileNoExtension) throws SQLException {
		// Retrieve custom column names
		LinkedList<String> customColumns = new LinkedList<String>();
		  
		// Create a Statement object to execute the query with.
		Statement stmt = conn.createStatement();
		  
		ResultSet results = stmt.executeQuery("SELECT * FROM " + scvFileNoExtension + " LIMIT 1 OFFSET 1");				      
		ResultSetMetaData meta = results.getMetaData();
		for (int i = 4; i < meta.getColumnCount(); i++)
		{
			//logger.println("[Analysis]: Column names: " + meta.getColumnName(i + 1) + " " + results.getString(i + 1));
			customColumns.add(meta.getColumnName(i + 1));
			//logger.println("[Analysis]: Column names: " + meta.getColumnName(i + 1));
		}
		return customColumns;
	}

	private Collection getAllFilesThatMatchFilenameExtension(String directoryName, String extension)
	{
	  File directory = new File(directoryName);
	  
	  return FileUtils.listFiles(directory, new String[] {extension} , true);
	}
	
    /*private void openCollapse(String message) {
    	logger.println("<div class=\"collapseHeader\">" + Util.escape(message));
    	logger.println("<div class=\"collapseAction\"><p onClick=\"doToggle(this)\">Hide Details</p></div></div><div class=\"expanded\">");
    }*/

    
    private void closeCollapse() {
        logger.println("</div>");
    }
    
	
	class KeyObject{
		private String hostName;
		private String processName;
		private String pid;
		
		public KeyObject (String hostName, String processName, String pid){
			this.hostName = hostName;
			this.processName = processName;
			this.pid = pid;
		}
		
		public String toString(){
			return this.hostName + ":" + this.processName + ":" + this.pid;
		}
		
		public String getWhereClause(){
			return "Host = '" + this.hostName + "' AND Process='" + this.processName +"' AND PID='"+ this.pid + "'";
		}
	}
}
