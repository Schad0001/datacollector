/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.alerts;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.config.MetricDefinition;
import com.streamsets.pipeline.el.ELEvaluator;
import com.streamsets.pipeline.metrics.MetricsConfigurator;
import com.streamsets.pipeline.runner.LaneResolver;
import com.streamsets.pipeline.util.ObserverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class MetricsChecker {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsChecker.class);

  private final MetricDefinition metricDefinition;
  private final MetricRegistry metrics;
  private final ELEvaluator.Variables variables;
  private final ELEvaluator elEvaluator;

  public MetricsChecker(MetricDefinition metricDefinition, MetricRegistry metrics, ELEvaluator.Variables variables,
                        ELEvaluator elEvaluator) {
    this.metricDefinition = metricDefinition;
    this.metrics = metrics;
    this.variables = variables;
    this.elEvaluator = elEvaluator;
  }

  public void recordMetrics(Map<String, List<Record>> snapshot) {
    if(metricDefinition.isEnabled()) {
      String lane = metricDefinition.getLane();
      String predicate = metricDefinition.getPredicate();
      List<Record> records = snapshot.get(LaneResolver.getPostFixedLaneForObserver(lane));
      for (Record record : records) {
        boolean success = false;
        try {
          success = AlertsUtil.evaluateRecord(record, predicate, variables, elEvaluator);
        } catch (ObserverException e) {
          //A faulty condition should not take down rest of the alerts with it.
          //Log and it and continue for now
          LOG.error("Error processing metric definition '{}', reason: {}", metricDefinition.getId(), e.getMessage());
        }
        if (success) {
          switch (metricDefinition.getMetricType()) {
            case METER:
              Meter meter = MetricsConfigurator.getMeter(metrics, metricDefinition.getId());
              if (meter == null) {
                meter = MetricsConfigurator.createMeter(metrics, metricDefinition.getId());
              }
              meter.mark();
              break;
            case HISTOGRAM:
              Histogram histogram = MetricsConfigurator.getHistogram(metrics, metricDefinition.getId());
              if (histogram == null) {
                histogram = MetricsConfigurator.createHistogram5Min(metrics, metricDefinition.getId());
              }
              histogram.update(1);
            default:
              //no-op
          }
        }
      }
    }
  }
}
