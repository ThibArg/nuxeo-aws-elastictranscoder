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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotification;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotification.JobState;
import org.nuxeo.aws.elastictranscoder.notification.JobStatusNotificationHandler;
import org.nuxeo.aws.elastictranscoder.notification.SqsQueueNotificationWorker;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.picture.api.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.s3.AmazonS3;
import com.google.common.io.Files;

/**
 * <i>[Please, see the README file to understand implementation, and actually
 * the limitation of this first implementation (mainly: It does not scale)]</i>
 * <p>
 * This class handles the following:
 * <ul>
 * <li>Connect to AWS</li>
 * <li>Uploads the file to the input S3 bucket</li>
 * <li>Use Elastic Transcoder to transcode the file</li>
 * <li>Waits until the transcoding is done<br>
 * Important: This is done using Amazon SNS and SQS, which must be configured
 * and used with the transcoder pipeline</li>
 * <li>Download the file</li>
 * <li>Cleanup and delete the files created in S3<br>
 * <b>IMPORTANT</b>: This is the default behavior: the class deletes the files
 * created in the S3 buckets (input bucket and output bucket) after the
 * transcoding. If you want to keep them on S3, call
 * <code>setDeleteInputFileOnCleanup(false)</code> and/or
 * <code>setDeleteOutputFileOnCleanup(false)</code> <i>before</i> calling
 * <code>transcode()</code></li>
 * </ul>
 * <p>
 * <b>Setting up the AWS dependencies</b>
 * <p>
 * The class expects the following to be already set on AWS:
 * <ul>
 * <li>A valid S3 input bucket</li>
 * <li>A valid S3 output bucket (can be the same as the input)</li>
 * <li>An existing SNS topic for the notification</li>
 * <li>An existing SQS which has subscribed to the SNS topic</li>
 * <li>A pipeline already created, referencing the bucket(s) and the the
 * notification</li>
 * </ul>
 * 
 * <p>
 * Important information:
 * <ul>
 * <li>Key ID and secret key used for the connection are either in nuxeo.conf or
 * set as environment variables (see GenericAWSClient)</li>
 * <li>We don't dispatch videos in sub folders (for example /userX/videos/
 * file1, ... fileN)</li>
 * <li>The name of the file in the output bucket must be unique: "If the
 * [output] bucket already contains a file that has the specified name, the
 * output fails"<br/>
 * The class adds a UUID prefix to the file names.</li>
 * <li>The code expect SNS and SQS to be configured, so we can get the AWS
 * Elastic Transcoder notifications</li>
 * <li></li>
 * <li>The code is inspired from the JobStatusNotificationsSample.java example
 * from AWS SDK, which states (2014-12):<br>
 * <i>Note that this implementation will not scale to multiple machines because
 * the provided JobStatusNotificationHandler is looking for a specific job ID.
 * If there are multiple machines polling SQS for notifications, there is no
 * guarantee that a particular machine will receive a particular notification.
 * </i></li>
 * </ul>
 * 
 * @since 7.1
 */
public class AWSElasticTranscoder {

    private static final Log log = LogFactory.getLog(AWSElasticTranscoder.class);

    protected Blob blob;

    protected File fileOfBlob;

    protected FileBlob transcodedBlob;

    protected String presetId;

    protected String inputS3Bucket;

    protected String outputS3Bucket;

    protected String pipelineId;

    protected String sqsQueueURL;

    protected boolean deleteInputFileOnCleanup = true;

    protected boolean deleteOutputFileOnCleanup = true;

    protected String awsJobId;

    protected JobState jobEndState;

    protected String uniqueFilePrefix;

    protected String inputKey;

    protected String outputKey;
    
    protected String outputFileSuffix;

    protected GenericAWSClient genericAwsClient;

    protected AWSS3Handler s3Handler;

    /*
     * Handling the progression so at cleanup time we know what can be cleaned
     * up (avoid trying to delete a file on S3 if we know we never could not
     * send it)
     */
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

