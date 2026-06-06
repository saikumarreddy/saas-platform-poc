package com.saasplatform.ingestion.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class LocalStackTestBase {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest")
    )
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.EVENTBRIDGE);

    protected static S3Client s3Client;
    protected static DynamoDbClient dynamoDbClient;
    protected static EventBridgeClient eventBridgeClient;

    static {
        localStack.start();
        initializeClients();
    }

    private static void initializeClients() {
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                ))
                .region(Region.US_EAST_1)
                .build();

        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create(localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                ))
                .region(Region.US_EAST_1)
                .build();

        eventBridgeClient = EventBridgeClient.builder()
                .endpointOverride(URI.create(localStack.getEndpointOverride(LocalStackContainer.Service.EVENTBRIDGE).toString()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                ))
                .region(Region.US_EAST_1)
                .build();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.region", () -> "us-east-1");
        registry.add("spring.cloud.aws.s3.endpoint", () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("spring.cloud.aws.dynamodb.endpoint", () -> localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
    }
}
