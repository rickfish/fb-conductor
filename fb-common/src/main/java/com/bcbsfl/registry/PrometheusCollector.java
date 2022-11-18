package com.bcbsfl.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Tag;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

/**
 * Collect metrics from spectator CompositeRegistry in this case,
 * Spectator.globalRegistry()
 *
 */
public class PrometheusCollector extends Collector {

	static private List<TypeIndicator> counterTypes = new ArrayList<TypeIndicator>();
	static private List<TypeIndicator> gaugeTypes = new ArrayList<TypeIndicator>();
	static private List<TypeIndicator> histogramTypes = new ArrayList<TypeIndicator>();
	static private List<TypeIndicator> summaryTypes = new ArrayList<TypeIndicator>();

	static {
		counterTypes.add(new TypeIndicator("workflow_server_error"));
		counterTypes.add(new TypeIndicator("task_poll_error"));
		counterTypes.add(new TypeIndicator("task_poll"));
		counterTypes.add(new TypeIndicator("task_poll_count"));
		counterTypes.add(new TypeIndicator("task_timeout"));
		counterTypes.add(new TypeIndicator("task_response_timeout"));
		counterTypes.add(new TypeIndicator("workflow_failure"));
		counterTypes.add(new TypeIndicator("workflow_start_error"));
		counterTypes.add(new TypeIndicator("task_update_conflict"));
		counterTypes.add(new TypeIndicator("task_update_error"));
		counterTypes.add(new TypeIndicator("task_queue_op_error"));
		counterTypes.add(new TypeIndicator("event_queue_messages_processed"));
		counterTypes.add(new TypeIndicator("observable_queue_error"));
		counterTypes.add(new TypeIndicator("event_queue_messages_handled"));
		counterTypes.add(new TypeIndicator("event_queue_messages_error"));
		counterTypes.add(new TypeIndicator("event_execution_success"));
		counterTypes.add(new TypeIndicator("event_execution_error"));
		counterTypes.add(new TypeIndicator("event_action_error"));
		counterTypes.add(new TypeIndicator("dao_requests"));
		counterTypes.add(new TypeIndicator("external_payload_storage_usage"));
		counterTypes.add(new TypeIndicator("dao_errors"));
		counterTypes.add(new TypeIndicator("task_ack_error"));
		counterTypes.add(new TypeIndicator("task_ack_error"));
		counterTypes.add(new TypeIndicator("conductor_endpoint_failure"));
		counterTypes.add(new TypeIndicator("conductor_endpoint_timing", "statistic"));
		counterTypes.add(new TypeIndicator("total_count"));
		counterTypes.add(new TypeIndicator("discarded_index_count"));
		counterTypes.add(new TypeIndicator("acquire_lock_unsuccessful"));
		counterTypes.add(new TypeIndicator("acquire_lock_failure"));
		counterTypes.add(new TypeIndicator("workflow_archived"));
		counterTypes.add(new TypeIndicator("discarded_archival_count"));
		counterTypes.add(new TypeIndicator("system_task_worker_polling_limited"));
		counterTypes.add(new TypeIndicator("queue_message_repushed"));
		
		gaugeTypes.add(new TypeIndicator("task_queue_depth"));
		gaugeTypes.add(new TypeIndicator("task_in_progress"));
		gaugeTypes.add(new TypeIndicator("workflow_running"));
		gaugeTypes.add(new TypeIndicator("task_pending_time"));
		gaugeTypes.add(new TypeIndicator("task_rate_limited"));
		gaugeTypes.add(new TypeIndicator("task_concurrent_execution_limited"));
		gaugeTypes.add(new TypeIndicator("dao_payload_size"));
		gaugeTypes.add(new TypeIndicator("indexing_worker_queue"));
		gaugeTypes.add(new TypeIndicator("workflow_archival_delay_queue_size"));
		gaugeTypes.add(new TypeIndicator("event_queue_poll"));
		gaugeTypes.add(new TypeIndicator("conductor_endpoint_timing", "unit"));
	}
	
	public PrometheusCollector() {
		// this.registry = registry;
	}

