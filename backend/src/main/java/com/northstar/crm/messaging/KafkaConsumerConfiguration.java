package com.northstar.crm.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultBackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            ProducerFactory<Object, Object> producerFactory,
            MeterRegistry meters) {
        KafkaOperations<Object, Object> rawKafkaTemplate = new KafkaTemplate<>(
                producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<? extends Object, ? extends Object>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawKafkaTemplate);
        templates.put(Object.class, kafkaTemplate);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                templates,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var failed = meters.counter("crm.kafka.messages", "topic", CustomerInteractionRecordedV1.TOPIC, "outcome", "failed");
        var retried = meters.counter("crm.kafka.messages", "topic", CustomerInteractionRecordedV1.TOPIC, "outcome", "retried");
        var deadLettered = meters.counter("crm.kafka.messages", "topic", CustomerInteractionRecordedV1.TOPIC, "outcome", "dead_lettered");
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L), new DefaultBackOffHandler() {
            @Override
            public void onNextBackOff(MessageListenerContainer container, Exception exception, long nextBackOff) {
                retried.increment();
                super.onNextBackOff(container, exception, nextBackOff);
            }
        });
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
                failed.increment();
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
                deadLettered.increment();
            }
        });
        handler.addNotRetryableExceptions(InvalidInteractionEventException.class);
        return handler;
    }
}
