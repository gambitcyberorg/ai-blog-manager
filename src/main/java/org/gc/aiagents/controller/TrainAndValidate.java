package org.gc.aiagents.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.gc.aiagents.domain.TrainingData;
import org.gc.aiagents.domain.ValidationData;

import java.util.List;

@Data
public class TrainAndValidate {

    @JsonProperty("training_data")
    private List<TrainingData> trainingData;

    @JsonProperty("validation_data")
    private List<ValidationData> validationData;
}