	@Override
	public List<MetricFamilySamples> collect() {
		List<MetricFamilySamples> familySamples = new ArrayList<MetricFamilySamples>();
		Registry registry = Spectator.globalRegistry();
		if (registry == null) {
			return familySamples;
		}

		Map<String, List<Sample>> untypedSamples = new HashMap<String, List<Sample>>();
		Map<String, List<Sample>> counterSamples = new HashMap<String, List<Sample>>();
		Map<String, List<Sample>> gaugeSamples = new HashMap<String, List<Sample>>();
		Map<String, List<Sample>> histogramSamples = new HashMap<String, List<Sample>>();
		Map<String, List<Sample>> summarySamples = new HashMap<String, List<Sample>>();
		
		Iterator<Meter> meters = registry.iterator();
		while (meters.hasNext()) {
			Meter meter = meters.next();
			if (meter != null) {
				Iterator<Measurement> measurements = meter.measure().iterator();
				while (measurements.hasNext()) {
					addSample(measurements.next(), untypedSamples, counterSamples, gaugeSamples, histogramSamples, summarySamples);
				}
			}
		}

		if(counterSamples.size() > 0) {
			counterSamples.keySet().forEach(sampleName -> {
				familySamples.add(new MetricFamilySamples(sanitizeMetricName(sampleName), Type.COUNTER, "generated from Spectator CompositeRegistry", counterSamples.get(sampleName)));
			});
		}

		if(gaugeSamples.size() > 0) {
			gaugeSamples.keySet().forEach(sampleName -> {
				familySamples.add(new MetricFamilySamples(sanitizeMetricName(sampleName), Type.GAUGE, "generated from Spectator CompositeRegistry", gaugeSamples.get(sampleName)));
			});
		}

		if(histogramSamples.size() > 0) {
			histogramSamples.keySet().forEach(sampleName -> {
				familySamples.add(new MetricFamilySamples(sanitizeMetricName(sampleName), Type.HISTOGRAM, "generated from Spectator CompositeRegistry", histogramSamples.get(sampleName)));
			});
		}

		if(summarySamples.size() > 0) {
			summarySamples.keySet().forEach(sampleName -> {
				familySamples.add(new MetricFamilySamples(sanitizeMetricName(sampleName), Type.SUMMARY, "generated from Spectator CompositeRegistry", summarySamples.get(sampleName)));
			});
		}

		if(untypedSamples.size() > 0) {
			untypedSamples.keySet().forEach(sampleName -> {
				familySamples.add(new MetricFamilySamples(sanitizeMetricName(sampleName), Type.UNTYPED, "generated from Spectator CompositeRegistry", untypedSamples.get(sampleName)));
			});
		}
		return familySamples;
	}

	private void addSample(Measurement measurement, Map<String, List<Sample>> untypedSamples, Map<String, List<Sample>> counterSamples, 
			Map<String, List<Sample>> gaugeSamples, Map<String, List<Sample>> histogramSamples, Map<String, List<Sample>> summarySamples) {
		boolean	added = addSample(measurement, counterTypes, counterSamples);
		if(!added) {
			added = addSample(measurement, gaugeTypes, gaugeSamples);
		}
		if(!added) {
			added = addSample(measurement, histogramTypes, histogramSamples);
		}
		if(!added) {
			added = addSample(measurement, summaryTypes, summarySamples);
		}
		if(!added) {
			List<Sample> sampleList = getSampleList(measurement, untypedSamples);
			sampleList.add(convertMeasurementToSample(measurement));
		}
	}
	
	private boolean addSample(Measurement measurement, List<TypeIndicator> typeIndicators, Map<String, List<Sample>> samples) {
		for(TypeIndicator ti : typeIndicators) {
			if(measurement.id().name() != null && measurement.id().name().startsWith(ti.metricName)) {
				if(ti.tag == null) {
					List<Sample> sampleList = getSampleList(measurement, samples);
					sampleList.add(convertMeasurementToSample(measurement));
					return true;
				} else if(measurement.id().tags() != null) {
					Iterator<Tag> tags = measurement.id().tags().iterator();
					while(tags.hasNext()) {
						Tag tag = tags.next();
						if(tag.key().equals(ti.tag)) {
							List<Sample> sampleList = getSampleList(measurement, samples);
							sampleList.add(convertMeasurementToSample(measurement));
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	private List<Sample> getSampleList(Measurement measurement, Map<String, List<Sample>> samples) {
		List<Sample> sampleList = samples.get(measurement.id().name());
		if(sampleList == null) {
			sampleList = new ArrayList<Sample>();
			samples.put(measurement.id().name(), sampleList);
		}
		return sampleList;
	}
	
	private Sample convertMeasurementToSample(Measurement measurement) {
		String prometheusName = sanitizeMetricName(measurement.id().name());
		List<String> labelNames = new ArrayList<String>();
		List<String> labelValues = new ArrayList<String>();
		for (Tag tag : measurement.id().tags()) {
			labelNames.add(tag.key());
			labelValues.add(tag.value());
		}
		return new Sample(prometheusName, labelNames, labelValues, measurement.value());
	}

	private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");

	/**
	 * Replace all unsupported chars with '_', prepend '_' if name starts with
	 * digit.
	 *
	 * @param originalName original metric name.
	 * @return the sanitized metric name.
	 */
	public static String sanitizeMetricName(String originalName) {
		String name = METRIC_NAME_RE.matcher(originalName).replaceAll("_");
		if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
			name = "_" + name;
		}
		return name;
	}
	
	static public class TypeIndicator {
		String metricName = null;
		String tag = null;
		public TypeIndicator(String metricName) {
			this.metricName = metricName;
		}
		public TypeIndicator(String metricName, String tag) {
			this.metricName = metricName;
			this.tag = tag;
		}
	}
}