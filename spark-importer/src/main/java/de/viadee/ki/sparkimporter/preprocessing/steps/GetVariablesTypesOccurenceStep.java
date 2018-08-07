package de.viadee.ki.sparkimporter.preprocessing.steps;

import de.viadee.ki.sparkimporter.preprocessing.interfaces.PreprocessingStep;
import de.viadee.ki.sparkimporter.util.SparkImporterUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.desc;

public class GetVariablesTypesOccurenceStep implements PreprocessingStep {

    @Override
    public Dataset<Row> runPreprocessingStep(Dataset<Row> initialDataSet, boolean writeStepResultIntoFile) {

        //Determine the process instances with their variable names and types
        Dataset<Row> variablesTypesDataset = initialDataSet.select("name_", "var_type_")
                .groupBy("name_","var_type_")
                .agg(count("*").alias("occurences"));

        if(writeStepResultIntoFile) {
            SparkImporterUtils.getInstance().writeDatasetToCSV(variablesTypesDataset, "variables_types");
        }

        //just analysing the data
        return initialDataSet;
    }
}
