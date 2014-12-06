/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere
 */

package org.nuxeo.aws.elastictranscoder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotification;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotification.JobState;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotificationHandler;
import org.nuxeo.aws.elastictranscoder.notification.SqsQueueNotificationWorker;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.picture.api.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.Job;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.elastictranscoder.model.ReadJobRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.google.common.io.Files;

/**
 * First draft uses polling to read job's status, this is good enough for first
 * POC, but be warned that AWS doc explicitly states that:
 * <p>
 * <quote> If you poll the Elastic Transcoder's ReadJob API to track job status,
 * you need to continuously call ReadJob on every submitted job. This
 * methodology cannot scale as the number of transcode jobs increases. To solve
 * this problem, Elastic Transcoder can publish notifications to Amazon SNS
 * which provides an event-driven mechanism for tracking job status. </quote>
 * <p>
 * Also, we don't dispatch videos in sub folders (for example
 * /userX/videos/file1, fiele2, ...
 *
 *<p>
 * <b>IMPORTANT</b>
 * By default, files created on S3 are deleted after the transcoding. If you want
 * to keep them on S3, call setDeleteAfterTranscoding(false) <i>before</i>
 * calling <code>transcode()</code>
 * @since 7.1
 */
/**
 * TODO Explain
 * <p>
 * Credentials in nuxeo.conf or in the AWS env. variables (nuxeo.conf first)
 * <p>
 * The code expect SNS and SQS to be configured, so we can get the AWS Elastic
 * Transcoder notifications
 * <p>
 * The code is inspired from the JobStatusNotificationsSample.java example from
 * AWS SDK, which states (2014-12):
 * <p>
 * <i> Note that this implementation will not scale to multiple machines because
 * the provided JobStatusNotificationHandler is looking for a specific job ID.
 * If there are multiple machines polling SQS for notifications, there is no
 * guarantee that a particular machine will receive a particular notification.
 * </i>
 * <p>
 * => You know it "will not scale to multiple machines"
 * <p>
 * "If the [output] bucket already contains a file that has the specified name, the output fails"
 * => We add a UID as prefix in this first implementation
 * 
 */
public class AWSElasticTranscoder {

    private static final Log log = LogFactory.getLog(AWSElasticTranscoder.class);

    // The keys in nuxeo.conf
    public static final String CONF_AWS_KEY_ACCESS = "aws.transcoder.key";

    public static final String CONF_AWS_KEY_SECRET = "aws.transcoder.secret";

    // These are the names of system variable keys, if used instead of
    // nuxeo.conf
    public static final String AWS_ENV_VAR_ACCESS_KEY = "AWS_ACCESS_KEY_ID";

    public static final String AWS_ENV_VAR_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";

    protected static String awsAccessKeyId;

    protected static String awsSecretAccessKey;

    protected static int awsKeysCheckedStatus = -1;

    protected boolean isRunning = true;

    protected Blob blob;

    protected File fileOfBlob;

    protected FileBlob transcodedBlob;

    protected String eTag;

    protected String presetId;

    protected String inputS3Bucket;

    protected String outputS3Bucket;

    protected String pipelineId;

    protected String sqsQueueURL;

    protected boolean deleteAfterTranscoding = true;

    protected String awsJobId;

    protected JobState jobEndState;

    protected String uniqueFilePrefix;

    protected String inputKey;

    protected String outputKey;

    protected AmazonS3 amazonS3;

    protected AmazonElasticTranscoder amazonElasticTranscoder;

    protected AmazonSQS amazonSQS;

    protected AWSCredentialsProvider awsCredentialsProvider;

    protected enum STEP {
        INIT(0), INPUT_FILE_SENT(2), TRANSCODING_DONE(2), OUTPUT_FILE_DOWNLOADED(
                3);

        private int step;

        private STEP(int inValue) {
            step = inValue;
        }

        protected int toInt() {
            return step;
        }

        protected boolean canDeleteInputFileOnS3() {
            return step >= INPUT_FILE_SENT.toInt();
        }

        protected boolean canDeleteOutputFileOnS3() {
            return step >= TRANSCODING_DONE.toInt();
        }
    }

    protected STEP step;

