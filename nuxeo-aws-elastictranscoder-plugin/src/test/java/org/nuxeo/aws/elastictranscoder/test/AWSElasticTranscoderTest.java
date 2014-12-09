/**
 * 
 */

package org.nuxeo.aws.elastictranscoder.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoderConstants;
import org.nuxeo.aws.elastictranscoder.GenericAWSClient;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

/*
 * The container type for the output file. Valid values include fmp4, mp3, mp4, ogg, ts, and webm. The following restrictions apply:
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy({ "nuxeo-aws-elastictranscoder" })
public class AWSElasticTranscoderTest {

    protected static final String VIDEO_MP4 = "files/a.mp4";

    protected static final String TEST_CONF = "aws-test.conf";

    // These buckets are created with the AWS nuxeo presales credentials
    // The keys are set in the nuxeo server configuration file.

    protected static String INTPUTBUCKET = "nuxeo-transcoding-input";

    protected static String OUTPUTBUCKET = "nuxeo-transcoding-output";

    protected static String PIPELINE_NAME = "nuxeo-transcoding-pipeline";

    protected static String PIPELINE_ID = "1417822775841-udlnwk";

    protected static String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/311032021612/nuxeo-transcoding-queue";

    // Presets available in US Region East:
    // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/system-presets.html
    // Web: Facebook, SmugMug, Vimeo, YouTube
    protected static final String PRESET_WEB = "1351620000001-100070";

    protected static final String PRESET_WEB_OUTPUT_SUFFIX = "-web.mp4";

    // iPhone 5, iPhone 4S, iPad 4G and 3G, iPad mini, Samsung Galaxy S2/S3/Tab
    // 2
    protected static final String PRESET_iPHONE_5 = "1351620000001-100020";

    protected static final String PRESET_iPHONE_5_OUTPUT_SUFFIX = "-ip5.mp4";

    @Inject
    CoreSession coreSession;

    @Before
    public void setup() throws Exception {

        File file = FileUtils.getResourceFileFromContext(TEST_CONF);
        FileInputStream fileInput = new FileInputStream(file);
        Properties props = new Properties();
        props.load(fileInput);
        fileInput.close();

        Properties systemProps = System.getProperties();
        systemProps.setProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS,
                props.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS));
        systemProps.setProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET,
                props.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET));

    }

    @After
    public void cleanup() {

    }

    @Test
    public void testTranscodeVideoToIPhone5() throws Exception {

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb,
                PRESET_iPHONE_5, INTPUTBUCKET, OUTPUTBUCKET, PIPELINE_ID,
                SQS_QUEUE_URL, PRESET_iPHONE_5_OUTPUT_SUFFIX);

        transcoder.transcode();

        FileBlob result = transcoder.getTranscodedBlob();
        assertNotNull(result);

        // Check it is a valid iPhone... video
        // . . .
    }

    @Ignore
    @Test
    public void testTranscodeVideoToWeb() throws Exception {
        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb,
                PRESET_WEB, INTPUTBUCKET, OUTPUTBUCKET, PIPELINE_ID,
                SQS_QUEUE_URL, PRESET_WEB_OUTPUT_SUFFIX);

        transcoder.transcode();

        FileBlob result = transcoder.getTranscodedBlob();
        assertNotNull(result);

        // Check it is a valid "web" video...
        // . . .
    }

}
