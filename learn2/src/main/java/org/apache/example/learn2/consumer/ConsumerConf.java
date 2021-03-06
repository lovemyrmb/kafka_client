package org.apache.example.learn2.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;
import java.util.UUID;

/**
 * 消费者配置
 *
 * @author maochao
 * @date 2019/6/15
 */
public class ConsumerConf {
    public static final String url = "39.100.104.199:9092";
    public static final String topic = "learn2";

    public static Properties initProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, url);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 自定义解码器
        // properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KryoDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtostuffDerializer.class.getName());
        return properties;
    }
}
