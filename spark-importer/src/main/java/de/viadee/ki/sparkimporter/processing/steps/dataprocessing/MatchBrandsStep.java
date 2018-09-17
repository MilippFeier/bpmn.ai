package de.viadee.ki.sparkimporter.processing.steps.dataprocessing;

import de.viadee.ki.sparkimporter.processing.interfaces.PreprocessingStepInterface;
import de.viadee.ki.sparkimporter.util.SparkImporterVariables;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static org.apache.spark.sql.functions.*;
import info.debatty.java.stringsimilarity.*;

public class MatchBrandsStep implements PreprocessingStepInterface {

	@Override
	public Dataset<Row> runPreprocessingStep(Dataset<Row> dataset, boolean writeStepResultIntoFile, String dataLevel) {

		final SparkSession sparkSession = SparkSession.builder().getOrCreate();

		String herstellercolumn = "int_fahrzeugHerstellernameAusVertrag";

		Dataset<Row> levenshteinds = LevenshteinMatching(dataset, sparkSession, herstellercolumn);
		Dataset<Row> matchedds = regexMatching(levenshteinds, sparkSession, herstellercolumn);

		return matchedds;
	}

	// Perform similarity matching of the brands using the levenshtein score
	public static Dataset<Row> LevenshteinMatching(Dataset<Row> ds, SparkSession s, String herstellercolumn) {

		
		// read matching data in a 2-dim array
		String fileName = "C:\\Users\\B77\\Desktop\\Glasbruch-Mining\\car_brands.csv";
		File file = new File(fileName);

		// return a 2-dimensional array of strings
		List<List<String>> brandsList = new ArrayList<>();
		Scanner inputStream;
		try {
			inputStream = new Scanner(file);
			while (inputStream.hasNext()) {
				String line = inputStream.next();
				String[] values = line.split(";");
				brandsList.add(Arrays.asList(values));
			}
			inputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
				

		// create user defined function
		s.udf().register("levenshteinMatching", new UDF1<String, String>() {
			
			public String call(String column) throws Exception {
				
	
				String brandOutput = "SONSTIGE";
				// discard not useful chars
				column = column.toUpperCase();
				column = column.replaceAll("[\\-,1,2,3,4,5,6,7,8,9,0,\\.,\\,\\_,\\+,\\),\\(,/\\s/g]", "");
				
				
				int lineNo = 1;
				double score = 1;
				NormalizedLevenshtein lev = new NormalizedLevenshtein();
				
				// traverse brand list and select the brand with the best score
				for (List<String> line : brandsList) {
					int columnNo = 1;
					String brand = line.get(1);
					double levScore = lev.distance(column, brand);
		
					if(levScore < score) {
						score = levScore;
						brandOutput = brand;
					}			
					lineNo++;				
				}
				if(score > 0.4) {
					brandOutput = "SONSTIGE";
				}
				if(column.equals("") || column.equals("-")) {
					brandOutput = "UNBEKANNT";
				}

				return brandOutput;
			}
		}, DataTypes.StringType);
		
		// call UDF for specific columns	
		ds = ds.withColumn("brand",callUDF("levenshteinMatching", ds.col(herstellercolumn)));
	
		
		return ds;
	}

	// applies the regexp functions saved in a csv file to the brands of the dataset
	public static Dataset<Row> regexMatching(Dataset<Row> dataset, SparkSession s, String herstellercolumn) {

		// read matching data in a 2-dim array
				String fileName = "C:\\Users\\B77\\Documents\\datasets\\brandmatching.csv";
				File file = new File(fileName);

				// return a 2-dimensional array of strings
				List<List<String>> brandsRegexp = new ArrayList<>();
				Scanner inputStream;
				try {
					inputStream = new Scanner(file);
					while (inputStream.hasNext()) {
						String line = inputStream.next();
						String[] values = line.split(";");
						brandsRegexp.add(Arrays.asList(values));
					}
					inputStream.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

				String[] columns = dataset.columns();
			
				// traverse the dataset
				dataset = dataset.map(row -> {

					Object[] newRow = new Object[columns.length];
					int columnCount = 0;
					for (String c : columns) {
						Object columnValue = null;
									
						// if brand is not matched
						if (c.equals("brand") && row.getAs(c).equals("SONSTIGE")) {
					
							String regexpValue = null;
							int lineNo = 1;

							// traverse the matching list
							for (List<String> line : brandsRegexp) {
								int columnNo = 1;
								String brandMatch = line.get(0);
								String regExpBrand = line.get(1);
								lineNo++;

								// replace value with regexp from matching list
								columnValue = ((String) row.getAs(herstellercolumn)).replaceAll(regExpBrand, brandMatch);

								// stop loop if value is already replaced and otherwise the value stays
								// "Sonstige"
								if ((String) row.getAs(herstellercolumn) != columnValue) {
									break;
								} else {
									columnValue = "SONSTIGE";
								}
							}
						}
						// the value of all the other columns stay the same
						else {
							columnValue = row.getAs(c);
						}
						newRow[columnCount++] = columnValue;
					}

					return RowFactory.create(newRow);
				}, RowEncoder.apply(dataset.schema()));

			
		
				// remove all unnecessary columns
				dataset = dataset.withColumn(herstellercolumn,dataset.col("brand"));
				dataset = dataset.drop("brand");
				return dataset;

	}

}
