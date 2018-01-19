
import javax.json.JsonObject;

import org.junit.Test;

import software.amazon.awssdk.core.auth.AwsCredentials;
import software.amazon.awssdk.core.auth.DefaultCredentialsProvider;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.apache.ApacheSdkHttpClientFactory;

public class HTTPSignerTest {

	//@Test
	public void awsHttpTest() throws Exception {
		DefaultCredentialsProvider provider = DefaultCredentialsProvider.create();
		AwsCredentials awsCredentials = provider.getCredentials();
		SdkHttpClient sdkClient = ApacheSdkHttpClientFactory.builder().build().createHttpClient();
		AWSSignerHttpClient client = AWSSignerHttpClient.builder().awsCredentials(awsCredentials).region(Region.US_WEST_1).serviceName("es").sdkClient(sdkClient).build();

		SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).protocol("https").host("search-some-aws-elasticsearch-domain.us-west-1.es.amazonaws.com").build();
		JsonObject obj = client.<JsonObject> execute(httpRequest);

		System.out.format("Result: %s\n", obj);

	}

}
