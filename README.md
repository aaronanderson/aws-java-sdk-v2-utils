# aws-java-sdk-v2-utils

A few Java utility classes that extends the capabilities of the new aws-java-sdk-v2 library to handle use cases that are not supported by the new library yet.

## S3Presigner
Presigning S3 download URLS is common requirement for sharing download links to S3 content.

 
```
AwsCredentialsProviderChain credentials = AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create());
S3Presigner s3Presigner = S3Presigner.builder().awsCredentials(credentials).build();
URI presigned = s3Presigner.presignS3UploadLink("some-bucket", "some-directory/some-file.txt");
```

## Elasticsearch
the V2 SDK provides an API to manage the instances but not query the Elasticsearch endpoint. Since Elasticsearch is secured with standard AWS security a signed request is required. This utility uses JSON-P to handle the JSON response data.  


```
AWSSignerHttpClient client = AWSSignerHttpClient.builder().serviceName("es").awsCredentials(AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create())).build();
SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).protocol("https").host("search-some-aws-elasticsearch-domain.us-west-1.es.amazonaws.com").build();
JsonObject obj = client.<JsonObject>execute(httpRequest);
```

##DynamoDB to JSON
A utility for converting DynamoDB AttributeValue structures into JSON-P objects and vice versa. Support for compressing JSON into AttributeValue byte storage is also available to avoid exceeding the 400k entry size limit. 

```
Map<String, AttributeValue> map = new HashMap<>();
map.put("string", AttributeValue.builder().s("test").build());
AttributeValue test = AttributeValue.builder().m(map).build();
JsonObject json = (JsonObject) DynamoDBUtil.toJson(test);

JsonObjectBuilder builder = Json.createObjectBuilder();
builder.add("string", "test");
Map<String, AttributeValue> attrValue = DynamoDBUtil.toAttribute(builder.build());
```

