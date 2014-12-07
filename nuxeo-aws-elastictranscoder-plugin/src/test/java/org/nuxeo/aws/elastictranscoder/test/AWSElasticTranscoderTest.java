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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.aws.elastictranscoder.GenericAWSClient;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

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
    // https://console.aws.amazon.com/elastictranscoder/home?region=us-east-1#presets:
    // Web: Facebook, SmugMug, Vimeo, YouTube
    protected static final String PRESET_WEB = "1351620000001-100070";

    // iPhone 5, iPhone 4S, iPad 4G and 3G, iPad mini, Samsung Galaxy S2/S3/Tab
    // 2
    protected static final String PRESET_iPHONE_5 = "1351620000001-100020";

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
        systemProps.setProperty(GenericAWSClient.CONF_AWS_KEY_ACCESS,
                props.getProperty(GenericAWSClient.CONF_AWS_KEY_ACCESS));
        systemProps.setProperty(GenericAWSClient.CONF_AWS_KEY_SECRET,
                props.getProperty(GenericAWSClient.CONF_AWS_KEY_SECRET));

    }

    @After
    public void cleanup() {

    }

    @Test
    public void testTranscodeVideoToIPhone5() throws Exception {

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb,
                PRESET_WEB/*PRESET_iPHONE_5*/, INTPUTBUCKET, OUTPUTBUCKET, PIPELINE_ID,
                SQS_QUEUE_URL);

        transcoder.transcode();

        FileBlob result = transcoder.getTranscodedBlob();
        assertNotNull(result);
        File tmp = new File("/Users/thibaud/Desktop/coucou");
        result.transferTo(tmp);

        // Check it is a valid
        // . . .
    }

    @Test
    public void testConverter() throws Exception {
        // Here we test a VideoConversionConverter

        // create a Video document...

        // add the binary...

        // save...

        // Wait...

        // Check the transcoded video(s) is/are available
    }

}
