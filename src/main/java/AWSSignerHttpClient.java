

import static software.amazon.awssdk.core.config.InternalAdvancedClientOption.CRC32_FROM_COMPRESSED_DATA_ENABLED;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonStructure;

import org.apache.cxf.helpers.IOUtils;

import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkRequestOverrideConfig;
import software.amazon.awssdk.core.auth.Aws4Signer;
import software.amazon.awssdk.core.auth.AwsCredentials;
import software.amazon.awssdk.core.auth.StaticSignerProvider;
import software.amazon.awssdk.core.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.config.MutableClientConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.http.AmazonHttpClient;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.core.http.HttpResponse;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.core.interceptor.AwsExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptorChain;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;

public class AWSSignerHttpClient {

	private String serviceName;
	private Region region;
	private AwsCredentials awsCredentials;
	private SdkHttpClient sdkClient;
	// required by client to avoid NPE
	private SdkRequest sdkRequest = new ServiceSDKRequest();
	private ExecutionInterceptorChain execInterceptorChain = new ExecutionInterceptorChain(Collections.emptyList());
	private AmazonHttpClient awsClient;
	private StaticSignerProvider signingProvider;
	private ExecutionAttributes executionAttributes;

	private AWSSignerHttpClient() {

	}

	public static Builder builder() {
		return new Builder();
	}

	public <T extends JsonStructure> T execute(SdkHttpFullRequest httpRequest) {
		return execute(httpRequest, new JsonHandler<T>(), new ErrorHandler());
	}

	public <T> T execute(SdkHttpFullRequest httpRequest, HttpResponseHandler<T> responseHandler, HttpResponseHandler<? extends SdkException> errorHandler) {
		InterceptorContext incerceptorContext = InterceptorContext.builder().request(sdkRequest).httpRequest(httpRequest).build();
		ExecutionContext.Builder execContextBuilder = ExecutionContext.builder();
		execContextBuilder.signerProvider(signingProvider);
		execContextBuilder.interceptorChain(execInterceptorChain);
		execContextBuilder.executionAttributes(executionAttributes);
		execContextBuilder.interceptorContext(incerceptorContext).build();
		ExecutionContext execContext = execContextBuilder.build();
		return awsClient.requestExecutionBuilder().executionContext(execContext).originalRequest(sdkRequest).errorResponseHandler(errorHandler).request(httpRequest).execute(responseHandler);
	}

	public static class Builder {
		AWSSignerHttpClient client = new AWSSignerHttpClient();

		public AWSSignerHttpClient build() {
			Aws4Signer signer = new Aws4Signer();
			signer.setRegionName(client.region.value());
			signer.setServiceName(client.serviceName);
			client.signingProvider = StaticSignerProvider.create(signer);
			client.executionAttributes = new ExecutionAttributes().putAttribute(AwsExecutionAttributes.AWS_CREDENTIALS, client.awsCredentials);
			MutableClientConfiguration clientConfiguration = new MutableClientConfiguration();
			clientConfiguration.httpClient(client.sdkClient);
			ClientOverrideConfiguration override = ClientOverrideConfiguration.builder().advancedOption(CRC32_FROM_COMPRESSED_DATA_ENABLED, true).retryPolicy(RetryPolicy.NONE).build();
			clientConfiguration.overrideConfiguration(override);
			client.awsClient = new AmazonHttpClient(clientConfiguration);
			return client;
		}

		public Builder sdkClient(SdkHttpClient sdkClient) {
			client.sdkClient = sdkClient;
			return this;
		}

		public Builder serviceName(String serviceName) {
			client.serviceName = serviceName;
			return this;
		}

		public Builder region(Region region) {
			client.region = region;
			return this;
		}

		public Builder awsCredentials(AwsCredentials awsCredentials) {
			client.awsCredentials = awsCredentials;
			return this;
		}
	}

	public static class ServiceSDKRequest extends SdkRequest {

		@Override
		public Optional<? extends SdkRequestOverrideConfig> requestOverrideConfig() {
			return Optional.empty();
		}

		@Override
		public Builder toBuilder() {
			return new Builder() {

				@Override
				public SdkRequestOverrideConfig requestOverrideConfig() {
					return null;
				}

				@Override
				public SdkRequest build() {
					return new ServiceSDKRequest();
				}

			};
		}

	}

	public static class ErrorHandler implements HttpResponseHandler<SdkException> {

		@Override
		public SdkException handle(HttpResponse response, ExecutionAttributes executionAttributes) throws Exception {
			String responseMsg = IOUtils.toString(new InputStreamReader(response.getContent()));
			return new SdkClientException(String.format("%d: %s", response.getStatusCode(), responseMsg));
		}

	}

	public static class JsonHandler<T extends JsonStructure> implements HttpResponseHandler<T> {

		@Override
		public T handle(HttpResponse response, ExecutionAttributes executionAttributes) throws Exception {
			JsonStructure responseValue = Json.createReader(response.getContent()).read();
			return (T) responseValue;
		}

	}
}