    /**
     * Constructor is strict an throws an error if a parameter looks invalid
     * 
     * @param inBlob
     * @param inPresetId
     * @param inInputBucket
     * @param inOutputBucket
     * @param inPipelineId
     */
    public AWSElasticTranscoder(Blob inBlob, String inPresetId,
            String inInputBucket, String inOutputBucket, String inPipelineId,
            String inSQSQueueURL) {

        loadAccessKeysFromConf();
        if (awsKeysCheckedStatus != 1) {
            throw new ClientException(
                    "AWS Access Key ID/Secret Access Key are missing or invalid. Are they correctly set-up in nuxeo.conf or as System variables?");
        }

        blob = inBlob;
        fileOfBlob = BlobHelper.getFileFromBlob(blob);
        if (fileOfBlob == null) {
            // TODO
            // . . . create a temp file instead of giving up . . .
            throw new RuntimeException("Cannot get a File from this blob");
        }

        if (StringUtils.isBlank(inPresetId)) {
            throw new RuntimeException("PresetId is blank");
        }
        if (StringUtils.isBlank(inPresetId)) {
            throw new RuntimeException("InputBucket is blank");
        }
        if (StringUtils.isBlank(inPresetId)) {
            throw new RuntimeException("utputBucket is blank");
        }
        if (StringUtils.isBlank(inPresetId)) {
            throw new RuntimeException("PipelineId is blank");
        }
        if (StringUtils.isBlank(inPresetId)) {
            throw new RuntimeException("QSQueueURL is blank");
        }

        presetId = inPresetId;
        inputS3Bucket = inInputBucket;
        outputS3Bucket = inOutputBucket;
        pipelineId = inPipelineId;
        sqsQueueURL = inSQSQueueURL;

        uniqueFilePrefix = java.util.UUID.randomUUID().toString().replace("-",
                "")
                + "-";
        buildInputKeyName();
        buildOutputKeyName();
        transcodedBlob = null;
        step = STEP.INIT;

        // Create the main AWS objects (S3, Elastic Transcoder, ...)
        awsCredentialsProvider = new SimpleAWSCredentialProvider(
                awsAccessKeyId, awsSecretAccessKey);
        amazonS3 = new AmazonS3Client(awsCredentialsProvider);
        amazonElasticTranscoder = new AmazonElasticTranscoderClient(
                awsCredentialsProvider);
        amazonSQS = new AmazonSQSClient(awsCredentialsProvider);

    }

