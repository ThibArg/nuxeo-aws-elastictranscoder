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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoder;
import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoderConstants;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;

/**
 * The converter is contributed via an XML extension (see
 * video-and-converter-contrib.xml). The contribution has the following
 * parameters:
 * <ul>
 * <li>inputBucket</li>
 * <li>outputBucket</li>
 * <li>pipelineId</li>
 * <li>presetId</li>
 * <li>sqsQueueUrl</li>
 * <li>deleteInputFileWhenDone</li>
 * <li>deleteOutputFileWhenDone</li>
 * <li>outputFileSuffix: file extension to use (to generate the
 * <code>filename</code> field of the resulting blob). Can be actually anything
 * that will be added to the filename</li>
 * </ul>
 * <p>
 * For all parameters except <code>outputFileSuffix</code>: If a parameter is
 * missing, the class gets it from the configuration (nuxeo.conf). If it is not
 * there, the conversion will fail.
 *
 * @since 7.1
 */
public class AWSElasticTranscoderConverter implements Converter {

    private static Log log = LogFactory.getLog(AWSElasticTranscoderConverter.class);

    protected String inputBucket;

    protected String outputBucket;

    protected String pipelineId;

    protected String presetId;

    protected String sqsQueueUrl;

    protected boolean deleteInputFileWhenDone;

    protected boolean deleteOutputFileWhenDone;

    protected String outputFileSuffix;

    @Override
    public void init(ConverterDescriptor descriptor) {

        Map<String, String> params = descriptor.getParameters();
        inputBucket = StringUtils.defaultIfBlank(params.get("inputBucket"),
                AWSElasticTranscoderConstants.getDefaultBucketInput());

        outputBucket = StringUtils.defaultIfBlank(params.get("outputBucket"),
                AWSElasticTranscoderConstants.getDefaultBucketOutput());

        pipelineId = StringUtils.defaultIfBlank(params.get("pipelineId"),
                AWSElasticTranscoderConstants.getDefaultPipelineId());

        presetId = StringUtils.defaultIfBlank(params.get("presetId"),
                AWSElasticTranscoderConstants.getDefaultPresetId());

        sqsQueueUrl = StringUtils.defaultIfBlank(params.get("sqsQueueUrl"),
                AWSElasticTranscoderConstants.getDefaultSqsQueueUrl());

        String str = params.get("deleteInputfileWhenDone");
        if (StringUtils.isBlank(str)) {
            deleteInputFileWhenDone = AWSElasticTranscoderConstants.getDefaultDeleteInputFileWhenDone();
        } else {
            deleteInputFileWhenDone = str.toLowerCase().equals("true");
        }

        str = params.get("deleteOutputFileWhenDone");
        if (StringUtils.isBlank(str)) {
            deleteOutputFileWhenDone = AWSElasticTranscoderConstants.getDefaultDeleteOutputFileWhenDone();
        } else {
            deleteOutputFileWhenDone = str.toLowerCase().equals("true");
        }

        outputFileSuffix = params.get("outputFileSuffix");

    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder,
            Map<String, Serializable> parameters) throws ConversionException {

        List<Blob> results = new ArrayList<Blob>();

        Blob theBlob = blobHolder.getBlob();
        try {
            AWSElasticTranscoder transcoder = new AWSElasticTranscoder(theBlob,
                    presetId, inputBucket, outputBucket, pipelineId,
                    sqsQueueUrl, outputFileSuffix);

            transcoder.transcode();

            Blob transcodedBlob = transcoder.getTranscodedBlob();
            results.add(transcodedBlob);

        } catch (NuxeoException | IOException e) {
            log.error("Cannot convert video", e);
        }

        return new SimpleCachableBlobHolder(results);
    }

}
