package com.loganalyzer.elasticsearch.query;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.api.dto.response.AggregationResponse;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class AggregationQueryBuilder {

    public AggregationResponse fromResponse(SearchResponse<Void> response) {
        var services = new ArrayList<AggregationResponse.ServiceErrorAggregation>();
        var byService = response.aggregations().get("by_service");
        if (byService == null || byService.sterms() == null) {
            return AggregationResponse.builder().services(services).build();
        }
        byService.sterms().buckets().array().forEach(serviceBucket -> {
            var exceptions = new ArrayList<AggregationResponse.ExceptionAggregation>();
            var exceptionAgg = serviceBucket.aggregations().get("by_exception");
            if (exceptionAgg != null && exceptionAgg.sterms() != null) {
                exceptionAgg.sterms().buckets().array().forEach(exBucket -> {
                    var timeline = new ArrayList<AggregationResponse.TimeBucket>();
                    var overTime = exBucket.aggregations().get("over_time");
                    if (overTime != null && overTime.dateHistogram() != null) {
                        overTime.dateHistogram().buckets().array().forEach(bucket -> timeline.add(
                            AggregationResponse.TimeBucket.builder()
                                .timestamp(bucket.keyAsString())
                                .count(bucket.docCount())
                                .build()));
                    }
                    exceptions.add(AggregationResponse.ExceptionAggregation.builder()
                        .exception(exBucket.key().stringValue())
                        .count(exBucket.docCount())
                        .timeline(timeline)
                        .build());
                });
            }
            services.add(AggregationResponse.ServiceErrorAggregation.builder()
                .service(serviceBucket.key().stringValue())
                .totalErrors(serviceBucket.docCount())
                .exceptions(exceptions)
                .build());
        });
        return AggregationResponse.builder().services(services).build();
    }
}
