/**
 * 
 */

package org.nuxeo.aws.elastictranscoder.test;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
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
	
	// iPhone 5, iPhone 4S, iPad 4G and 3G, iPad mini, Samsung Galaxy
			// S2/S3/Tab 2 => 1351620000001-100020
	protected static final String PRESET_iPHONE_5 = "1351620000001-100020";

	@Inject
	CoreSession coreSession;

	@Before
	public void setup() {

	}

	@After
	public void cleanup() {

	}

	@Test
	public void testTranscodeVideoToIPhone5() throws Exception {

		File f = FileUtils.getResourceFileFromContext(VIDEO_MP4);
		FileBlob fb = new FileBlob(f);
		
		AWSElasticTranscoder transcoder = new AWSElasticTranscoder(fb, PRESET_iPHONE_5);
		
		transcoder.transcode();
		
		// wait until it's done
		// do {...} while(!transcoder.done()) 
		
		File result = transcoder.getTranscodedFile();
		assertNotNull(result);
		
		// Check it is a valid iPhone5 video
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