        protected boolean isRunning() {
            return step > INIT.toInt() && step < OUTPUT_FILE_DOWNLOADED.toInt();
        }
    }

    protected STEP step;

    /**
     * Constructor is strict and throws an error if a parameter looks invalid
     * 
     * @param inBlob
     * @param inPresetId
     * @param inInputBucket
     * @param inOutputBucket
     * @param inPipelineId
     * @throws IOException
     */
    public AWSElasticTranscoder(Blob inBlob, String inPresetId,
            String inInputBucket, String inOutputBucket, String inPipelineId,
            String inSQSQueueURL, String inOutputFileSuffix) throws IOException {

        genericAwsClient = new GenericAWSClient();
        s3Handler = new AWSS3Handler(genericAwsClient.getS3Client());

        blob = inBlob;
        fileOfBlob = BlobHelper.getFileFromBlob(blob);
        if (fileOfBlob == null) {
            // Create a temp file
            fileOfBlob = File.createTempFile("NxAWSET-",
                    Files.getFileExtension(blob.getFilename()));
            blob.transferTo(fileOfBlob);
            fileOfBlob.deleteOnExit();
            Framework.trackFile(fileOfBlob, this);
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
        outputFileSuffix = StringUtils.isBlank(inOutputFileSuffix) ? "" : inOutputFileSuffix;

        uniqueFilePrefix = java.util.UUID.randomUUID().toString().replace("-",
                "")
                + "-";
        buildInputKeyName();
        buildOutputKeyName();
        transcodedBlob = null;
        step = STEP.INIT;

    }

    public void transcode() throws RuntimeException {

        try {
            // Send the file to the s3 inputS3Bucket
            sendFileToInputBucket();
            step = STEP.INPUT_FILE_SENT;

            // Setup our notification worker.
            SqsQueueNotificationWorker sqsQueueNotificationWorker = new SqsQueueNotificationWorker(
                    genericAwsClient.getSQSClient(), sqsQueueURL);
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

    }

    public boolean done() {

        return !step.isRunning();
    }

    public FileBlob getTranscodedBlob() {

        return transcodedBlob;
    }

    protected void cleanup() {

        if (deleteInputFileOnCleanup) {
            deleteInputFileOnS3IfNeeded(true);
        }

        if (deleteOutputFileOnCleanup) {
            deleteOutputFileOnS3IfNeeded(true);
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

        String key = uniqueFilePrefix + fileOfBlob.getName() + outputFileSuffix;

        outputKey = key;
    }

    protected String getOutputFileName() {
        String fileName = outputKey.replace(uniqueFilePrefix, "");

        return fileName;
    }

    protected void sendFileToInputBucket() throws RuntimeException {

        s3Handler.setBucket(inputS3Bucket);
        s3Handler.sendFile(inputKey, fileOfBlob);

    }

    protected void getFileFromOutputBucket() throws IOException,
            RuntimeException {

        s3Handler.setBucket(outputS3Bucket);
        transcodedBlob = s3Handler.downloadFile(outputKey, getOutputFileName());
    }

    protected void deleteInputFileOnS3IfNeeded(boolean inIgnoreError) {

        if (step.canDeleteInputFileOnS3()) {
            try {
                s3Handler.setBucket(inputS3Bucket);
                s3Handler.deleteFile(inputKey);
            } catch (Exception e) {
                if (inIgnoreError) {
                    log.error("Error when deleting file " + inputKey
                            + " in the S3 input bucket", e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    protected void deleteOutputFileOnS3IfNeeded(boolean inIgnoreError) {

        if (step.canDeleteOutputFileOnS3()) {
            try {
                s3Handler.setBucket(outputS3Bucket);
                s3Handler.deleteFile(outputKey);
            } catch (Exception e) {
                if (inIgnoreError) {
                    log.error("Error when deleting file " + outputKey
                            + " in the S3 output bucket", e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    protected void createElasticTranscoderJob() {
        // (using code from the AWS code sample in
        // JobStatusNotificationsSample.java)

        // Setup the job input
        JobInput jobInput = new JobInput().withKey(inputKey);

        // We create just one job here. Look at the code sample in
        // JobStatusNotificationsSample.java to see how to create several jobs
        // in one call.
        CreateJobOutput output = new CreateJobOutput().withKey(outputKey);
        output.withPresetId(presetId);
        // Create a job on the specified pipeline and get the job ID
        CreateJobRequest createJobRequest = new CreateJobRequest();
        createJobRequest.withPipelineId(pipelineId);
        createJobRequest.withInput(jobInput);
        createJobRequest.withOutput(output);

        CreateJobResult cjr = genericAwsClient.getElasticTranscoder().createJob(
                createJobRequest);
        awsJobId = cjr.getJob().getId();

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

                    /*
                     * System.out.println("========== <jobStatusNotification>");
                     * System.out.println(jobStatusNotification);
                     * System.out.println
                     * ("========== </jobStatusNotification>");
                     */

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

    public boolean getDeleteInputFileOnCleanup() {
        return deleteInputFileOnCleanup;
    }

    public void setDeleteInputFileOnCleanup(boolean inValue) {
        deleteInputFileOnCleanup = inValue;
    }

    public boolean getDeleteOutputFileOnCleanup() {
        return deleteOutputFileOnCleanup;
    }

    public void setDeleteOutputFileOnCleanup(boolean inValue) {
        deleteOutputFileOnCleanup = inValue;
    }

}
