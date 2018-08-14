package de.viadee.ki.sparkimporter;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import de.viadee.ki.sparkimporter.exceptions.NoDataImporterDefinedException;
import de.viadee.ki.sparkimporter.importing.DataImportRunner;
import de.viadee.ki.sparkimporter.importing.implementations.CSVDataImporter;
import de.viadee.ki.sparkimporter.preprocessing.PreprocessingRunner;
import de.viadee.ki.sparkimporter.preprocessing.aggregation.AllButEmptyStringAggregationFunction;
import de.viadee.ki.sparkimporter.preprocessing.steps.AddVariablesColumnsStep;
import de.viadee.ki.sparkimporter.preprocessing.steps.AggregateToProcessInstanceaStep;
import de.viadee.ki.sparkimporter.preprocessing.steps.GetVariablesTypesOccurenceStep;
import de.viadee.ki.sparkimporter.preprocessing.steps.VariablesTypeEscalationStep;
import de.viadee.ki.sparkimporter.util.SparkImporterArguments;
import de.viadee.ki.sparkimporter.util.SparkImporterUtils;

public class SparkImporterApplication {

	private static final Logger LOG = LoggerFactory.getLogger(SparkImporterApplication.class);
	public static SparkImporterArguments ARGS;

	public static void main(String[] arguments) {

		final long startMillis = System.currentTimeMillis();

		ARGS = SparkImporterArguments.getInstance();

		// instantiate JCommander
		// Use JCommander for flexible usage of Parameters
		final JCommander jCommander = JCommander.newBuilder().addObject(SparkImporterArguments.getInstance()).build();
		try {
			jCommander.parse(arguments);
		} catch (final ParameterException e) {
			LOG.error("Parsing of parameters failed. Error message: " + e.getMessage());
			jCommander.usage();
			System.exit(1);
		}

		// SparkImporter code starts here

		// Delete destination files, required to avoid exception during runtime
		FileUtils.deleteQuietly(new File(ARGS.getFileDestination()));

		// Configuration is being loaded from Environment (e.g. when using spark-submit)
		final SparkSession sparkSession = SparkSession.builder().getOrCreate();

		// register our own aggregation function
		sparkSession.udf().register("AllButEmptyString", new AllButEmptyStringAggregationFunction());

		// Import data
		final DataImportRunner dataImportRunner = DataImportRunner.getInstance();

		// Import from CSV file
		dataImportRunner.setDataImporter(new CSVDataImporter());
		Dataset<Row> dataset = null;
		try {
			dataset = dataImportRunner.runImport(sparkSession);
		} catch (final NoDataImporterDefinedException e) {
			LOG.error("Could not import data.", e);
			System.exit(1);
		}

		// write imported CSV structure to file for debugging
		if (SparkImporterArguments.getInstance().isWriteStepResultsToCSV()) {
			SparkImporterUtils.getInstance().writeDatasetToCSV(dataset, "import_result");
		}

		// remove duplicated columns created at CSV import step
		dataset = SparkImporterUtils.getInstance().removeDuplicatedColumnsFromCSV(dataset);
		dataset = SparkImporterUtils.getInstance().removeEmptyLinesAfterImport(dataset);

		// write imported unique column CSV structure to file for debugging
		if (SparkImporterArguments.getInstance().isWriteStepResultsToCSV()) {
			SparkImporterUtils.getInstance().writeDatasetToCSV(dataset, "import_unique_columns_result");
		}

		// Define preprocessing steps to run
		final PreprocessingRunner preprocessingRunner = PreprocessingRunner.getInstance();

		// it's faster if we do not reduce the dataset columns in the beginning and
		// rejoin the dataset later, left steps in commented if required later
		// preprocessingRunner.addPreprocessorStep(new ReduceColumnsDatasetStep());
		preprocessingRunner.addPreprocessorStep(new GetVariablesTypesOccurenceStep());
		preprocessingRunner.addPreprocessorStep(new VariablesTypeEscalationStep());
		preprocessingRunner.addPreprocessorStep(new AddVariablesColumnsStep());
		preprocessingRunner.addPreprocessorStep(new AggregateToProcessInstanceaStep());
		// preprocessingRunner.addPreprocessorStep(new
		// AddRemovedColumnsToDatasetStep());

		// Run preprocessing runner
		preprocessingRunner.run(dataset, SparkImporterArguments.getInstance().isWriteStepResultsToCSV());

		// Cleanup
		sparkSession.close();

		final long endMillis = System.currentTimeMillis();

		LOG.info("Job ran for " + ((endMillis - startMillis) / 1000) + " seconds in total.");
	}

}
