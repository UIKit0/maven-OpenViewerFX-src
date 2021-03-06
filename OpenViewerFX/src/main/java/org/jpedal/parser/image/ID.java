/*
 * ===========================================
 * Java Pdf Extraction Decoding Access Library
 * ===========================================
 *
 * Project Info:  http://www.idrsolutions.com
 * Help section for developers at http://www.idrsolutions.com/support/
 *
 * (C) Copyright 1997-2016 IDRsolutions and Contributors.
 *
 * This file is part of JPedal/JPDF2HTML5
 *
     This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


 *
 * ---------------
 * ID.java
 * ---------------
 */
package org.jpedal.parser.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.jpedal.color.ColorspaceFactory;
import org.jpedal.color.DeviceRGBColorSpace;
import org.jpedal.color.GenericColorSpace;
import org.jpedal.external.ErrorTracker;
import org.jpedal.external.ImageHandler;
import org.jpedal.io.*;
import org.jpedal.objects.PdfImageData;
import org.jpedal.objects.PdfPageData;
import org.jpedal.objects.raw.PdfArrayIterator;
import org.jpedal.objects.raw.PdfDictionary;
import org.jpedal.objects.raw.PdfObject;
import org.jpedal.parser.PdfObjectCache;
import org.jpedal.parser.image.data.ImageData;

public class ID extends ImageDecoder {
    public ID(final int imageCount, final PdfObjectReader currentPdfFile, final ErrorTracker errorTracker, final ImageHandler customImageHandler, final ObjectStore objectStoreStreamRef, final PdfImageData pdfImages, final PdfPageData pageData, final String imagesInFile) {
        super(imageCount, currentPdfFile, errorTracker, customImageHandler, objectStoreStreamRef, pdfImages, pageData, imagesInFile);
    }


