Inuxeo-aws-elastictranscoder
=====

The `nuxeo-aws-elastictranscoder` allows to use [Amazon Elastic Transcoder](http://aws.amazon.com/elastictranscoder/) to transcode any video.

In order to use the plug-in, (once it is installed), you need:

1. To setup your AWS environment (S3 bucket(s), Pipeline, ...)
2. Setup your AWS authentication keys server-side, so the plug-in can connect to AWS
3. Add an XML contribution to your nuxeo project, to automatically and/or manually transcode videos

Also, please read the [Dependencies](#dependencies) and the [Warning](#warning) parts of this documentation

### AWS Environment
The plug-in requires the following elements:
* An input S3 bucket (where nuxeo will upload the video to be transcoded)
* An output S3 bucket (where AWS Elastic Transcoder will save the transcoded video)
* A pipeline ID (used to push the video transcoding jobs)
* A preset, letting the elastic Transcoder know what destination format you need
  * There are [predefined System presets](http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/system-presets.html)
  * You can build your own custom preset
* And a SQS URL, so the plugin detects when a video has been transcoded

Assuming you already have an AWS account, you can follow the instructions at [this page](http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html). See these topics:
* Creating Amazon S3 input and output buckets
* Creating an Amazon SNS topic to receive job status notifications
* Creating an Amazon SQS queue to poll for job status notifications
* Subscribing your Amazon SQS queue to your Amazon SNS topic
* Creating an Elastic Transcoder pipeline

Once these elements are available, using the plug-in is quite easy. Notice that the plug-in is not limited to one S3 input bucket, one pipeline, etc. It allows to use different elements. This can be useful if you need to have, for example, a _normal_ priority transcoding set, and a _high availability_ one which would be using big AWS environments (fast i/o, fast drives, ...)

### Authentication Keys
To connect to your AWS environment, the plug-in (actually, the underlying AWS SDK) needs the Key ID and Secret Key provided by AWS. These keys must be available server side and can be installed:
* In the `nuxeo.conf` file using the following parameters: `aws.transcoder.key` and `aws.transcoder.secret`. For example:
```
aws.transcoder.key=1234567890ABCDEFGHIJ
aws.transcoder.secret=AbCdEFgh1234567890iJkLmNoPqR+sTuVwXYZ012 
```
* Or in Environment Variables, under the AWS official key names, `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`.

### Transcoding Videos
To transcode videos, you must add XML contributions to your project. You must contribute:
* One or more `converter`, where you specify the misc. parameters (s3 buckets, pipeline id, ...)
* One or more `videoConversion`
  * A `videoConversion` has a unique name and references a `converter`
  * Each `videoConversion` will be available by default in the UI for a `Video` document
* Optionally, some `automaticVideoConversion`
  * An `automaticVideoConversion` has a unique name and references a `videoConversion`
  * Nuxeo will automatically start the transcoding in an asynchronous work

#### The `converter` contribution(s)
It must have a unique `name` and declare the `parameters` used by the plug-in. For example:
```
<extension target="org.nuxeo.ecm.core.convert.service.ConversionServiceImpl"
           point="converter">

  <converter name="awsET_presetGeneric480p4-3"
    class="org.nuxeo.aws.elastictranscoder.connverters.AWSElasticTranscoderConverter">
    <sourceMimeType>video/*</sourceMimeType>
    <destinationMimeType>video/mp4</destinationMimeType>
    <parameters>
      <parameter name="inputBucket">nuxeo-transcoding-input</parameter>
      <parameter name="outputBucket">nuxeo-transcoding-output</parameter>
      <parameter name="pipelineId">1417822775841-udlnwk</parameter>
      <parameter name="presetId">1351620000001-000030</parameter>
      <parameter name="sqsQueueUrl">https://sqs.us-east-1.amazonaws.com/311032021612/nuxeo-transcoding-queue
      </parameter>
      <parameter name="deleteInputFileWhenDone">true</parameter>
      <parameter name="deleteOutputFileWhenDone">true</parameter>
      <parameter name="outputFileSuffix”>-480.mp4</parameter>
    </parameters>
  </converter>
  
</extension>
```

Some important details:
* You _must_ use the `class="org.nuxeo.aws.elastictranscoder.connverters.AWSElasticTranscoderConverter"` attribute
* `pipelineId` is the ID of the pipeline, not its name
* The same goes for the `presetID`. In this example, we are using the “Generic 490p 4:3” preset, whose ID is `1351620000001-000030`
* `deleteInputFileWhenDone` and `deleteOutputFileWhenDone`:
  * **By default**, if not specified in the extension, the values are `true`, meaning **the files are always deleted from their S3 bucket after the transcoding is done** and the result video has been downloaded to the server.
  * If you want/need to keep the files, set these parameters to `false`

#### The `videoConversion` contribution(s)
It must have a unique `name` and a reference to an existing `converter`. For example, here is a contribution referencing the previous `converter`:
```
<extension target="org.nuxeo.ecm.platform.video.service.VideoService"
    point="videoConversions">

    <videoConversion name="Elastic Transcoder: Generic 480p 4:3" converter="awsET_presetGeneric480p4-3" />
</extension>
```
#### The `automaticVideoConversion` contribution(s)
It must references the `name` of a `videoConversion`. For example:
```
<extension target="org.nuxeo.ecm.platform.video.service.VideoService"
    point="automaticVideoConversions">

  <automaticVideoConversion name="Elastic Transcoder: Generic 480p 4:3" order="0" />

</extension>
```

#### All Together
In the following example, we declare 3 converters, with one of them being automatic. This XML could be used as is in your Studio project.
```
<!-- Declare some videoConversion -->
<extension target="org.nuxeo.ecm.platform.video.service.VideoService"
           point="videoConversions">
  <videoConversion name="Label 1" converter="myConverter1" />
  <videoConversion name="Label 2" converter="myConverter2" />
  <videoConversion name="Label 3" converter="myConverter3" />
</extension>

<!-- Declare one automatic conversion, and make sure the other one are not enabled -->
<extension target="org.nuxeo.ecm.platform.video.service.VideoService"
           point="automaticVideoConversions">
  <automaticVideoConversion name="Label 1" order="0" />
  <automaticVideoConversion name="Label 2" order="0" enabled="false" />
  <automaticVideoConversion name="Label 3" order="0" enabled="false" />
</extension>

<!-- The converters used by the videoConversion elements -->
<!-- We skip the declarations of all the parameters: Do not copy-paste as is-->
<extension target="org.nuxeo.ecm.core.convert.service.ConversionServiceImpl"
           point="converter">
  <converter name="myConverter1"
    class="org.nuxeo.aws.elastictranscoder.connverters.AWSElasticTranscoderConverter">
    <parameters>
      . . . here your parameters: buckets, pipeline, preset, ...
    </parameters>
  </converter>
  <converter name="myConverter2"
    class="org.nuxeo.aws.elastictranscoder.connverters.AWSElasticTranscoderConverter">
    <parameters>
      . . . here your parameters: buckets, pipeline, preset, ...
    </parameters>
  </converter>
  <converter name="myConverter3"
    class="org.nuxeo.aws.elastictranscoder.connverters.AWSElasticTranscoderConverter">
    <parameters>
      . . . here your parameters: buckets, pipeline, preset, ...
    </parameters>
  </converter>
</extension>
```

### Dependencies
The plug-ins depends on the AWS SDK version 1.9.9, which itself depends on version 2.2 (min.) of `joda-time` and version 2.3.2 (min.) of `fasterxml-jackson`. As of today-right-now (2014-12-10), nuxeo uses lower versions, and the process of upgrading these dependencies inside nuxeo has started. Which means that, in order for the plug-in to work properly you can either:
* Wait for version 7.1 to be release (January 2014)
* Or manually replace the corresponding .jar files in the `lib` folder of your `nxserver` folder (`x.y.z` is the version you install):
  * `jackson-annotations-x.y.z`
  * `jackson-core-x.y.z`
  * `jackson-databind-x.y.z`
  * `joda-time-x.y.z`

(think about removing the older one to avoid conflicts)

### WARNING
This plug-in, even if it works very well, will not scale at large, it will require some adaptation if you need to transcode a lot of videos in a concurrent way.


### Install-Build

#### Install

You can use the Marketplace package located in the "Releases" tab of this GitHup repository. It is a regular package, you ca install it either from the UI (Admin Center > Update Center > Local packages) or from the command line if you prefer (`/nuxeoctl mp-install /path/to/nuxeo-aws-elastictranscoder-mp-7.1-SNAPSHOT.zip`).

#### Build

Assuming `maven` version 3.2 minimum is installed, you can just
```
cd /path/to/nuxeo-aws-elastictranscoder
mvn clean install -DskipTests=true
```

The Marketplace Package is now in `/path/to/nuxeo-aws-elastictranscoder/nuxeo-aws-elastictranscoder-mp/target/nuxeo-aws-elastictranscoder-mp.x.y.z-SNAPSHOT.zip`

If you want to run the tests, you need to setup the test environement so JUnit can find the AWS keys: Put then in a text file at src/test/resources/aws-test.conf (this file is ignored by git)

### About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com) and packaged applications for Document Management, Digital Asset Management and Case Management. Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.
