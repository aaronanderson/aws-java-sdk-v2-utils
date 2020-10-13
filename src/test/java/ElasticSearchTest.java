import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

public class ElasticSearchTest {

    public static final String ELASTIC_SEARCH_HOST = "XXXXXXXXXXXXXX.us-west-1.es.amazonaws.com";
    

    //@Test
    public void searchTest() throws Exception {
        AWSSignerHttpClient client = AWSSignerHttpClient.builder().serviceName("es").awsCredentials(AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create())).build();
        SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).protocol("https").host(ELASTIC_SEARCH_HOST).appendRawQueryParameter("pretty", "true").build();
        JsonObject obj = client.<JsonObject> execute(httpRequest);
        assertNotNull(obj);
        System.out.format("Result: %s\n", obj);

    }

    //@Test
    public void createIndexTest() throws Exception {
        AWSSignerHttpClient client = AWSSignerHttpClient.builder().serviceName("es").readTimeout(Duration.ofMinutes(1)).awsCredentials(AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create())).build();

        String json = "{\"settings\": {  \"index\": { \"number_of_shards\": 5, \"number_of_replicas\": 1 } } }";
        SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.PUT).protocol("https").host(ELASTIC_SEARCH_HOST).encodedPath("/test-index").appendRawQueryParameter("pretty", "true").contentStreamProvider(() -> new ByteArrayInputStream(json.getBytes())).appendHeader("Content-Type", "application/json")
                .appendHeader("Content-Length", String.valueOf(json.getBytes().length)).build();
        JsonObject obj = client.<JsonObject> execute(httpRequest);
        assertNotNull(obj);
        System.out.format("Result: %s\n", obj);

        httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.DELETE).protocol("https").host(ELASTIC_SEARCH_HOST).encodedPath("/test-index").appendRawQueryParameter("pretty", "true").build();
        obj = client.<JsonObject> execute(httpRequest);
        assertNotNull(obj);
        System.out.format("Result: %s\n", obj);
    }

}
