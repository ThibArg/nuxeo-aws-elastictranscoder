/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.aws.elastictranscoder.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.core.convert.service.ConversionServiceImpl;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.video.VideoHelper;
import org.nuxeo.ecm.platform.video.VideoInfo;
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
 * Check the README and SimpleFeatureCustom to setup your access to AWS for the test.
 * <p>
 * Basically: Have a aws-test.conf fil in test/resources and fill it with the corrcet keys/values
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SimpleFeatureCustom.class })
@Deploy({ "nuxeo-aws-elastictranscoder", "org.nuxeo.ecm.platform.video.core",
        "org.nuxeo.ecm.platform.video.convert",
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.picture.convert",
        "org.nuxeo.ecm.platform.picture.api",
        "org.nuxeo.ecm.platform.commandline.executor" })
@LocalDeploy({
        "nuxeo-aws-elastictranscoder-test:video-and-converter-test-contrib.xml",
        "nuxeo-aws-elastictranscoder-test:disabled-listeners-contrib.xml" })
public class AWSElasticTranscoderTest {

    protected static final String VIDEO_MP4 = "files/a.mp4";

    protected static final String TEST_CONF = "aws-test.conf";

    public static final String AUTOMATIC_CONVERSION_NAME = "Elastic Transcoder: Generic 480p 4:3";

    protected static String inputBucket;

    protected static String outputBucket;

    protected static String pipelineName;

    protected static String pipelineId;

    protected static String sqsQueueUrl;

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

    protected static boolean hasSetupInfo = false;

    @Inject
    CoreSession coreSession;

    @Inject
    EventService eventService;

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

    protected boolean atLeastOneBlank(String... list) {

        for (String str : list) {
            if (StringUtils.isBlank(str)) {
                return true;
            }
        }

        return false;
    }

    @Before
    public void setup() throws Exception {

        hasSetupInfo = false;

        if (SimpleFeatureCustom.hasLocalTestConfiguration()) {
            // Sanity check
            String awsKey = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_KEY);
            String awsSecret = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_SECRET);
            inputBucket = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_INPUT_BUCKET);
            outputBucket = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_OUTPUT_BUCKET);
            pipelineName = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_PIPE_LINE);
            pipelineId = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_PIPELINE_ID);
            sqsQueueUrl = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_KEY_AWS_SQS_QUEUE_URL);

            boolean hasABlank = atLeastOneBlank(awsKey, awsSecret, inputBucket,
                    outputBucket, pipelineName, pipelineId, sqsQueueUrl);

            assertFalse(hasABlank);
            hasSetupInfo = true;
        }

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

    // @Ignore
    @Test
    public void testTranscodeVideoToIPhone5() throws Exception {

        Assume.assumeTrue(
                "Ignoring test: Missing configuration parameters. Check your aws-test.conf file",
                hasSetupInfo);

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
        FileBlob fb = new FileBlob(f);

        System.out.println("Transcoding on AWS started...");

        AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb,
                PRESET_iPHONE_5, inputBucket, outputBucket, pipelineId,
                sqsQueueUrl, PRESET_iPHONE_5_OUTPUT_SUFFIX);

        transcoder.transcode();

        System.out.println("...Transcoding on AWS Done");

        Blob result = transcoder.getTranscodedBlob();
        assertNotNull(result);
        assertTrue(result.getFilename().endsWith(PRESET_iPHONE_5_OUTPUT_SUFFIX));

        // Check we have a valid video
        VideoInfo vi = null;
        try {
            vi = VideoHelper.getVideoInfo(result);
        } catch (Exception e) {
            vi = null;
        }
        assertNotNull(vi);

    }

    // @Ignore
    @Test
    public void testVideoConversionContribution() throws Exception {

        Assume.assumeTrue(
                "Missing configuration parameters. Check your aws-test.conf file",
                hasSetupInfo);

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

        System.out.println("Transcoding on AWS started...");

        BlobHolder result = conversionService.convert(CONVERTER_ET_WEB, source,
                null);

        System.out.println("...Transcoding on AWS Done");

        // Get info from the converter, to check we have a coherent result
        ConverterDescriptor desc = ConversionServiceImpl.getConverterDescriptor(CONVERTER_ET_WEB);
        String expectedMimeType = desc.getDestinationMimeType();
        // Specific to our converter
        String expectedFileSuffix = desc.getParameters().get("outputFileSuffix");
        // desc.get

        Blob convertedBlob = result.getBlob();
        assertNotNull(convertedBlob);
        assertTrue(convertedBlob.getLength() > 0);
        assertTrue(convertedBlob.getFilename().endsWith(expectedFileSuffix));
        assertEquals(expectedMimeType.toLowerCase(),
                convertedBlob.getMimeType().toLowerCase());

        // Check we have a valid video
        VideoInfo vi = null;
        try {
            vi = VideoHelper.getVideoInfo(convertedBlob);
        } catch (Exception e) {
            vi = null;
        }
        assertNotNull(vi);

    }

    /*
     * The video-and-converter-test-contrib.xml declares _one_ automatic
     * conversion with name "Elastic Transcoder: Generic 480p 4:3"
     */
    // @Ignore
    @Test
    public void testVideoConversionOnDocument() throws Exception {

        Assume.assumeTrue(
                "Missing configuration parameters. Check your aws-test.conf file",
                hasSetupInfo);

        File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);

        System.out.println("Transcoding on AWS started...");

        DocumentModel videoDoc = createAndSaveDoc("Video", f.getName(), f);
        assertNotNull(videoDoc);
        Blob b = (Blob) videoDoc.getPropertyValue("file:content");
        assertNotNull(b);
        assertEquals(f.getName(), b.getFilename());
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        eventService.waitForAsyncCompletion();

        System.out.println("...Transcoding on AWS Done");

        // Check we have our video
        videoDoc = coreSession.getDocument(new IdRef(videoDoc.getId()));
        videoDoc.refresh();
        List<Map<String, Serializable>> transcodedVideos = (List<Map<String, Serializable>>) videoDoc.getPropertyValue("vid:transcodedVideos");
        assertNotNull(transcodedVideos);
        assertTrue(transcodedVideos.size() > 0);

        boolean gotMine = false;
        for (Map<String, Serializable> prop : transcodedVideos) {
            if (prop.get("name").equals(AUTOMATIC_CONVERSION_NAME)) {
                gotMine = true;
                break;
            }
        }
        assertTrue(gotMine);

    }

}
