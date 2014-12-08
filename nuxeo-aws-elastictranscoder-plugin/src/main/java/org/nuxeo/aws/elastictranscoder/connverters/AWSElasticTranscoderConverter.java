/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
package org.nuxeo.aws.elastictranscoder.connverters;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.webengine.jaxrs.servlet.mapping.Path;

/**
 * The converter is contributed via an XML extension (see
 * video-and-converter-contrib.xml). The contribution has the following
 * parameters:
 * <ul>
 * <li><code>presetId</code>
 * <ul>
 * <li><b>Required</b></li>
 * <li>The AWS preset id</li>
 * </ul>
 * </li>
 * <li><code>outputFileSuffix</code>: Must contain at least the file extension
 * to use (to generate the <code>filename</code> field of the resuting blob)</li>
 * </ul>
 *
 * @since 7.1
 */
public class AWSElasticTranscoderConverter implements Converter {

    private static Log log = LogFactory.getLog(AWSElasticTranscoderConverter.class);

    protected String presetId;

    protected String outputFileSuffix;

    protected static String INTPUTBUCKET = "nuxeo-transcoding-input";

    protected static String OUTPUTBUCKET = "nuxeo-transcoding-output";

    protected static String PIPELINE_NAME = "nuxeo-transcoding-pipeline";

    protected static String PIPELINE_ID = "1417822775841-udlnwk";

    protected static String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/311032021612/nuxeo-transcoding-queue";

    @Override
    public void init(ConverterDescriptor descriptor) {

        Map<String, String> params = descriptor.getParameters();
        presetId = params.get("presetId");
        outputFileSuffix = params.get("outputFileSuffix");
    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder,
            Map<String, Serializable> parameters) throws ConversionException {

        List<Blob> results = new ArrayList<Blob>();
        
        String test = (String) parameters.get("presetId");
        String test2 = (String) parameters.get("outputFileSuffix");
        String test3 = (String) parameters.get("jkjkjk");

        Blob theBlob = blobHolder.getBlob();
        try {
            AWSElasticTranscoder transcoder = new AWSElasticTranscoder(theBlob,
                    presetId, INTPUTBUCKET, OUTPUTBUCKET, PIPELINE_ID,
                    SQS_QUEUE_URL);
            
            transcoder.transcode();

            FileBlob transcodedBlob = transcoder.getTranscodedBlob();
            results.add(transcodedBlob);
            
        } catch (ClientException | IOException e) {
            log.error("Cannot convert video", e);
        }

        return new SimpleCachableBlobHolder(results);
    }

}
