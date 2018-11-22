import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonStructure;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.RequestOverrideConfiguration;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptorChain;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.core.internal.http.AmazonSyncHttpClient;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.IoUtils;

public class AWSSignerHttpClient {

	private String serviceName;
	private Region region;
	private AwsCredentialsProvider awsCredentialsProvider;
	private SdkHttpClient sdkClient;
	// required by client to avoid NPE
	private SdkRequest sdkRequest = new ServiceSDKRequest();
	private ExecutionInterceptorChain execInterceptorChain = new ExecutionInterceptorChain(Collections.emptyList());
	private AmazonSyncHttpClient awsClient;
	private Aws4Signer signer;

	private AWSSignerHttpClient() {

	}

	public static Builder builder() {
		return new Builder();
	}

	public <T> T execute(SdkHttpFullRequest httpRequest, HttpResponseHandler<T> responseHandler) {
		return execute(httpRequest, responseHandler, new ErrorHandler());
	}

	public <T extends JsonStructure> T execute(SdkHttpFullRequest httpRequest) {
		return execute(httpRequest, new JsonHandler<T>(), new ErrorHandler());
	}

	public <T> T execute(SdkHttpFullRequest httpRequest, HttpResponseHandler<T> responseHandler, HttpResponseHandler<? extends SdkException> errorHandler) {
		InterceptorContext incerceptorContext = InterceptorContext.builder().request(sdkRequest).httpRequest(httpRequest).build();
		ExecutionContext.Builder execContextBuilder = ExecutionContext.builder();
		execContextBuilder.signer(signer);
		execContextBuilder.interceptorChain(execInterceptorChain);
		ExecutionAttributes executionAttributes = new ExecutionAttributes();
		executionAttributes.putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS, awsCredentialsProvider.resolveCredentials());
		executionAttributes.putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, serviceName);
		executionAttributes.putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, region);
		execContextBuilder.executionAttributes(executionAttributes);
		execContextBuilder.interceptorContext(incerceptorContext).build();
		ExecutionContext execContext = execContextBuilder.build();
		return awsClient.requestExecutionBuilder().executionContext(execContext).originalRequest(sdkRequest).errorResponseHandler(errorHandler).request(httpRequest).execute(responseHandler);
	}

	public static class Builder {
		AWSSignerHttpClient client = new AWSSignerHttpClient();

		public AWSSignerHttpClient build() {
			if (client.awsCredentialsProvider == null) {
				DefaultCredentialsProvider provider = DefaultCredentialsProvider.create();
				client.awsCredentialsProvider = provider;
			}
			if (client.sdkClient == null) {
				client.sdkClient = new DefaultSdkHttpClientBuilder().buildWithDefaults(AttributeMap.empty());
			}

			if (client.region == null) {
				client.region = new DefaultAwsRegionProviderChain().getRegion();
			}
			client.signer = Aws4Signer.create();
			// signer.setRegionName(client.region.value());
			// signer.setServiceName(client.serviceName);
			// client.signingProvider = StaticSignerProvider.create(signer);
			SdkClientConfiguration clientConfiguration = SdkClientConfiguration.builder().option(SdkClientOption.ADDITIONAL_HTTP_HEADERS, new LinkedHashMap<>()).option(SdkClientOption.CRC32_FROM_COMPRESSED_DATA_ENABLED, true).option(SdkClientOption.SYNC_HTTP_CLIENT, client.sdkClient)
					.option(SdkClientOption.RETRY_POLICY, RetryPolicy.none()).build();
			client.awsClient = new AmazonSyncHttpClient(clientConfiguration);
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

		public Builder awsCredentials(AwsCredentialsProvider awsCredentialsProvider) {
			client.awsCredentialsProvider = awsCredentialsProvider;
			return this;
		}
	}

	public static class ServiceSDKRequest extends SdkRequest {

		@Override
		public Optional<? extends RequestOverrideConfiguration> overrideConfiguration() {
			return Optional.empty();
		}

		@Override
		public Builder toBuilder() {
			return new Builder() {

				@Override
				public RequestOverrideConfiguration overrideConfiguration() {
					return null;
				}

				@Override
				public SdkRequest build() {
					return new ServiceSDKRequest();
				}

			};
		}

		@Override
		public List<SdkField<?>> sdkFields() {
			return Collections.unmodifiableList(Collections.emptyList());
		}

	}

	public static class ErrorHandler implements HttpResponseHandler<SdkException> {

		@Override
		public SdkException handle(SdkHttpFullResponse response, ExecutionAttributes executionAttributes) throws Exception {
			String responseMsg = response.content().isPresent() ? IoUtils.toUtf8String(response.content().get()) : "";
			return SdkException.builder().message(String.format("%d: %s", response.statusCode(), responseMsg)).build();
		}

	}

	public static class JsonHandler<T extends JsonStructure> implements HttpResponseHandler<T> {

		@Override
		public T handle(SdkHttpFullResponse response, ExecutionAttributes executionAttributes) throws Exception {
			return response.content().isPresent() ? (T) Json.createReader(response.content().get()).read() : null;
		}

	}
}