    @Override
    public int processImage(int dataPointer, final int startInlineStream, final byte[] stream, final int tokenNumber) throws Exception{

        /*
         * read Dictionary
         */
        final PdfObject XObject=new org.jpedal.objects.raw.XObject(PdfDictionary.ID);

        final IDObjectDecoder objectDecoder=new IDObjectDecoder(currentPdfFile.getObjectReader());
        objectDecoder.setEndPt(dataPointer-2);

        objectDecoder.readDictionaryAsObject(XObject,startInlineStream,stream);

        BufferedImage image =   null;

        final boolean inline_imageMask;

        //store pointer to current place in file
        int inline_start_pointer = dataPointer + 1;
        final int i_w;
        final int i_h;
        final int i_bpc;

        //find end of stream
        int i = inline_start_pointer;
        final int streamLength=stream.length;

        //find end
        while (true) {
            //look for end EI

            //handle Pdflib and xenos variety
            if ( streamLength-i>3 &&  stream[i + 1] == 69 && stream[i + 2] == 73 && ((stream[i+3] == 10 && (streamLength==i+4 || stream[i+4]==81))  || (stream[i+3]==32 && stream[i+4] == 10))){
                break;
            }
            //general case
            if ((streamLength-i>3)&&(stream[i] == 32 || stream[i] == 10 || stream[i] == 13 ||  (stream[i+3] == 32 && stream[i+4] == 'Q'))
                    && (stream[i + 1] == 69)
                    && (stream[i + 2] == 73)
                    && ( stream[i+3] == 32 || stream[i+3] == 10 || stream[i+3] == 13)) {
                break;
            }
            

            i++;

            if(i==streamLength) {
                break;
            }
        }
        
        if(parserOptions.imagesNeeded()){

            //load the data
            //		generate the name including file name to make it unique
            final String image_name =parserOptions.getFileName()+ "-IN-" + tokenNumber;

            int endPtr=i;
            //hack for odd files
            if(i<stream.length && stream[endPtr] != 32 && stream[endPtr] != 10 && stream[endPtr] != 13) {
                endPtr++;
            }

            //correct data (ie idoq/FC1100000021259.pdf )
            if(stream[inline_start_pointer]==10) {
                inline_start_pointer++;
            }

            /*
             * put image data in array
             */
            byte[] i_data = new byte[endPtr - inline_start_pointer];
            System.arraycopy(
                    stream,
                    inline_start_pointer,
                    i_data,
                    0,
                    endPtr - inline_start_pointer);

            XObject.setStream(i_data);

            /*
             * work out colorspace
             */
            PdfObject ColorSpace=XObject.getDictionary(PdfDictionary.ColorSpace);

            //check for Named value
            if(ColorSpace!=null){
                final String colKey=ColorSpace.getGeneralStringValue();

                if(colKey!=null){
                    final Object col=cache.get(PdfObjectCache.Colorspaces,colKey);

                    if(col!=null) {
                        ColorSpace = (PdfObject) col;
                    }
                }
            }

            /*
             * allow user to process image
             */
            if(customImageHandler!=null) {
                image = customImageHandler.processImageData(gs, XObject);
            }

            final PdfArrayIterator filters = XObject.getMixedArray(PdfDictionary.Filter);

            //check not handled elsewhere
            final int firstValue;
            boolean needsDecoding=false;
            if(filters!=null && filters.hasMoreTokens()){
                firstValue=filters.getNextValueAsConstant(false);

                needsDecoding=(firstValue!= PdfFilteredReader.JPXDecode && firstValue!=PdfFilteredReader.DCTDecode);
            }

            i_w=XObject.getInt(PdfDictionary.Width);
            i_h=XObject.getInt(PdfDictionary.Height);
            i_bpc=XObject.getInt(PdfDictionary.BitsPerComponent);
            inline_imageMask=XObject.getBoolean(PdfDictionary.ImageMask);

            //handle filters (JPXDecode/DCT decode is handle by process image)
            if(needsDecoding){
                final PdfFilteredReader filter=new PdfFilteredReader();
                i_data=filter.decodeFilters(ObjectUtils.setupDecodeParms(XObject, currentPdfFile.getObjectReader()), i_data, filters, i_w, i_h, null);
            }

            //handle colour information
            GenericColorSpace decodeColorData=new DeviceRGBColorSpace();
            if(ColorSpace!=null){
                decodeColorData= ColorspaceFactory.getColorSpaceInstance(currentPdfFile, ColorSpace);
                decodeColorData.setPrinting(parserOptions.isPrinting());

                //track colorspace use
                cache.put(PdfObjectCache.ColorspacesUsed, decodeColorData.getID(),"x");

                //use alternate as preference if CMYK
                //if(newColorSpace.getID()==ColorSpaces.ICC && ColorSpace.getParameterConstant(PdfDictionary.Alternate)==ColorSpaces.DeviceCMYK)
                //newColorSpace=new DeviceCMYKColorSpace();
            }
            if(i_data!=null){

                if(customImageHandler==null ||(image==null && !customImageHandler.alwaysIgnoreGenericHandler())){


                    final ImageData imageData=new ImageData(ImageCommands.ID);
                    imageData.setWidth(i_w);
                    imageData.setHeight(i_h);
                    imageData.setDepth(i_bpc);
                    imageData.setObjectData(i_data);
                    imageData.setCompCount(decodeColorData.getColorSpace().getNumComponents());
        
                    image=processImage(decodeColorData, imageData, inline_imageMask, XObject);

                    //transparency slows down printing so try to reduce if possible in printing
                    if(inline_imageMask && !ImageDecoder.allowPrintTransparency && parserOptions.isPrinting() && image!=null && imageData.getDepth()==1){

                        //setup any imageMask
                        final byte[] maskCol=ImageCommands.getMaskColor(gs);
                        
                        if(maskCol!=null && maskCol[0]==0 && maskCol[1]==0 && maskCol[2]==0 && maskCol[3]==0){
                            final int iw=image.getWidth();
                            final int ih=image.getHeight();
                            final BufferedImage newImage=new BufferedImage(iw,ih,BufferedImage.TYPE_BYTE_GRAY);

                            newImage.getGraphics().setColor(Color.WHITE);
                            newImage.getGraphics().fillRect(0,0,iw,ih);
                            newImage.getGraphics().drawImage(image, 0, 0, null);
                            image=newImage;
                        }
                    }
                    
                    if(image!=null && !current.isHTMLorSVG() && !parserOptions.renderDirectly() && parserOptions.isPageContent() && parserOptions.imagesNeeded()) {
                        objectStoreStreamRef.saveStoredImage(image_name,ImageCommands.addBackgroundToMask(image, isMask),false,parserOptions.createScaledVersion(),"tif");          
                    }
        
                    //generate name including filename to make it unique
                    currentImage = image_name;

                }

                if (image != null){

                    if(current.isHTMLorSVG()){
                        generateTransformedImage(image, image_name);
                    }else{

                        gs.x=gs.CTM[2][0];
                        gs.y=gs.CTM[2][1];

                        current.drawImage(parserOptions.getPageNumber(), image, gs, false, image_name, -1);
                    }

                    if(image!=null) {
                        image.flush();
                    }
                }
            }
        }

        dataPointer = i + 3;

        return dataPointer;

    }
}
