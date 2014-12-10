nuxeo-aws-elastictranscoder
=====

The `nuxeo-aws-elastictranscoder` allows to use (Amazon Elastic Transcoder)[http://aws.amazon.com/elastictranscoder/] to transcode any video.

In order to use the plug-in, (once it is installed), you need:

1. To setup the AWS environment (S3 bucket(s), Pipeline, ...)
2. Setup you AWS authentication keys server-side, so the plug-in can connect to AWS
3. Add an XML contribution to your nuxeo project, top automatically and/or manually transcode videos


### AWS Environment
The plug-in requires the following elements:
* An input S3 bucket (where nuxeo will upload the video to be transcoded)
* An output S3 bucket (where AWS Elastic Transcoder will save the transcoded video)
* A pipeline ID (used to push the video transcoding jobs)
* A preset, letting the elastic Transcoder know what destination format you need
** There are predefined System presets: http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/system-presets.html
** But you can build your own custom preset
* And a SQS URL, so the plugin detects when a video has been transcoded

Assuming you already have an AWS account, you can follow the instructions at (this page)[http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html], these topics:
* Creating Amazon S3 input and output buckets
* Creating an Amazon SNS topic to receive job status notifications
* Creating an Amazon SQS queue to poll for job status notifications
* Subscribing your Amazon SQS queue to your Amazon SNS topic
* Creating an Elastic Transcoder pipeline

Once these elements are available, using the plug-in is quite easy. Notice that the plug-in is not limited to one S3 input bucket, one pipeline, etc. It allows to use different elements. This can be useful if you need to have, for example, a _normal_ priority transcoding set, and a _high availability_ one which would be using big AWS environments (fast i/o, fast drives, ...)

### Your Authentication Keys
To connect to your AWS environment, the plug-in (actually, the underlying AWS SDK) needs the Key ID and Secret Key provide by AWS. These keys must be available server side and can be installed:
* In the `nuxeo.conf` file using the following parameters: `aws.transcoder.key` and `aws.transcoder.secret`. For example:
```
aws.transcoder.key=1234567890ABCDEFGHIJ
aws.transcoder.secret=AbCdEfGh1234567890iJkLmNoPqR+sTuVwXYZ012 
```
* Or in Environment Variables, under the AWS key names


### WARNING
This plug-in, even if it works very well, will not scale at large, it will require some adaptation if you need to transcode a lot of videos in a concurrent way.


_Work in progress_

Note/Reminder: To test with your key/secret AWS key, put then in src/test/resources/aws-test.conf

### About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com) and packaged applications for Document Management, Digital Asset Management and Case Management. Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.
