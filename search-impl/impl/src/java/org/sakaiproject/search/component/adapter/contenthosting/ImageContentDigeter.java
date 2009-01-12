package org.sakaiproject.search.component.adapter.contenthosting;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TagInfo;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.ServerOverloadException;



public class ImageContentDigeter extends BaseContentDigester {

	private static Log log = LogFactory.getLog(ImageContentDigeter.class);
	
	public String getContent(ContentResource contentResource) {
		log.info("digesting image for index!");
		BufferedImage image = null;
		StringBuilder sb = new StringBuilder();
		IImageMetadata metadata = null;
		ResourceProperties  rp  = contentResource.getProperties();
		sb.append(rp.getProperty(ResourceProperties.PROP_DISPLAY_NAME)).append(" ");
		sb.append(rp.getProperty(ResourceProperties.PROP_DESCRIPTION)).append(" ");
		try {
			metadata = Sanselan.getMetadata(contentResource.streamContent(), rp.getProperty(ResourceProperties.PROP_DISPLAY_NAME));
		} catch (ImageReadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServerOverloadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		if (metadata instanceof JpegImageMetadata) {
			JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
			TagInfo[] tags = TiffConstants.ALL_EXIF_TAGS;
			int numTags = 0;
			int emptyTags = 0;
			for (int i =0; i < tags.length; i++) {
				TagInfo tagInfo = tags[i];
				TiffField field = jpegMetadata.findEXIFValue(tagInfo);
				if (field != null) {
				log.info("got tag " + tagInfo.name + " with Value:" + field.getValueDescription());
				sb.append(tagInfo.name + ": " + field.getValueDescription() + "\n");
				numTags++;
				} else {
					log.debug("field " + tagInfo.name + " is empty");
					emptyTags++;
				}
			}
			log.info("jpeg image found with " + numTags + " and " + emptyTags + " empty tags");
			
		
		}
		
		
		return sb.toString();

	}

	public Reader getContentReader(ContentResource contentResource) {
		return new StringReader(getContent(contentResource));
	}

}
