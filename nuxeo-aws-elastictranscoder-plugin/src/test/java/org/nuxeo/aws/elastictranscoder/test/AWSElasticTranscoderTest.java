/**
 * 
 */

package org.nuxeo.aws.elastictranscoder.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoderConstants;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.video.service.VideoConversion;
import org.nuxeo.ecm.platform.video.service.VideoService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

/*
 * The container type for the output file. Valid values include fmp4, mp3, mp4, ogg, ts, and webm. The following restrictions apply:
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, CoreFeature.class })
@Deploy({ "nuxeo-aws-elastictranscoder", "org.nuxeo.ecm.platform.video.core",
        "org.nuxeo.ecm.platform.video.convert",
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.picture.convert",
        "org.nuxeo.ecm.platform.picture.api",
        "org.nuxeo.ecm.platform.commandline.executor" })
@LocalDeploy({ "nuxeo-aws-elastictranscoder-test:video-and-converter-test-contrib.xml" })
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
    // iPhone 5-4S, iPad 3-4G, iPad mini, Samsung Galaxy S2/S3/Tab 2
    protected static final String PRESET_iPHONE_5 = "1351620000001-100020";

    protected static final String PRESET_iPHONE_5_OUTPUT_SUFFIX = "-ip5.mp4";

    // COnverters used in the video-and-converter-test-contrib.xml contribution
    protected static final String VIDEO_CONVERT_WEB = "Elastic Transcoder: Web";

    // ET = Elastic Transcoder
    protected static final String CONVERTER_ET_WEB = "awsET_presetWeb";

    protected static final String VIDEO_CONVERT_IPHONE5 = "Elastic Transcoder: iPhone5";

    // ET = Elastic Transcoder
    protected static final String CONVERTER_ET_IPHONE5 = "awsET_presetIPhone5";

    protected static final String VIDEO_CONVERT_GENERIC_480p = "Elastic Transcoder: Generic 480p 4:3";

    protected static final String CONVERTER_ET_GENERIC_480p = "awsET_presetGeneric480p4-3";

    protected DocumentModel parentOfTestDocs;

    protected DocumentModel videoDoc;

    @Inject
    CoreSession coreSession;

    @Inject
    EventService eventService;

    protected void doLog(String what) {
        System.out.println(what);
    }

    // Not sure it's the best way to get the current method name, but at least
    // it works
    protected String getCurrentMethodName(RuntimeException e) {
        StackTraceElement currentElement = e.getStackTrace()[0];
        return currentElement.getMethodName();
    }

    /*
     * Create and save the documents at first level of parentOfTestDocs
     */
    protected DocumentModel createAndSaveDoc(String inType, String inTitle,
            File inFile) {

        DocumentModel doc;

        doc = coreSession.createDocumentModel(
                parentOfTestDocs.getPathAsString(), inTitle, inType);
        doc.setPropertyValue("dc:title", inTitle);
        if (inFile != null) {
            FileBlob fb = new FileBlob(inFile);
            doc.setPropertyValue("file:content", fb);
        }
        doc = coreSession.createDocument(doc);
        doc = coreSession.saveDocument(doc);
        coreSession.save();
        

        return doc;
    }

    @Before
    public void setup() throws Exception {

        File file = FileUtils.getResourceFileFromContext(TEST_CONF);
        FileInputStream fileInput = new FileInputStream(file);
        Properties props = new Properties();
        props.load(fileInput);
        fileInput.close();

        Properties systemProps = System.getProperties();
        systemProps.setProperty(
                AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS,
                props.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS));
        systemProps.setProperty(
                AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET,
                props.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET));

        parentOfTestDocs = coreSession.createDocumentModel("/", "test-docs",
                "Folder");
        parentOfTestDocs.setPropertyValue("dc:title", "test-docs");
        parentOfTestDocs = coreSession.createDocument(parentOfTestDocs);
        parentOfTestDocs = coreSession.saveDocument(parentOfTestDocs);

    }

    @After
    public void cleanup() {
        coreSession.removeDocument(parentOfTestDocs.getRef());
        coreSession.save();
    }

    @Ignore
    @Test
    public void testTranscodeVideoToIPhone5() throws Exception {

        doLog(getCurrentMethodName(new RuntimeException()) + "...");

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb,
                PRESET_iPHONE_5, INTPUTBUCKET, OUTPUTBUCKET, PIPELINE_ID,
                SQS_QUEUE_URL, PRESET_iPHONE_5_OUTPUT_SUFFIX);

        transcoder.transcode();

        FileBlob result = transcoder.getTranscodedBlob();
        assertNotNull(result);

        // Check it is a valid video
        // . . .
    }

    @Ignore
    @Test
    public void testVideoConversionContribution() throws Exception {

        doLog(getCurrentMethodName(new RuntimeException()) + "...");

        boolean hasETWebConverter = false;
        boolean hasETIPhoneConverter = false;
        boolean hasETGeneric480Converter = false;
        VideoService videoService = Framework.getLocalService(VideoService.class);
        Collection<VideoConversion> availableConversions = videoService.getAvailableVideoConversions();
        for (VideoConversion oneConv : availableConversions) {
            String name = oneConv.getName();
            if (VIDEO_CONVERT_WEB.equals(name)) {
                hasETWebConverter = true;
            }
            if (VIDEO_CONVERT_IPHONE5.equals(name)) {
                hasETIPhoneConverter = true;
            }
            if (VIDEO_CONVERT_GENERIC_480p.equals(name)) {
                hasETGeneric480Converter = true;
            }
            if (hasETWebConverter && hasETIPhoneConverter
                    && hasETGeneric480Converter) {
                break;
            }
        }
        assertTrue(
                "Was expecting "
                        + VIDEO_CONVERT_WEB
                        + " to be available. Check video-and-converter-test-contrib.xml",
                hasETWebConverter);
        assertTrue(
                "Was expecting "
                        + VIDEO_CONVERT_IPHONE5
                        + " to be available. Check video-and-converter-test-contrib.xml",
                hasETIPhoneConverter);
        assertTrue(
                "Was expecting "
                        + VIDEO_CONVERT_GENERIC_480p
                        + " to be available. Check video-and-converter-test-contrib.xml",
                hasETGeneric480Converter);

        // Now, use one of them. We checked by name, now we use the conversion
        // service, so the converter declared in
        // video-and-converter-test-contrib.xml
        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        ConversionService conversionService = Framework.getService(ConversionService.class);
        BlobHolder source = new SimpleBlobHolder(fb);
        BlobHolder result = conversionService.convert(CONVERTER_ET_WEB, source,
                null);
        Blob convertedBlob = result.getBlob();
        assertNotNull(convertedBlob);

        doLog(getCurrentMethodName(new RuntimeException()) + "...done");

    }

    /*
     * The video-and-converter-test-contrib.xml declares an automatic conversion
     * to
     */
    // @Ignore
    @Test
    public void testVideoConversionOnDocument() throws Exception {

        doLog(getCurrentMethodName(new RuntimeException()) + "...");

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        DocumentModel videoDoc = createAndSaveDoc("Video", f.getName(), f);
        assertNotNull(videoDoc);
        Blob b = (Blob) videoDoc.getPropertyValue("file:content");
        assertNotNull(b);
        assertEquals(f.getName(), b.getFilename());
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        eventService.waitForAsyncCompletion();
        
        CoreSession session = Framework.getService(RepositoryManager.class).getDefaultRepository().open();
        
        // Check we have our video
        videoDoc = session.getDocument(new IdRef(videoDoc.getId()));
        videoDoc.refresh();
        List<Map<String, Serializable>> transcodedVideos = (List<Map<String, Serializable>>) videoDoc.getPropertyValue("vid:transcodedVideos");
        if(transcodedVideos == null) {
            System.out.println("no transcodedVideos");
        } else {
            System.out.println("transcodedVideos: " + transcodedVideos.size());
        }
        
        for (Map<String, Serializable> prop : transcodedVideos) {
            System.out.println(prop.get("name"));
        }

    }

}