    public void transcode() throws RuntimeException {

        isRunning = true;

        try {
            // Send the file to the s3 inputS3Bucket
            sendFileToInputBucket();
            step = STEP.INPUT_FILE_SENT;

            // Setup our notification worker.
            SqsQueueNotificationWorker sqsQueueNotificationWorker = new SqsQueueNotificationWorker(
                    amazonSQS, sqsQueueURL);
            Thread notificationThread = new Thread(sqsQueueNotificationWorker);
            notificationThread.start();

            // Create the job
            createElasticTranscoderJob();

            // Wait for the job we created to complete.
            // System.out.println("Waiting for job to complete: " + awsJobId);
            waitForCompletion(sqsQueueNotificationWorker);
            step = STEP.TRANSCODING_DONE;

            // Get the transcoded video
            if (jobEndState == JobState.ERROR) {
                // TODO Something else than just throw the error maybe?
                // At least, give more details
                throw new RuntimeException(
                        "An error occured while transcoding file " + inputKey);
            } else {
                getFileFromOutputBucket();
                step = STEP.OUTPUT_FILE_DOWNLOADED;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            cleanup();
        }

        isRunning = false;

    }

    public boolean done() {

        return !isRunning;
    }

    public FileBlob getTranscodedBlob() {

        return transcodedBlob;
    }

    protected void cleanup() {

        if (deleteAfterTranscoding) {
            removeFilesFromS3IgnoreError();
        }

        step = STEP.INIT;
    }

    /*
     * Here we are "ready" (fun to say that with such an empty method) to setup
     * the key (the file name + full path prefix) in the S3 bucket, so we could
     * put it in a folder/subfolder/etc.
     * 
     * Assume we have a valid blob in entry, default implementation: Just the
     * filename
     */
    protected void buildInputKeyName() {

        inputKey = uniqueFilePrefix + fileOfBlob.getName();
    }

    /*
     * See getInputKeyName() about the destination file.
     * 
     * But also, here we should change the name and setup the extension
     * according to the transcoding for example
     * 
     * Assume we have a valid blob in entry, default implementation: Just the
     * filename
     */
    protected void buildOutputKeyName() {

        String key = uniqueFilePrefix + fileOfBlob.getName();
        // String ext = Files.getFileExtension(key);
        // key = key.replace("." + ext, "");

        outputKey = key;
    }

    protected void sendFileToInputBucket() {

        try {

            // System.out.println("Uploading a new object to S3 from a file\n");
            PutObjectResult result = amazonS3.putObject(new PutObjectRequest(
                    inputS3Bucket, inputKey, fileOfBlob));
            eTag = result.getETag();

        } catch (AmazonServiceException ase) {
            String message = "Caught an AmazonServiceException, which "
                    + "means your request made it "
                    + "to Amazon S3, but was rejected with an error response"
                    + " for some reason.";
            message += "\nError Message:    " + ase.getMessage();
            message += "\nHTTP Status Code: " + ase.getStatusCode();
            message += "\nAWS Error Code:   " + ase.getErrorCode();
            message += "\nError Type:       " + ase.getErrorType();
            message += "\nRequest ID:       " + ase.getRequestId();

            throw new RuntimeException(message);

        } catch (AmazonClientException ace) {
            String message = "Caught an AmazonClientException, which "
                    + "means the client encountered "
                    + "an internal error while trying to "
                    + "communicate with S3, "
                    + "such as not being able to access the network.";
            message += "\nError Message: " + ace.getMessage();
            throw new RuntimeException(message);
        }

    }

    protected void getFileFromOutputBucket() throws IOException {

        File tmp = File.createTempFile("NxAWSET-", "");
        tmp.deleteOnExit();
        Framework.trackFile(tmp, this);
        GetObjectRequest gor = new GetObjectRequest(outputS3Bucket, outputKey);
        ObjectMetadata metadata = amazonS3.getObject(gor, tmp);

        transcodedBlob = new FileBlob(tmp);
        transcodedBlob.setMimeType(metadata.getContentType());
        System.out.println("ZE FILE: ");
        System.out.println(metadata.getContentType());
    }

    protected void removeFilesFromS3IgnoreError() {

        if (step.canDeleteInputFileOnS3()) {
            try {
                amazonS3.deleteObject(inputS3Bucket, inputKey);
            } catch (Exception e) {
                log.error("Error when deleting file in the S3 input bucket", e);
            }
        }

        if (step.canDeleteOutputFileOnS3()) {
            try {
                amazonS3.deleteObject(outputS3Bucket, outputKey);
            } catch (Exception e) {
                log.error("Error when deleting file in the S3 output bucket", e);
            }
        }
    }

    protected void createElasticTranscoderJob() {
        // (using code from the AWS code sample in
        // JobStatusNotificationsSample.java)

        // Setup the job input
        JobInput jobInput = new JobInput().withKey(inputKey);

        // Setup the job output using the provided input key to generate an
        // output key.
        List<CreateJobOutput> outputs = new ArrayList<CreateJobOutput>();
        CreateJobOutput output = new CreateJobOutput().withKey(outputKey);
        output.withPresetId(presetId);
        outputs.add(output);

        // Create a job on the specified pipeline and get the job ID
        CreateJobRequest createJobRequest = new CreateJobRequest();
        createJobRequest.withPipelineId(pipelineId);
        createJobRequest.withInput(jobInput);
        createJobRequest.withOutputs(outputs);

        CreateJobResult cjr = amazonElasticTranscoder.createJob(createJobRequest);
        Job job = cjr.getJob();
        awsJobId = job.getId();

    }

    /**
     * Waits for the specified job to complete by adding a handler to the SQS
     * notification worker that is polling for status updates. This method will
     * block until the specified job completes.
     * 
     * @param sqsQueueNotificationWorker
     * @throws InterruptedException
     */
    protected void waitForCompletion(SqsQueueNotificationWorker inWorker)
            throws InterruptedException {

        // Create a handler that will wait for this specific job to complete.
        JobStatusNotificationHandler handler = new JobStatusNotificationHandler() {

            @Override
            public void handle(JobStatusNotification jobStatusNotification) {
                if (jobStatusNotification.getJobId().equals(awsJobId)) {

                    System.out.println("========== <jobStatusNotification>");
                    System.out.println(jobStatusNotification);
                    System.out.println("========== </jobStatusNotification>");

                    if (jobStatusNotification.getState().isTerminalState()) {
                        jobEndState = jobStatusNotification.getState();
                        if (jobEndState == JobState.ERROR) {
                            log.error(jobStatusNotification);
                        }
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                }
            }
        };
        inWorker.addHandler(handler);

        // Wait for job to complete.
        synchronized (handler) {
            handler.wait();
        }

        // When job completes, shutdown the sqs notification worker.
        inWorker.shutdown();

    }

    protected synchronized void loadAccessKeysFromConf() {

        if (awsKeysCheckedStatus == -1) {
            awsAccessKeyId = Framework.getProperty(CONF_AWS_KEY_ACCESS);
            awsSecretAccessKey = Framework.getProperty(CONF_AWS_KEY_SECRET);

            // Fallback if the keys are not here
            if (StringUtils.isBlank(awsAccessKeyId)) {
                awsAccessKeyId = System.getenv(AWS_ENV_VAR_ACCESS_KEY);
            }
            if (StringUtils.isBlank(awsSecretAccessKey)) {
                awsSecretAccessKey = System.getenv(AWS_ENV_VAR_SECRET_KEY);
            }

            if (StringUtils.isBlank(awsAccessKeyId)
                    || StringUtils.isBlank(awsSecretAccessKey)) {
                awsKeysCheckedStatus = 0;
            } else {
                awsKeysCheckedStatus = 1;
            }
        }
    }

    protected boolean checkNoBlank(String... inValues) {

        for (String val : inValues) {
            if (StringUtils.isBlank(val)) {
                return false;
            }
        }

        return true;
    }

    public boolean detDeleteAfterTranscoding() {
        return deleteAfterTranscoding;
    }

    public void setDeleteAfterTranscoding(boolean inValue) {
        deleteAfterTranscoding = inValue;
    }

}
