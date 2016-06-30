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
 * SaveFile.java
 * ---------------
 */
package org.jpedal.examples.viewer.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.jpedal.examples.viewer.Values;
import org.jpedal.examples.viewer.utils.FileFilterer;
import org.jpedal.examples.viewer.utils.ItextFunctions;
import org.jpedal.external.ExternalHandlers;
import org.jpedal.gui.GUIFactory;
import org.jpedal.utils.LogWriter;
import org.jpedal.utils.Messages;

/**
 * Saves the current document file
 */
public class SaveFile {

    public static void execute(final Object[] args, final GUIFactory currentGUI, final Values commonValues) {
        if (args == null) {
            saveFile(currentGUI, commonValues);
        } else {

        }
    }

        public static void handleUnsaveForms(final GUIFactory currentGUI, final Values commonValues) {
        
        if(!org.jpedal.DevFlags.GUITESTINGINPROGRESS){
           
        	//OLD FORM CHANGE CODE
            if(commonValues.isFormsChanged()){
                final int n = currentGUI.showConfirmDialog(Messages.getMessage("PdfViewerFormsUnsavedOptions.message"),Messages.getMessage("PdfViewerFormsUnsavedWarning.message"), JOptionPane.YES_NO_OPTION);
                
                if(n==JOptionPane.YES_OPTION) {
                    SaveFile.saveFile(currentGUI, commonValues);
                }
            }  
        }
        
        commonValues.setFormsChanged(false);
        currentGUI.setViewerTitle(null);
    }
    
    private static final FileFilterer pdf = new FileFilterer(new String[]{"pdf"}, "Pdf (*.pdf)");
    private static final FileFilterer fdf = new FileFilterer(new String[]{"fdf"}, "fdf (*.fdf)");
        
    private static void saveFile(final GUIFactory currentGUI, final Values commonValues) {
        final JFileChooser chooser = new JFileChooser(commonValues.getInputDir());
        chooser.setSelectedFile(new File(commonValues.getInputDir() + '/' + commonValues.getSelectedFile()));
        chooser.addChoosableFileFilter(pdf);
        chooser.addChoosableFileFilter(fdf);
        chooser.setFileFilter(pdf);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        //set default name to current file name
        final int approved = chooser.showSaveDialog(null);
        if (approved == JFileChooser.APPROVE_OPTION) {
            String filter = chooser.getFileFilter().getDescription().toLowerCase();
            if (filter.startsWith("all") || filter.startsWith("pdf") || filter.startsWith("fdf")) {
                saveAsPdf(chooser.getSelectedFile(), currentGUI, commonValues);
            }
        }
    }
    
    private static void saveAsPdf(File file, final GUIFactory currentGUI, final Values commonValues) {

        String fileToSave = file.getAbsolutePath();
        File tempFile = null;
        
        if (!fileToSave.endsWith(".pdf")) {
            fileToSave += ".pdf";
            file = new File(fileToSave);
        }

//        if (fileToSave.equals(commonValues.getSelectedFile())) {
//            return;
//        }

        if (file.exists()) {
            final int n = currentGUI.showConfirmDialog(fileToSave + '\n'
                    + Messages.getMessage("PdfViewerMessage.FileAlreadyExists") + '\n'
                    + Messages.getMessage("PdfViewerMessage.ConfirmResave"),
                    Messages.getMessage("PdfViewerMessage.Resave"), JOptionPane.YES_NO_OPTION);
            if (n == 1) {
                return;
            }
        }
        
        try {
            tempFile = File.createTempFile(fileToSave.substring(0, fileToSave.lastIndexOf('.')) + "Temp", fileToSave.substring(fileToSave.lastIndexOf('.')));
            copyFile(commonValues.getSelectedFile(), tempFile.getAbsolutePath(), currentGUI);
        } catch (IOException ex) {
            LogWriter.writeLog("Exception attempting to create temp file: " + ex);
        }
        
        if (tempFile != null) {
            if (currentGUI.getAnnotationPanel().annotationAdded()) {
                currentGUI.getAnnotationPanel().saveAnnotations(tempFile.getAbsolutePath(), fileToSave);
            } else {
                copyFile(tempFile.getAbsolutePath(), fileToSave, currentGUI);
            }

            if (ExternalHandlers.isITextPresent()) {
                //final ItextFunctions itextFunctions = new ItextFunctions(currentGUI, commonValues.getSelectedFile(), currentGUI.getPdfDecoder());
                ItextFunctions.saveFormsData(fileToSave);
            }

            //Remove temp File
            tempFile.delete();
            
            commonValues.setFormsChanged(false);
            currentGUI.setViewerTitle(null);
        }
    }
    
    private static void copyFile(String input, String output, GUIFactory currentGUI){
        /*
         * reset flag and graphical clue
         */
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(input);
            fos = new FileOutputStream(output);

            final byte[] buffer = new byte[4096];
            int bytes_read;

            while ((bytes_read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytes_read);
            }
        } catch (final Exception e1) {

            //e1.printStackTrace();
            currentGUI.showMessageDialog(Messages.getMessage("PdfViewerException.NotSaveInternetFile") + ' ' + e1);
        }

        try {
            if(fis!=null){
                fis.close();
            }
            
            if(fos!=null){
                fos.close();
            }
        } catch (final IOException e2) {
            LogWriter.writeLog("Exception attempting close IOStream: " + e2);
        }
    }
}
